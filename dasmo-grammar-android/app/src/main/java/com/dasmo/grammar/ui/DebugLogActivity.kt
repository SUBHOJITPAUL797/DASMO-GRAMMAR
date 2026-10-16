package com.dasmo.grammar.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dasmo.grammar.R
import com.dasmo.grammar.engine.DasmoEngine
import com.dasmo.grammar.engine.DasmoLogger

class DebugLogActivity : AppCompatActivity() {

    private lateinit var tvLogOutput: TextView
    private lateinit var svLogs: ScrollView
    private lateinit var engine: DasmoEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_log)

        engine = DasmoEngine.getInstance(this)

        tvLogOutput = findViewById(R.id.tvLogOutput)
        svLogs = findViewById(R.id.svLogs)

        findViewById<Button>(R.id.btnRefreshLogs).setOnClickListener {
            loadLogs()
        }

        findViewById<Button>(R.id.btnClearLogs).setOnClickListener {
            DasmoLogger.clear()
            loadLogs()
            Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnCopyReport).setOnClickListener {
            val report = DasmoLogger.getSystemDiagnosticsReport(
                engineReady = engine.isReady.value,
                dialect = engine.currentDialect.displayName,
                dictCount = engine.dictionary.getWords().size
            )
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Dasmo Diagnostics", report))
            Toast.makeText(this, "Full diagnostics copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        loadLogs()
    }

    private fun loadLogs() {
        val report = DasmoLogger.getSystemDiagnosticsReport(
            engineReady = engine.isReady.value,
            dialect = engine.currentDialect.displayName,
            dictCount = engine.dictionary.getWords().size
        )
        tvLogOutput.text = report
        svLogs.post {
            svLogs.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}
