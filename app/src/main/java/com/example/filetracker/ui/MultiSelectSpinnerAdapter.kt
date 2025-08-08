package com.example.filetracker.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.TextView

class MultiSelectSpinnerAdapter(
    private val context: Context,
    private val items: List<String>,
    private val selectedItems: MutableSet<String>
) : BaseAdapter() {

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): String = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            android.R.layout.simple_list_item_multiple_choice, parent, false
        )

        val checkBox = view.findViewById<CheckBox>(android.R.id.checkbox)
        val textView = view.findViewById<TextView>(android.R.id.text1)

        val item = items[position]
        textView.text = item
        checkBox.isChecked = selectedItems.contains(item)

        view.setOnClickListener {
            if (selectedItems.contains(item)) {
                selectedItems.remove(item)
            } else {
                selectedItems.add(item)
            }
            checkBox.isChecked = selectedItems.contains(item)
        }

        return view
    }
}