package com.henrykvdb.sttt

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.asLiveData
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.min

private const val RECONNECT_TIMER_START_MILLISECONDS = 1L * 1000L
private const val RECONNECT_TIMER_MAX_TIME_MILLISECONDS = 1000L * 60L * 15L // 15 minutes
private const val SKU_DETAILS_REQUERY_TIME = 1000L * 60L * 60L * 4L // 4 hours

const val BILLING_PRODUCT_ID_ADS = "remove_ads"

private val PRODUCT_ID_LIST = listOf(BILLING_PRODUCT_ID_ADS)
private val PRODUCT_LIST = PRODUCT_ID_LIST.map {
    QueryProductDetailsParams.Product.newBuilder().setProductId(it)
        .setProductType(BillingClient.ProductType.INAPP).build()
}

class BillingDataSource private constructor(
    application: Application, private val defaultScope: CoroutineScope
) : PurchasesUpdatedListener, BillingClientStateListener, DefaultLifecycleObserver {
    // Billing client, connection, cached data
    private val billingClient: BillingClient

    // how long before the data source tries to reconnect to Google play
    private var reconnectMilliseconds = RECONNECT_TIMER_START_MILLISECONDS

    // when was the last successful SkuDetailsResponse?
    private var skuDetailsResponseTime = -SKU_DETAILS_REQUERY_TIME

    private enum class SkuState {
        STATE_UNPURCHASED, STATE_PENDING, STATE_PURCHASED, STATE_PURCHASED_AND_ACKNOWLEDGED
    }

    // Flows that can be transformed into observables.
    // default: no ads until billing says not premium
    private val stateFlow: MutableStateFlow<SkuState> = MutableStateFlow(SkuState.STATE_PURCHASED_AND_ACKNOWLEDGED)
    private val detailsFlow: MutableStateFlow<ProductDetails?> = MutableStateFlow(null)
    private val billingFlowInProcess = MutableStateFlow(false)

    init {
        // Querry details if stale
        detailsFlow.subscriptionCount.map { count -> count > 0 } // map count into active/inactive flag
            .distinctUntilChanged() // only react to true<->false changes
            .onEach { isActive -> // configure an action
                val shouldRefresh = SystemClock.elapsedRealtime() - skuDetailsResponseTime > SKU_DETAILS_REQUERY_TIME
                if (isActive && shouldRefresh) {
                    log("Skus not fresh, requerying")
                    skuDetailsResponseTime = SystemClock.elapsedRealtime()
                    querySkuDetailsAsync()
                }
            }.launchIn(defaultScope)

        // Create billing client
        billingClient = BillingClient.newBuilder(application).setListener(this).enablePendingPurchases().build()
        billingClient.startConnection(this)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        log("onBillingSetupFinished: $responseCode $debugMessage")
        when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                reconnectMilliseconds = RECONNECT_TIMER_START_MILLISECONDS
                querySkuDetailsAsync()
                refreshPurchasesAsync()
            }

            else -> retryBillingServiceConnectionWithExponentialBackoff()
        }
    }

    override fun onBillingServiceDisconnected() {
        retryBillingServiceConnectionWithExponentialBackoff()
    }

    private fun retryBillingServiceConnectionWithExponentialBackoff() {
        handler.postDelayed(
            { billingClient.startConnection(this@BillingDataSource) }, reconnectMilliseconds
        )
        reconnectMilliseconds =
            min(reconnectMilliseconds * 2, RECONNECT_TIMER_MAX_TIME_MILLISECONDS)
    }

    val showAdsLiveData = isPremiumFlow().map {premium -> !premium }.asLiveData()
    private fun isPremiumFlow(): Flow<Boolean> {
        return stateFlow.map { skuState -> skuState == SkuState.STATE_PURCHASED_AND_ACKNOWLEDGED }
    }

    fun purchaseAdUnlock(activity: Activity) {
        val productDetails = detailsFlow.value
        if (null != productDetails) {
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails).build()
            )
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList).build()
            defaultScope.launch { // launchBillingFlow should be called on UI thread
                val br = billingClient.launchBillingFlow(activity, billingFlowParams)
                if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                    billingFlowInProcess.tryEmit(true)
                } else log("Billing failed: + " + br.debugMessage)
            }
        } else log("SkuDetails not found")
    }

    private fun querySkuDetailsAsync() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(PRODUCT_LIST).build()
        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                log("onSkuDetailsResponse OK: ${billingResult.debugMessage}")
                skuDetailsResponseTime = SystemClock.elapsedRealtime()
                for (productDetails in productDetailsList) {
                    detailsFlow.tryEmit(productDetails)
                }
            } else skuDetailsResponseTime = -SKU_DETAILS_REQUERY_TIME
        }
    }

    private fun refreshPurchasesAsync() {
        log("Refreshing purchases.")
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchases.isEmpty()) {
                    stateFlow.tryEmit(SkuState.STATE_UNPURCHASED)
                } else for (purchase in purchases) acknowledgePurchase(purchase)
            } else log("Problem getting purchases: " + billingResult.debugMessage)
            log("Refreshing purchases finished.")
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> stateFlow.tryEmit(SkuState.STATE_PENDING)
            Purchase.PurchaseState.UNSPECIFIED_STATE -> stateFlow.tryEmit(SkuState.STATE_UNPURCHASED)
            Purchase.PurchaseState.PURCHASED -> {
                if (purchase.isAcknowledged)
                    stateFlow.tryEmit(SkuState.STATE_PURCHASED_AND_ACKNOWLEDGED)
                else {
                    stateFlow.tryEmit(SkuState.STATE_PURCHASED)
                    val params = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken).build()
                    billingClient.acknowledgePurchase(params) { billingResult ->
                        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                            stateFlow.tryEmit(SkuState.STATE_PURCHASED_AND_ACKNOWLEDGED)
                            log("Error acknowledging purchas")
                        } else log("Error acknowledging purchase")
                    }
                }
            }

            else -> log("Purchase in unknown state: ${purchase.purchaseState}")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) acknowledgePurchase(purchase)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            log("User Cancelled: (${billingResult.debugMessage})")
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            log("Product already owned")
        } else log("Purchase failed")
        billingFlowInProcess.tryEmit(false)
    }

    override fun onResume(owner: LifecycleOwner) {
        log("ON_RESUME")
        super.onResume(owner)

        // this just avoids an extra purchase refresh after we finish a billing flow
        if (!billingFlowInProcess.value && billingClient.isReady) {
            refreshPurchasesAsync()
        }
    }

    companion object {
        @Volatile
        private var sInstance: BillingDataSource? = null
        private val handler = Handler(Looper.getMainLooper())

        // Standard boilerplate double check locking pattern for thread-safe singletons.
        @JvmStatic
        fun getInstance(
            application: Application, defaultScope: CoroutineScope
        ) = sInstance ?: synchronized(this) {
            sInstance ?: BillingDataSource(
                application, defaultScope
            ).also { sInstance = it }
        }
    }
}
