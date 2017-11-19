package com.henrykvdb.sttt

interface Callback<in T> {
    operator fun invoke(t: T)
}