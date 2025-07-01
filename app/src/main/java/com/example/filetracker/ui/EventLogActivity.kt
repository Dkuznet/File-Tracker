package com.example.filetracker.ui

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filetracker.R
import com.example.filetracker.data.AppDatabase

class EventLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_log)

        val recycler = findViewById<RecyclerView>(R.id.eventRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        val adapter = EventLogAdapter()
        recycler.adapter = adapter
        AppDatabase.getDatabase(this).eventLogDao().getRecent().observe(this, Observer {
            adapter.submitList(it)
        })

        // Обработка нажатия кнопки "Назад"
        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }
    }
}