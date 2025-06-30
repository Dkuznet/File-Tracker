package com.example.filetracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filetracker.R

class MainActivity : ComponentActivity() {

    private val trackerViewModel: TrackerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Убедитесь, что у вас есть этот layout

        // Инициализация RecyclerView (или вашего UI)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val adapter = TrackerAdapter(
            onDeleteClick = { tracker -> trackerViewModel.removeTracker(tracker) }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Подписка на LiveData из ViewModel
        trackerViewModel.trackers.observe(this, Observer { trackers ->
            trackers?.let { adapter.submitList(it) }
        })

        // Пример добавления нового трекера (замените на ваш ввод)
        // trackerViewModel.addTracker("sourceUri", "destUri")
    }
}