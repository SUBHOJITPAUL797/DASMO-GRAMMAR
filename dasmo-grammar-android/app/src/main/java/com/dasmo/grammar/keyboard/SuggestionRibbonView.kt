package com.dasmo.grammar.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.dasmo.grammar.engine.DasmoLint

class SuggestionRibbonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val badgeContainer: LinearLayout
    private val badgeIcon: TextView
    private val badgeCount: TextView
    private val scrollView: HorizontalScrollView
    private val suggestionsContainer: LinearLayout
    private val explanationContainer: LinearLayout
    private val explanationText: TextView
    private val explanationAcceptBtn: TextView
    private val explanationIgnoreBtn: TextView

    private var activeLints: List<DasmoLint> = emptyList()
    private var selectedLint: DasmoLint? = null

    var onSuggestionClicked: ((lint: DasmoLint, replacement: String) -> Unit)? = null
    var onIgnoreClicked: ((lint: DasmoLint) -> Unit)? = null
    var onSettingsClicked: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#1E1E2E"))

        val ribbonRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(46))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        // Dasmo Badge Pill
        badgeContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#313244"))
                cornerRadius = dp(16).toFloat()
            }
            background = bg
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(34)).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                toggleExplanation()
            }
        }

        badgeIcon = TextView(context).apply {
            text = "D"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981")) // Emerald Green
        }

        badgeCount = TextView(context).apply {
            text = "0"
            textSize = 11f
            setTextColor(Color.parseColor("#CDD6F4"))
            setPadding(dp(4), 0, 0, 0)
        }

        badgeContainer.addView(badgeIcon)
        badgeContainer.addView(badgeCount)
        ribbonRow.addView(badgeContainer)

        // Scrollable Suggestion Chips
        scrollView = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f)
            isHorizontalScrollBarEnabled = false
        }

        suggestionsContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        }

        scrollView.addView(suggestionsContainer)
        ribbonRow.addView(scrollView)

        // Settings / Dismiss button
        val settingsBtn = TextView(context).apply {
            text = "⚙"
            textSize = 16f
            setTextColor(Color.parseColor("#A6ADC8"))
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(dp(36), dp(34))
            setOnClickListener {
                onSettingsClicked?.invoke()
            }
        }
        ribbonRow.addView(settingsBtn)

        addView(ribbonRow)

        // Explanation popup bar (collapsible)
        explanationContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#24273A"))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(40))
            setPadding(dp(12), dp(4), dp(12), dp(4))
            visibility = View.GONE
        }

        explanationText = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#BAC2DE"))
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
            maxLines = 1
        }

        explanationAcceptBtn = TextView(context).apply {
            text = "Apply"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                val lint = selectedLint ?: activeLints.firstOrNull()
                val repl = lint?.primarySuggestion
                if (lint != null && repl != null) {
                    onSuggestionClicked?.invoke(lint, repl)
                    hideExplanation()
                }
            }
        }

        explanationIgnoreBtn = TextView(context).apply {
            text = "Ignore"
            textSize = 12f
            setTextColor(Color.parseColor("#F38BA8"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                val lint = selectedLint ?: activeLints.firstOrNull()
                if (lint != null) {
                    onIgnoreClicked?.invoke(lint)
                    hideExplanation()
                }
            }
        }

        explanationContainer.addView(explanationText)
        explanationContainer.addView(explanationAcceptBtn)
        explanationContainer.addView(explanationIgnoreBtn)

        addView(explanationContainer)
    }

    fun setLints(lints: List<DasmoLint>) {
        activeLints = lints
        suggestionsContainer.removeAllViews()

        if (lints.isEmpty()) {
            badgeCount.text = "✓"
            badgeCount.setTextColor(Color.parseColor("#10B981"))
            badgeIcon.setTextColor(Color.parseColor("#10B981"))
            hideExplanation()

            val placeholder = TextView(context).apply {
                text = "Dasmo: All good"
                textSize = 12f
                setTextColor(Color.parseColor("#6C7086"))
                setPadding(dp(8), 0, 0, 0)
            }
            suggestionsContainer.addView(placeholder)
            return
        }

        badgeCount.text = lints.size.toString()
        badgeCount.setTextColor(Color.parseColor("#F38BA8"))
        badgeIcon.setTextColor(Color.parseColor("#F38BA8"))

        for (lint in lints) {
            for (suggestion in lint.suggestions.take(3)) {
                val chip = createSuggestionChip(lint, suggestion)
                suggestionsContainer.addView(chip)
            }
        }
    }

    private fun createSuggestionChip(lint: DasmoLint, suggestion: String): TextView {
        return TextView(context).apply {
            text = suggestion
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FFFFFF"))

            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#45475A"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor(lint.colorHex.ifEmpty { "#10B981" }))
            }
            background = bg
            setPadding(dp(12), dp(6), dp(12), dp(6))

            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(6)
            }

            setOnClickListener {
                onSuggestionClicked?.invoke(lint, suggestion)
            }

            setOnLongClickListener {
                selectedLint = lint
                showExplanation(lint)
                true
            }
        }
    }

    private fun showExplanation(lint: DasmoLint) {
        explanationText.text = "${lint.problem} → ${lint.message}"
        explanationContainer.visibility = View.VISIBLE
    }

    private fun hideExplanation() {
        explanationContainer.visibility = View.GONE
        selectedLint = null
    }

    private fun toggleExplanation() {
        if (explanationContainer.visibility == View.VISIBLE) {
            hideExplanation()
        } else if (activeLints.isNotEmpty()) {
            showExplanation(activeLints.first())
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
