package com.dasmo.grammar

import android.app.Application
import android.content.Context
import com.dasmo.grammar.engine.DasmoDialect
import com.dasmo.grammar.engine.DasmoEngine

class DasmoApplication : Application() {

    lateinit var engine: DasmoEngine
        private set

    override fun onCreate() {
        super.onCreate()
        engine = DasmoEngine.getInstance(this)

        val prefs = getSharedPreferences("dasmo_prefs", Context.MODE_PRIVATE)
        val savedDialect = prefs.getString("dialect", DasmoDialect.AMERICAN.engineName)
        engine.setDialect(DasmoDialect.fromEngineName(savedDialect))
    }
}
