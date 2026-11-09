package com.az.tube

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        AppLock.init(this)
        NewPipe.init(DownloaderImpl, Localization("vi", "VN"), ContentCountry("VN"))
    }
}
