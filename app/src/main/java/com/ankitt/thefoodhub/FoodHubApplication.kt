package com.ankitt.thefoodhub

import android.app.Application
import com.ankitt.thefoodhub.core.AppConfig
import com.ankitt.thefoodhub.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class FoodHubApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initKoin(
            // The Spoonacular key comes from the shared module (generated from local.properties).
            config = AppConfig(isDebug = BuildConfig.DEBUG),
        ) {
            if (BuildConfig.DEBUG) androidLogger()
            androidContext(this@FoodHubApplication)
        }
    }
}
