package com.dasmo.grammar.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.dasmo.grammar.R
import com.dasmo.grammar.engine.DasmoEngine
import com.dasmo.grammar.engine.DasmoLint
import com.dasmo.grammar.engine.DasmoLogger

/**
 * Enterprise Accessibility Service for Dasmo Grammar.
 * Allows users to keep their favorite Samsung Keyboard, Gboard, or any OEM keyboard
 * as default, while providing a real-time floating Grammarly-style assistant.
 */
class DasmoFloatingAssistantService : AccessibilityService() {

    companion object {
        private const val TAG = "DasmoFloatingAssistant"
        var isServiceRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var engine: DasmoEngine
    private val mainHandler = Handler(Looper.getMainLooper())

    private var floatingView: View? = null
    private var windowParams: WindowManager.LayoutParams? = null

    private var cardFloatingExplanation: View? = null
    private var tvFloatingProblem: TextView? = null
    private var tvFloatingCategory: TextView? = null
    private var tvFloatingMessage: TextView? = null
    private var llFloatingSuggestions: LinearLayout? = null
    private var tvBubbleBadgeCount: TextView? = null
    private var tvBubblePrimaryFix: TextView? = null

    private var activeLints: List<DasmoLint> = emptyList()
    private var lastObservedText: String = ""
    private var targetNode: AccessibilityNodeInfo? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        DasmoLogger.i(TAG, "Dasmo Floating Assistant Service Connected successfully")
        isServiceRunning = true
        engine = DasmoEngine.getInstance(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        initFloatingOverlay()
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun initFloatingOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            DasmoLogger.w(TAG, "Cannot draw overlay: SYSTEM_ALERT_WINDOW permission missing")
            return
        }

        if (floatingView != null) return

        try {
            val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_DasmoGrammar)
            val inflater = LayoutInflater.from(themedContext)
            val view = inflater.inflate(R.layout.view_floating_assistant, null)
            floatingView = view

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                x = dp(16)
                y = dp(340) // Positioned comfortably above Samsung Keyboard
            }
            windowParams = params

            // Bind views
            cardFloatingExplanation = view.findViewById(R.id.cardFloatingExplanation)
            tvFloatingProblem = view.findViewById(R.id.tvFloatingProblem)
            tvFloatingCategory = view.findViewById(R.id.tvFloatingCategory)
            tvFloatingMessage = view.findViewById(R.id.tvFloatingMessage)
            llFloatingSuggestions = view.findViewById(R.id.llFloatingSuggestions)
            tvBubbleBadgeCount = view.findViewById(R.id.tvBubbleBadgeCount)
            tvBubblePrimaryFix = view.findViewById(R.id.tvBubblePrimaryFix)

            val bubbleBg = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            view.findViewById<View>(R.id.llFloatingBubble).background = bubbleBg

            // Round the badge icon and counter
            view.findViewById<TextView>(R.id.tvLogoIcon)?.background = GradientDrawable().apply {
                setColor(Color.parseColor("#10B981"))
                cornerRadius = dp(16).toFloat()
            }
            tvBubbleBadgeCount?.background = GradientDrawable().apply {
                setColor(Color.parseColor("#F43F5E"))
                cornerRadius = dp(8).toFloat()
            }

            view.findViewById<View>(R.id.btnFloatingCloseCard).setOnClickListener {
                cardFloatingExplanation?.animate()
                    ?.alpha(0f)
                    ?.scaleX(0.85f)
                    ?.scaleY(0.85f)
                    ?.setDuration(160)
                    ?.withEndAction { cardFloatingExplanation?.visibility = View.GONE }
                    ?.start()
            }

