package com.example.filetracker.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSpinner

class MultiSelectSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatSpinner(context, attrs, defStyleAttr) {

    private var items = listOf<String>()
    private val selectedItems = mutableSetOf<String>()
    private var onSelectionChangedListener: (() -> Unit)? = null

    fun setItems(items: List<String>, selected: Set<String> = setOf()) {
        android.util.Log.d("MultiSelectSpinner", "setItems called with selected: $selected")
        this.items = if (items.contains("All")) {
            listOf("All") + items.filter { it != "All" }
        } else {
            listOf("All") + items
        }
        selectedItems.clear()
        selectedItems.addAll(if (selected.isEmpty()) setOf("All") else selected)
        android.util.Log.d("MultiSelectSpinner", "selectedItems after setup: $selectedItems")
        adapter = MultiSelectAdapter()
        post {
            (adapter as? MultiSelectAdapter)?.notifyDataSetChanged()
        }
        updateDisplayText()
    }

    fun getSelectedItems(): Set<String> = selectedItems.toSet()

    fun setOnSelectionChangedListener(listener: () -> Unit) {
        onSelectionChangedListener = listener
    }

    private fun updateDisplayText() {
        post {
            (adapter as? MultiSelectAdapter)?.notifyDataSetChanged()
        }
    }

    private inner class MultiSelectAdapter : BaseAdapter() {
        override fun getCount(): Int = if (items.isEmpty()) 1 else items.size

        override fun getItem(position: Int): String =
            if (items.isEmpty()) "Loading..." else items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(
                android.R.layout.simple_spinner_item, parent, false
            )
            val textView = view.findViewById<TextView>(android.R.id.text1)

            val displayText = when {
                items.isEmpty() -> "Loading..."
                selectedItems.contains("All") -> "All Notify"
                selectedItems.isEmpty() -> "Select packages"
                selectedItems.size == 1 -> {
                    val item = selectedItems.first()
                    if (item.length > 11) "~" + item.takeLast(11) else item
                }
                else -> "${selectedItems.size} packages"
            }
            textView.text = displayText
            return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
            if (items.isEmpty()) {
                val view = convertView ?: LayoutInflater.from(context).inflate(
                    android.R.layout.simple_spinner_dropdown_item, parent, false
                )
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.text = "Loading..."
                return view
            }

            val view = convertView ?: LayoutInflater.from(context).inflate(
                android.R.layout.simple_spinner_dropdown_item, parent, false
            )

            val textView = view.findViewById<TextView>(android.R.id.text1)
            val item = items[position]
            val isSelected = selectedItems.contains(item)
            textView.text = if (isSelected) "✓ $item" else "  $item"

            view.setOnClickListener {
                if (item == "All") {
                    selectedItems.clear()
                    selectedItems.add("All")
                } else {
                    if (selectedItems.contains(item)) {
                        selectedItems.remove(item)
                        if (selectedItems.isEmpty()) {
                            selectedItems.add("All")
                        }
                    } else {
                        selectedItems.remove("All")
                        selectedItems.add(item)
                    }
                }

                notifyDataSetChanged()
                updateDisplayText()
                onSelectionChangedListener?.invoke()
            }

            return view
        }
    }
}