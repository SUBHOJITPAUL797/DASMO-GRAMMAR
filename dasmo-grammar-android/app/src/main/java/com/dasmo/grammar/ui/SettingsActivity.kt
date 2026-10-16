package com.dasmo.grammar.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dasmo.grammar.BuildConfig
import com.dasmo.grammar.R
import com.dasmo.grammar.engine.DasmoDialect
import com.dasmo.grammar.engine.DasmoEngine
import com.dasmo.grammar.updater.DasmoUpdateManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var engine: DasmoEngine
    private lateinit var updateManager: DasmoUpdateManager
    private lateinit var spinnerDialect: Spinner
    private lateinit var etNewWord: EditText
    private lateinit var btnAddWord: Button
    private lateinit var lvDictionary: ListView
    private lateinit var wordsAdapter: ArrayAdapter<String>
    private val wordsList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        engine = DasmoEngine.getInstance(this)

        spinnerDialect = findViewById(R.id.spinnerDialect)
        etNewWord = findViewById(R.id.etNewWord)
        btnAddWord = findViewById(R.id.btnAddWord)
        lvDictionary = findViewById(R.id.lvDictionary)

        setupDialectSpinner()
        setupDictionaryList()

        findViewById<Button>(R.id.btnClearDictionary).setOnClickListener {
            engine.dictionary.clear()
            refreshDictionary()
            Toast.makeText(this, "Dictionary cleared", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnViewLogs).setOnClickListener {
            startActivity(Intent(this, DebugLogActivity::class.java))
        }

        updateManager = DasmoUpdateManager(this)
        findViewById<TextView>(R.id.tvCurrentVersion).text = "Installed: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
        findViewById<TextView>(R.id.tvRepoName).text = updateManager.getRepository()

        findViewById<Button>(R.id.btnCheckUpdatesSettings).setOnClickListener {
            Toast.makeText(this, "Checking GitHub for updates...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val release = updateManager.checkForUpdates()
                if (release != null && release.isNewer) {
                    updateManager.showUpdateDialog(this@SettingsActivity, release)
                } else if (release != null) {
                    Toast.makeText(this@SettingsActivity, "Dasmo Grammar is up to date (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SettingsActivity, "No newer update found on GitHub.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupDialectSpinner() {
        val dialects = DasmoDialect.entries.toTypedArray()
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            dialects.map { it.displayName }
        )
        spinnerDialect.adapter = adapter

        val current = engine.currentDialect
        val currentIndex = dialects.indexOf(current)
        if (currentIndex != -1) {
            spinnerDialect.setSelection(currentIndex)
        }

        spinnerDialect.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selected = dialects[position]
                engine.setDialect(selected)
                val prefs = getSharedPreferences("dasmo_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("dialect", selected.engineName).apply()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
    }

    private fun setupDictionaryList() {
        wordsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, wordsList)
        lvDictionary.adapter = wordsAdapter

        refreshDictionary()

        btnAddWord.setOnClickListener {
            val word = etNewWord.text.toString().trim()
            if (word.isNotEmpty()) {
                engine.addCustomWord(word)
                etNewWord.setText("")
                refreshDictionary()
                Toast.makeText(this, "Added '$word'", Toast.LENGTH_SHORT).show()
            }
        }

        lvDictionary.setOnItemClickListener { _, _, position, _ ->
            val word = wordsList[position]
            engine.dictionary.removeWord(word)
            refreshDictionary()
            Toast.makeText(this, "Removed '$word'", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshDictionary() {
        wordsList.clear()
        wordsList.addAll(engine.dictionary.getWords())
        wordsAdapter.notifyDataSetChanged()
    }
}
