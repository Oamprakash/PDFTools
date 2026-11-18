package com.oam.pdftools

import android.app.Application
import androidx.room.Room
import com.oam.pdftools.ads.Ads
import com.oam.pdftools.billing.Billing
import com.oam.pdftools.data.AppDb
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class App : Application() {
    val db by lazy { Room.databaseBuilder(this, AppDb::class.java, "pdftools.db").build() }
    override fun onCreate() { super.onCreate(); PDFBoxResourceLoader.init(this); Billing.init(this); Ads.init(this)
        // Shared-in copies from earlier sessions (nothing references them after a restart)
        Thread { java.io.File(cacheDir, "incoming").deleteRecursively() }.start()
    }
}
