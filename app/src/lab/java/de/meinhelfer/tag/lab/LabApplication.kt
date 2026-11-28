package de.meinhelfer.tag.lab

import android.app.Application

/** Lab startup deliberately does not invoke production services. */
class LabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        check(packageName == "de.meinhelfer.tag.lab") { "Lab package isolation failed" }
    }
}