            // Bubble Click -> Toggle Full Explanation Card
            view.findViewById<View>(R.id.llFloatingBubble).setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                toggleExplanationCard()
            }

            // Drag to reposition anywhere on screen
            setupDragListener(view.findViewById(R.id.llFloatingBubble))

            // Initially hidden until text errors detected
            view.visibility = View.GONE
            windowManager.addView(view, params)
            DasmoLogger.i(TAG, "Floating overlay added to WindowManager")
        } catch (e: Exception) {
            DasmoLogger.e(TAG, "Failed adding floating overlay view", e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener(dragView: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDrag = false

        dragView.setOnTouchListener { v, event ->
            val params = windowParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDrag = false
                    v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(100).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (initialTouchX - event.rawX).toInt()
                    val dy = (initialTouchY - event.rawY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDrag = true
                    }
                    if (isDrag) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(floatingView, params)
                        } catch (e: Exception) {
                            // ignore layout updates during rapid drag
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    if (isDrag) {
                        snapToNearestEdge(params)
                    } else {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToNearestEdge(params: WindowManager.LayoutParams) {
        val screenWidth = resources.displayMetrics.widthPixels
        val viewWidth = floatingView?.width ?: dp(180)
        val targetX = if (params.x < (screenWidth - viewWidth) / 2) {
            dp(8)
        } else {
            (screenWidth - viewWidth - dp(8)).coerceAtLeast(dp(8))
        }

        val startX = params.x
        val animator = android.animation.ValueAnimator.ofInt(startX, targetX).apply {
            duration = 200
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { va ->
                params.x = va.animatedValue as Int
                try {
                    windowManager.updateViewLayout(floatingView, params)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
        animator.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                inspectActiveNode(event)
            }
        }
    }

    private fun inspectActiveNode(event: AccessibilityEvent) {
        val node = if (event.source?.isEditable == true) {
            event.source
        } else {
            findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } ?: targetNode?.takeIf { it.isEditable }

        if (node == null || !node.isEditable) {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                val currentFocus = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (currentFocus == null || !currentFocus.isEditable) {
                    hideOverlay()
                }
            }
            return
        }

        targetNode = node
        val nodeText = node.text?.toString()
        val text = if (!nodeText.isNullOrEmpty()) {
            nodeText
        } else if (event.text.isNotEmpty()) {
            event.text.joinToString("")
        } else {
            ""
        }

        if (text == lastObservedText) return
        lastObservedText = text

        if (text.isBlank()) {
            activeLints = emptyList()
            showReadyState("Ready")
            return
        }

        // Debounced grammar check on the text typed via Samsung Keyboard
        engine.lintDebounced(text, 250L) { lints ->
            mainHandler.post {
                activeLints = lints
                if (lints.isEmpty()) {
                    showReadyState("✓ All clear")
                } else {
                    showOverlayWithLints(lints)
                }
            }
        }
    }

    private fun showReadyState(statusText: String) {
        if (floatingView == null) {
            initFloatingOverlay()
        }
        val view = floatingView ?: return
        tvBubbleBadgeCount?.visibility = View.GONE
        tvBubblePrimaryFix?.text = statusText
        if (view.visibility != View.VISIBLE) {
            view.alpha = 0f
            view.visibility = View.VISIBLE
            view.animate().alpha(1f).setDuration(180).start()
        }
        try {
            windowParams?.let { windowManager.updateViewLayout(view, it) }
        } catch (e: Exception) {
            // ignore layout sync
        }
    }

    private fun showOverlayWithLints(lints: List<DasmoLint>) {
        if (floatingView == null) {
            initFloatingOverlay()
        }
        val view = floatingView ?: return

        tvBubbleBadgeCount?.visibility = View.VISIBLE
        tvBubbleBadgeCount?.text = lints.size.toString()
        val firstLint = lints.firstOrNull()
        val primaryFix = firstLint?.primarySuggestion

        if (primaryFix != null) {
            tvBubblePrimaryFix?.text = "${firstLint.problem} → $primaryFix"
        } else {
            tvBubblePrimaryFix?.text = "${lints.size} issues found"
        }

        if (view.visibility != View.VISIBLE) {
            view.alpha = 0f
            view.visibility = View.VISIBLE
            view.animate().alpha(1f).setDuration(180).start()
        }
        try {
            windowParams?.let { windowManager.updateViewLayout(view, it) }
        } catch (e: Exception) {
            // ignore layout sync
        }
    }

    private fun hideOverlay() {
        floatingView?.visibility = View.GONE
        cardFloatingExplanation?.visibility = View.GONE
    }

    private fun toggleExplanationCard() {
        val card = cardFloatingExplanation ?: return
        if (card.visibility == View.VISIBLE) {
            card.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(160)
                .withEndAction { card.visibility = View.GONE }
                .start()
            return
        }

        val lint = activeLints.firstOrNull()
        if (lint == null) {
            android.widget.Toast.makeText(this, "✓ All clear! No errors found.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        tvFloatingProblem?.text = lint.problem
        tvFloatingCategory?.text = lint.kind
        tvFloatingMessage?.text = lint.message

        llFloatingSuggestions?.removeAllViews()
        for (suggestion in lint.suggestions) {
            val chip = TextView(this).apply {
                text = suggestion
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)

                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#10B981"))
                    cornerRadius = dp(14).toFloat()
                }
                background = bg
                setPadding(dp(12), dp(6), dp(12), dp(6))

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(8)
                }

                setOnClickListener {
                    it.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).withEndAction {
                        applyFixToActiveInput(lint, suggestion)
                        card.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(150).withEndAction {
                            card.visibility = View.GONE
                        }.start()
                    }.start()
                }
            }
            llFloatingSuggestions?.addView(chip)
        }

        card.visibility = View.VISIBLE
        card.alpha = 0f
        card.scaleX = 0.85f
        card.scaleY = 0.85f
        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .start()
    }

    private fun applyFixToActiveInput(lint: DasmoLint, replacement: String) {
        val node = targetNode ?: findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val currentText = node.text?.toString() ?: return

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
            try {
                val updatedText = currentText.substring(0, index) + replacement + currentText.substring(index + problem.length)
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updatedText)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                DasmoLogger.i(TAG, "Replaced '$problem' with '$replacement' via Accessibility in active input field")

                lastObservedText = updatedText
                // Trigger fresh lint check
                engine.lintDebounced(updatedText, 100L) { lints ->
                    mainHandler.post {
                        activeLints = lints
                        if (lints.isEmpty()) {
                            showReadyState("✓ Fixed")
                            mainHandler.postDelayed({
                                if (activeLints.isEmpty() && floatingView?.visibility == View.VISIBLE) {
                                    showReadyState("✓ All clear")
                                }
                            }, 1800L)
                        } else {
                            showOverlayWithLints(lints)
                        }
                    }
                }
            } catch (e: Exception) {
                DasmoLogger.e(TAG, "Error performing ACTION_SET_TEXT via accessibility", e)
            }
        }
    }

    override fun onInterrupt() {
        DasmoLogger.w(TAG, "Dasmo Floating Assistant interrupted")
        hideOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // ignore
            }
        }
        floatingView = null
        DasmoLogger.i(TAG, "Dasmo Floating Assistant Service Destroyed")
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
