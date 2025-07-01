package com.example.filetracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.filetracker.R
import com.example.filetracker.data.EventLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class EventLogAdapter : ListAdapter<EventLog, EventLogAdapter.ViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_event_log, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.eventDate)
        private val messageText: TextView = itemView.findViewById(R.id.eventMessage)
        fun bind(entry: EventLog) {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("Europe/Moscow")
            dateText.text = sdf.format(Date(entry.timestamp))
            messageText.text = entry.message
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<EventLog>() {
            override fun areItemsTheSame(a: EventLog, b: EventLog) = a.id == b.id
            override fun areContentsTheSame(a: EventLog, b: EventLog) = a == b
        }
    }
}