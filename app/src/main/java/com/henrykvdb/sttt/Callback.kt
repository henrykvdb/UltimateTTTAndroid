package com.henrykvdb.sttt

interface Callback<in T> {
    fun callback(t: T)
}