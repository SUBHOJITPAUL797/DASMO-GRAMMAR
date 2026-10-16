package com.dasmo.grammar.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dasmo.grammar.R
import com.dasmo.grammar.engine.DasmoLint

class LintsAdapter(
    private var lints: List<DasmoLint>,
    private val onApplySuggestion: (lint: DasmoLint, replacement: String) -> Unit,
    private val onIgnoreLint: (lint: DasmoLint) -> Unit,
    private val onAddToDictionary: (lint: DasmoLint) -> Unit
) : RecyclerView.Adapter<LintsAdapter.LintViewHolder>() {

    fun updateLints(newLints: List<DasmoLint>) {
        this.lints = newLints
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LintViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lint_card, parent, false)
        return LintViewHolder(view)
    }

    override fun onBindViewHolder(holder: LintViewHolder, position: Int) {
        holder.bind(lints[position])
    }

    override fun getItemCount(): Int = lints.size

    inner class LintViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvProblem: TextView = itemView.findViewById(R.id.tvProblem)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val suggestionsLayout: LinearLayout = itemView.findViewById(R.id.suggestionsLayout)
        private val btnIgnore: TextView = itemView.findViewById(R.id.btnIgnore)
        private val btnAddDict: TextView = itemView.findViewById(R.id.btnAddDict)
        private val vCategoryIndicator: View = itemView.findViewById(R.id.vCategoryIndicator)

        fun bind(lint: DasmoLint) {
            tvProblem.text = lint.problem
            tvCategory.text = lint.kind
            tvMessage.text = lint.message

            val categoryColor = Color.parseColor(lint.colorHex.ifEmpty { "#F43F5E" })
            vCategoryIndicator.setBackgroundColor(categoryColor)

            // Category tag color
            val tagBg = GradientDrawable().apply {
                setColor(Color.parseColor("#374151"))
                cornerRadius = dp(6).toFloat()
            }
            tvCategory.background = tagBg
            tvCategory.setTextColor(Color.WHITE)

            suggestionsLayout.removeAllViews()
            for (suggestion in lint.suggestions) {
                val chip = TextView(itemView.context).apply {
                    text = suggestion
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)

                    val bg = GradientDrawable().apply {
                        setColor(Color.parseColor("#10B981")) // Green accent
                        cornerRadius = dp(16).toFloat()
                    }
                    background = bg
                    setPadding(dp(12), dp(6), dp(12), dp(6))

                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dp(8)
                        topMargin = dp(4)
                        bottomMargin = dp(4)
                    }
                    layoutParams = params

                    setOnClickListener {
                        onApplySuggestion(lint, suggestion)
                    }
                }
                suggestionsLayout.addView(chip)
            }

            btnIgnore.setOnClickListener {
                onIgnoreLint(lint)
            }

            btnAddDict.setOnClickListener {
                onAddToDictionary(lint)
            }
        }

        private fun dp(value: Int): Int {
            return (value * itemView.context.resources.displayMetrics.density).toInt()
        }
    }
}
