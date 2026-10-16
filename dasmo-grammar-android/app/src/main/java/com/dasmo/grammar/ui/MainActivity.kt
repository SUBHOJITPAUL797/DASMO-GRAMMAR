package com.dasmo.grammar.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dasmo.grammar.BuildConfig
import com.dasmo.grammar.R
import com.dasmo.grammar.engine.DasmoEngine
import com.dasmo.grammar.engine.DasmoLint
import com.dasmo.grammar.engine.DasmoStats
import com.dasmo.grammar.updater.DasmoUpdateManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var engine: DasmoEngine
    private lateinit var updateManager: DasmoUpdateManager
    private lateinit var etEditor: EditText
    private lateinit var rvLints: RecyclerView
    private lateinit var adapter: LintsAdapter

    private lateinit var tvStatusBadge: TextView
    private lateinit var tvStatsInfo: TextView
    private lateinit var tvReadabilityGrade: TextView
    private lateinit var btnFixAll: Button
    private lateinit var btnEnableKeyboard: Button
    private lateinit var btnEnableFloatingAssistant: Button
    private lateinit var btnSettings: View
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var cardCelebration: View

    private var allLints: List<DasmoLint> = emptyList()
    private var filteredLints: List<DasmoLint> = emptyList()
    private var currentFilter: String = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        engine = DasmoEngine.getInstance(this)
        updateManager = DasmoUpdateManager(this)

        initViews()
        setupListeners()
        observeEngine()

        // Background update check (silent)
        lifecycleScope.launch {
            delay(2500L)
            val release = updateManager.checkForUpdates()
            if (release != null && release.isNewer && !isFinishing && !isDestroyed) {
                updateManager.showUpdateDialog(this@MainActivity, release)
            }
        }

        // Preload sample text if empty
        if (etEditor.text.isNullOrBlank()) {
            etEditor.setText("She do not likes apples. Their going to there house and they should of called.")
        }
    }

    private fun initViews() {
        etEditor = findViewById(R.id.etEditor)
        rvLints = findViewById(R.id.rvLints)
        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        tvStatsInfo = findViewById(R.id.tvStatsInfo)
        tvReadabilityGrade = findViewById(R.id.tvReadabilityGrade)
        btnFixAll = findViewById(R.id.btnFixAll)
        btnEnableKeyboard = findViewById(R.id.btnEnableKeyboard)
        btnEnableFloatingAssistant = findViewById(R.id.btnEnableFloatingAssistant)
        btnSettings = findViewById(R.id.btnSettings)
        chipGroupFilter = findViewById(R.id.chipGroupFilter)
        cardCelebration = findViewById(R.id.cardCelebration)

        adapter = LintsAdapter(
            lints = emptyList(),
            onApplySuggestion = { lint, replacement ->
                applySuggestion(lint, replacement)
            },
            onIgnoreLint = { lint ->
                allLints = allLints.filter { it.id != lint.id }
                applyFilter(currentFilter)
            },
            onAddToDictionary = { lint ->
                engine.addCustomWord(lint.problem)
                Toast.makeText(this, "Added '${lint.problem}' to personal dictionary", Toast.LENGTH_SHORT).show()
                checkDocument(etEditor.text.toString())
            }
        )

        rvLints.layoutManager = LinearLayoutManager(this)
        rvLints.adapter = adapter
    }

    private fun setupListeners() {
        etEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                checkDocument(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnFixAll.setOnClickListener {
            fixAllSuggestions()
        }

        btnEnableFloatingAssistant.setOnClickListener {
            startActivity(Intent(this, FloatingAssistantSetupActivity::class.java))
        }

        btnEnableKeyboard.setOnClickListener {
            startActivity(Intent(this, KeyboardSetupActivity::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.btnCheckUpdate)?.setOnClickListener {
            Toast.makeText(this, "Checking for latest update...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val release = updateManager.checkForUpdates()
                if (release != null && release.isNewer) {
                    updateManager.showUpdateDialog(this@MainActivity, release)
                } else if (release != null) {
                    Toast.makeText(this@MainActivity, "Dasmo Grammar is up to date (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Unable to check updates. Please check your network.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<View>(R.id.btnCopy).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Dasmo Grammar", etEditor.text.toString()))
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnPaste).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = cm.primaryClip?.getItemAt(0)?.text
            if (!item.isNullOrEmpty()) {
                etEditor.setText(item)
            }
        }

        findViewById<View>(R.id.btnClear).setOnClickListener {
            etEditor.setText("")
        }

        findViewById<View>(R.id.btnShare).setOnClickListener {
            val text = etEditor.text.toString()
            if (text.isNotBlank()) {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(sendIntent, "Share Checked Text"))
            }
        }

        chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val chip = findViewById<Chip>(checkedIds.first())
            currentFilter = when (chip.id) {
                R.id.chipCorrectness -> "Correctness"
                R.id.chipClarity -> "Clarity"
                R.id.chipEngagement -> "Engagement"
                else -> "ALL"
            }
            applyFilter(currentFilter)
        }
    }

    private fun observeEngine() {
        lifecycleScope.launch {
            engine.isReady.collect { ready ->
                if (ready) {
                    checkDocument(etEditor.text.toString())
                }
            }
        }
    }

    private fun checkDocument(text: String) {
        if (text.isBlank()) {
            allLints = emptyList()
            applyFilter(currentFilter)
            cardCelebration.visibility = View.GONE
            tvStatsInfo.text = "0 words • 0 chars • 0 min read"
            tvReadabilityGrade.text = "Ready"
            tvStatusBadge.text = "✓ Ready"
            tvStatusBadge.setTextColor(Color.parseColor("#10B981"))
            btnFixAll.isEnabled = false
            return
        }

        engine.lintDebounced(text, 250L) { lints ->
            runOnUiThread {
                allLints = lints
                applyFilter(currentFilter)

                if (lints.isEmpty()) {
                    tvStatusBadge.text = "✓ All clear"
                    tvStatusBadge.setTextColor(Color.parseColor("#10B981"))
                    btnFixAll.isEnabled = false
                    cardCelebration.visibility = View.VISIBLE
                } else {
                    tvStatusBadge.text = "${lints.size} issues found"
                    tvStatusBadge.setTextColor(Color.parseColor("#F43F5E"))
                    btnFixAll.isEnabled = true
                    cardCelebration.visibility = View.GONE
                }
            }
        }

        lifecycleScope.launch {
            val stats = engine.getStats(text)
            runOnUiThread {
                tvStatsInfo.text = "${stats.wordCount} words • ${stats.charCount} chars • ${stats.readingTimeMinutes} min read"
                tvReadabilityGrade.text = "Score: ${stats.readabilityScore} (${stats.readabilityGrade})"
            }
        }
    }

    private fun applyFilter(filter: String) {
        filteredLints = if (filter == "ALL") {
            allLints
        } else {
            allLints.filter { it.category.equals(filter, ignoreCase = true) }
        }
        adapter.updateLints(filteredLints)
    }

    private fun applySuggestion(lint: DasmoLint, replacement: String) {
        val currentText = etEditor.text.toString()
        val problem = lint.problem

        // Prefer exact span match if text hasn't mutated out from under it
        val index = if (lint.start in 0..currentText.length &&
            lint.end in lint.start..currentText.length &&
            currentText.substring(lint.start, lint.end) == problem
        ) {
            lint.start
        } else {
            currentText.indexOf(problem)
        }

        if (index != -1) {
            val updated = currentText.substring(0, index) + replacement + currentText.substring(index + problem.length)
            etEditor.setText(updated)
            etEditor.setSelection((index + replacement.length).coerceAtMost(updated.length))
        }
    }

    private fun fixAllSuggestions() {
        var text = etEditor.text.toString()
        // Sort descending by start offset to prevent index shifting from corrupting subsequent replacements
        val sortedLints = allLints.sortedByDescending { it.start }
        var fixesApplied = 0

        for (lint in sortedLints) {
            val repl = lint.primarySuggestion ?: continue
            val problem = lint.problem
            if (lint.start in 0..text.length &&
                lint.end in lint.start..text.length &&
                text.substring(lint.start, lint.end) == problem
            ) {
                text = text.substring(0, lint.start) + repl + text.substring(lint.end)
                fixesApplied++
            } else {
                val idx = text.lastIndexOf(problem)
                if (idx != -1) {
                    text = text.substring(0, idx) + repl + text.substring(idx + problem.length)
                    fixesApplied++
                }
            }
        }
        etEditor.setText(text)
        Toast.makeText(this, "Applied $fixesApplied suggestions!", Toast.LENGTH_SHORT).show()
    }
}
