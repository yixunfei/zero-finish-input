package dev.zeroinput.ime

import android.app.Application

class ZeroInputApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }

    override fun onTerminate() {
        graph.close()
        super.onTerminate()
    }
}

