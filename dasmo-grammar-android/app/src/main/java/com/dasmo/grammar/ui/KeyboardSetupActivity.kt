package com.dasmo.grammar.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dasmo.grammar.R

class KeyboardSetupActivity : AppCompatActivity() {

    private lateinit var btnStep1: Button
    private lateinit var btnStep2: Button
    private lateinit var tvStatus1: TextView
    private lateinit var tvStatus2: TextView
    private lateinit var etSandbox: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        btnStep1 = findViewById(R.id.btnStep1)
        btnStep2 = findViewById(R.id.btnStep2)
        tvStatus1 = findViewById(R.id.tvStatus1)
        tvStatus2 = findViewById(R.id.tvStatus2)
        etSandbox = findViewById(R.id.etSandbox)

        btnStep1.setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }

        btnStep2.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        findViewById<Button>(R.id.btnDone).setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        checkKeyboardStatus()
    }

    private fun checkKeyboardStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val list = imm.enabledInputMethodList
        val isEnabled = list.any { it.packageName == packageName }

        if (isEnabled) {
            tvStatus1.text = "✓ Enabled in Settings"
            tvStatus1.setTextColor(getColor(android.R.color.holo_green_light))
        } else {
            tvStatus1.text = "Pending activation"
            tvStatus1.setTextColor(getColor(android.R.color.holo_orange_light))
        }

        val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val isDefault = currentIme != null && currentIme.contains(packageName)

        if (isDefault) {
            tvStatus2.text = "✓ Selected as Default Keyboard"
            tvStatus2.setTextColor(getColor(android.R.color.holo_green_light))
        } else {
            tvStatus2.text = "Not selected as default"
            tvStatus2.setTextColor(getColor(android.R.color.holo_orange_light))
        }
    }
}
