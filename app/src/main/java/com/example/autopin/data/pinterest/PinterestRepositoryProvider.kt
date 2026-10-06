package com.example.autopin.data.pinterest

import android.content.Context

object PinterestRepositoryProvider {

    @Volatile
    private var INSTANCE: PinterestRepository? = null

    fun getInstance(context: Context): PinterestRepository {
        return INSTANCE ?: synchronized(this) {
            val instance = PinterestRepositoryImpl(context.applicationContext)
            INSTANCE = instance
            instance
        }
    }
}
