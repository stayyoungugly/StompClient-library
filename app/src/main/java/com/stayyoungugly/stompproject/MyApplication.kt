package com.stayyoungugly.stompproject

import android.app.Application
import com.stayyoungugly.stompproject.BuildConfig
import timber.log.Timber

class MyApplication : Application(){

    override fun onCreate() {
        super.onCreate()
        // init timber
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
