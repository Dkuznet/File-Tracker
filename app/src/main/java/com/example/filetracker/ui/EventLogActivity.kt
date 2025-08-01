package com.example.filetracker.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.Spinner
import androidx.activity.ComponentActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filetracker.R
import com.example.filetracker.data.AppDatabase

class EventLogActivity : ComponentActivity() {
    private var currentLimit = 100
    private var logObserver: Observer<List<com.example.filetracker.data.EventLog>>? = null
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_log)

        prefs = getSharedPreferences("eventlog_prefs", MODE_PRIVATE)
        val extendedLoggingCheckBox = findViewById<CheckBox>(R.id.extendedLoggingCheckBox)
        extendedLoggingCheckBox.isChecked = prefs.getBoolean("extended_logging", false)
        extendedLoggingCheckBox.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("extended_logging", isChecked).apply()
        }

        val recycler = findViewById<RecyclerView>(R.id.eventRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        val adapter = EventLogAdapter()
        recycler.adapter = adapter

        val db = AppDatabase.getDatabase()
        val eventLogDao = db.eventLogDao()
        val spinner = findViewById<Spinner>(R.id.logLimitSpinner)
        val countText = findViewById<android.widget.TextView>(R.id.eventLogCountText)

        fun observeLogs(limit: Int) {
            logObserver?.let { eventLogDao.getRecentLimited(currentLimit).removeObserver(it) }
            logObserver = Observer<List<com.example.filetracker.data.EventLog>> {
                adapter.submitList(it)
            }
            eventLogDao.getRecentLimited(limit).observe(this, logObserver!!)
        }

        spinner.setSelection(0) // по умолчанию 100
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selected = parent.getItemAtPosition(position).toString().toIntOrNull() ?: 100
                if (selected != currentLimit) {
                    currentLimit = selected
                    observeLogs(currentLimit)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        observeLogs(currentLimit)

        eventLogDao.getCount().observe(this) { count ->
            countText.text = "Всего: $count"
        }

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }
    }
}