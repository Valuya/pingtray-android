package be.valuya.pingtray

import android.app.Application

class App : Application() {
    lateinit var db: Db
        private set
    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        db = Db(this)
        settings = Settings(this)
        // osmdroid: tile cache in the app's own storage, a proper user agent
        val osm = org.osmdroid.config.Configuration.getInstance()
        osm.userAgentValue = "pingtray-android/${BuildConfigLite.VERSION}"
        osm.osmdroidBasePath = filesDir.resolve("osmdroid")
        osm.osmdroidTileCache = cacheDir.resolve("tiles")
    }
}

object BuildConfigLite {
    const val VERSION = "0.1"
}

val android.content.Context.app: App get() = applicationContext as App
