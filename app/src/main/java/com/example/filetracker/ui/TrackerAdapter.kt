package com.example.filetracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.ToggleButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.filetracker.R
import com.example.filetracker.data.Tracker
import com.example.filetracker.util.getShortPath

class TrackerAdapter(
    private val onDeleteClick: (Tracker) -> Unit,
    private val onToggleActive: (Tracker, Boolean) -> Unit
) : ListAdapter<Tracker, TrackerAdapter.TrackerViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tracker, parent, false)
        return TrackerViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrackerViewHolder, position: Int) {
        val tracker = getItem(position)
        holder.bind(tracker, onDeleteClick, onToggleActive)
    }

    class TrackerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val sourceText: TextView = itemView.findViewById(R.id.sourceText)

        // private val destText: TextView = itemView.findViewById(R.id.destText)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        private val toggleActive: ToggleButton =
            itemView.findViewById(R.id.toggleActiveButton)

        fun bind(
            tracker: Tracker,
            onDeleteClick: (Tracker) -> Unit,
            onToggleActive: (Tracker, Boolean) -> Unit
        ) {
            sourceText.text = getShortPath(tracker.sourceUri)
            // destText.text = getShortPath(tracker.destUri)
            deleteButton.setOnClickListener { onDeleteClick(tracker) }
            toggleActive.isChecked = tracker.isActive
            toggleActive.setOnCheckedChangeListener { _, isChecked ->
                if (tracker.isActive != isChecked) onToggleActive(tracker, isChecked)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Tracker>() {
            override fun areItemsTheSame(oldItem: Tracker, newItem: Tracker): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Tracker, newItem: Tracker): Boolean =
                oldItem == newItem
        }
    }
}