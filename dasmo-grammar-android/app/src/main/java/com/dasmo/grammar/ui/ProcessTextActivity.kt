package com.dasmo.grammar.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dasmo.grammar.R
import com.dasmo.grammar.engine.DasmoEngine
import com.dasmo.grammar.engine.DasmoLint
import kotlinx.coroutines.launch

class ProcessTextActivity : AppCompatActivity() {

    private lateinit var engine: DasmoEngine
    private lateinit var tvOriginalText: TextView
    private lateinit var rvLints: RecyclerView
    private lateinit var btnCopyFixed: Button
    private lateinit var btnReplace: Button
    private lateinit var adapter: LintsAdapter

    private var originalText: String = ""
    private var currentText: String = ""
    private var detectedLints: List<DasmoLint> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_process_text)

        engine = DasmoEngine.getInstance(this)

        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
        originalText = selectedText
        currentText = selectedText

        tvOriginalText = findViewById(R.id.tvOriginalText)
        rvLints = findViewById(R.id.rvProcessLints)
        btnCopyFixed = findViewById(R.id.btnCopyFixed)
        btnReplace = findViewById(R.id.btnReplace)

        tvOriginalText.text = originalText

        val isReadOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        if (isReadOnly) {
            btnReplace.visibility = View.GONE
        }

        adapter = LintsAdapter(
            lints = emptyList(),
            onApplySuggestion = { lint, replacement ->
                applySuggestion(lint, replacement)
            },
            onIgnoreLint = { lint ->
                detectedLints = detectedLints.filter { it.id != lint.id }
                adapter.updateLints(detectedLints)
            },
            onAddToDictionary = { lint ->
                engine.addCustomWord(lint.problem)
                runLintCheck()
            }
        )

        rvLints.layoutManager = LinearLayoutManager(this)
        rvLints.adapter = adapter

        btnCopyFixed.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Dasmo Grammar", currentText))
            Toast.makeText(this, "Copied corrected text to clipboard", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnReplace.setOnClickListener {
            val resultIntent = Intent().apply {
                putExtra(Intent.EXTRA_PROCESS_TEXT, currentText)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        runLintCheck()
    }

    private fun runLintCheck() {
        lifecycleScope.launch {
            detectedLints = engine.lint(currentText)
            runOnUiThread {
                adapter.updateLints(detectedLints)
                if (detectedLints.isEmpty()) {
                    findViewById<TextView>(R.id.tvProcessStatus).text = "✓ Text looks great! No errors found."
                } else {
                    findViewById<TextView>(R.id.tvProcessStatus).text = "${detectedLints.size} issue(s) detected:"
                }
            }
        }
    }

    private fun applySuggestion(lint: DasmoLint, replacement: String) {
        val problem = lint.problem
        val index = if (lint.start in 0..currentText.length &&
            lint.end in lint.start..currentText.length &&
            currentText.substring(lint.start, lint.end) == problem
        ) {
            lint.start
        } else {
            currentText.indexOf(problem)
        }

        if (index != -1) {
            currentText = currentText.substring(0, index) + replacement + currentText.substring(index + problem.length)
            tvOriginalText.text = currentText
            runLintCheck()
        }
    }
}
