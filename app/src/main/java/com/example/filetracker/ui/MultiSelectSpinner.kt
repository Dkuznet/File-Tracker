package com.example.filetracker.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView

class MultiSelectSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : Spinner(context, attrs, defStyleAttr) {

    private var items = listOf<String>()
    private val selectedItems = mutableSetOf<String>()
    private var onSelectionChangedListener: (() -> Unit)? = null

    fun setItems(items: List<String>, selected: Set<String> = setOf()) {
        this.items = items
        selectedItems.clear()
        selectedItems.addAll(selected)
        adapter = MultiSelectAdapter()
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
            val view = LayoutInflater.from(context).inflate(
                android.R.layout.simple_spinner_item, parent, false
            )
            val textView = view.findViewById<TextView>(android.R.id.text1)

            val displayText = when {
                items.isEmpty() -> "Loading..."
                selectedItems.contains("All") -> "All packages"
                selectedItems.isEmpty() -> "Select packages"
                selectedItems.size == 1 -> selectedItems.first()
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
                android.R.layout.simple_list_item_multiple_choice, parent, false
            )

            val checkBox = view.findViewById<CheckBox>(android.R.id.checkbox)
            val textView = view.findViewById<TextView>(android.R.id.text1)

            if (checkBox == null || textView == null) {
                // Fallback to simple layout if checkbox layout fails
                val fallbackView = LayoutInflater.from(context).inflate(
                    android.R.layout.simple_spinner_dropdown_item, parent, false
                )
                val fallbackText = fallbackView.findViewById<TextView>(android.R.id.text1)
                fallbackText.text = if (position < items.size) items[position] else "Error"
                return fallbackView
            }

            val item = items[position]
            textView.text = item
            checkBox.isChecked = selectedItems.contains(item)

            view.setOnClickListener {
                if (item == "All") {
                    if (selectedItems.contains("All")) {
                        selectedItems.clear()
                    } else {
                        selectedItems.clear()
                        selectedItems.add("All")
                    }
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

                checkBox.isChecked = selectedItems.contains(item)
                updateDisplayText()
                onSelectionChangedListener?.invoke()
                notifyDataSetChanged()
            }

            return view
        }
    }
}