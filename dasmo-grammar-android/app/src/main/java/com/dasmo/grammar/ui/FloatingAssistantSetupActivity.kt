package com.dasmo.grammar.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dasmo.grammar.R
import com.dasmo.grammar.accessibility.DasmoFloatingAssistantService

class FloatingAssistantSetupActivity : AppCompatActivity() {

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnOpenAccessibility: Button
    private lateinit var etFloatingSandbox: EditText
    private lateinit var tvSandboxFeedback: TextView
    private lateinit var engine: com.dasmo.grammar.engine.DasmoEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_floating_setup)

        engine = com.dasmo.grammar.engine.DasmoEngine.getInstance(this)

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility)
        etFloatingSandbox = findViewById(R.id.etFloatingSandbox)
        tvSandboxFeedback = findViewById(R.id.tvSandboxFeedback)

        btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        btnOpenAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnDoneFloating).setOnClickListener {
            finish()
        }

        etFloatingSandbox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                val input = s?.toString() ?: ""
                if (input.isBlank()) {
                    tvSandboxFeedback.text = "💡 Ready: Type any mistake to see Dasmo detect it live."
                    tvSandboxFeedback.setTextColor(Color.parseColor("#10B981"))
                    return
                }
                engine.lintDebounced(input, 200L) { lints ->
                    runOnUiThread {
                        if (lints.isEmpty()) {
                            tvSandboxFeedback.text = "✓ Text looks clean! No errors found."
                            tvSandboxFeedback.setTextColor(Color.parseColor("#10B981"))
                        } else {
                            val first = lints.first()
                            val fix = first.primarySuggestion ?: first.kind
                            tvSandboxFeedback.text = "⚠️ Found ${lints.size} issue: '${first.problem}' ➔ '$fix'"
                            tvSandboxFeedback.setTextColor(Color.parseColor("#F43F5E"))
                        }
                    }
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        val hasOverlay = Settings.canDrawOverlays(this)
        if (hasOverlay) {
            tvOverlayStatus.text = "✓ Overlay Allowed"
            tvOverlayStatus.setTextColor(Color.parseColor("#10B981"))
            btnGrantOverlay.text = "Permission Granted"
            btnGrantOverlay.isEnabled = false
        } else {
            tvOverlayStatus.text = "Permission required"
            tvOverlayStatus.setTextColor(Color.parseColor("#F59E0B"))
            btnGrantOverlay.text = "Grant Overlay Permission"
            btnGrantOverlay.isEnabled = true
        }

        val isAccessibilityEnabled = isAccessibilityServiceEnabled()
        if (isAccessibilityEnabled) {
            tvAccessibilityStatus.text = "✓ Assistant Active & Ready"
            tvAccessibilityStatus.setTextColor(Color.parseColor("#10B981"))
            btnOpenAccessibility.text = "Service Enabled"
        } else {
            tvAccessibilityStatus.text = "Turn on in Accessibility Settings"
            tvAccessibilityStatus.setTextColor(Color.parseColor("#F59E0B"))
            btnOpenAccessibility.text = "Open Accessibility Settings"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        if (DasmoFloatingAssistantService.isServiceRunning) return true

        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName
        }
    }
}
