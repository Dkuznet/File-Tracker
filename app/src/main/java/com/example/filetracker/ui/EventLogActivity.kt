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
    private lateinit var filterNotifications: CheckBox
    private lateinit var filterSystem: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_log)

        prefs = getSharedPreferences("eventlog_prefs", MODE_PRIVATE)
        val extendedLoggingCheckBox = findViewById<CheckBox>(R.id.extendedLoggingCheckBox)
        filterNotifications = findViewById(R.id.filterNotifications)
        filterSystem = findViewById(R.id.filterSystem)

        filterNotifications.isChecked = prefs.getBoolean("filter_notifications", true)
        filterSystem.isChecked = prefs.getBoolean("filter_system", true)
        extendedLoggingCheckBox.isChecked = prefs.getBoolean("extended_logging", false)

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

            val selectedTypes = mutableListOf<String>()
            if (filterNotifications.isChecked) selectedTypes.add("notification")
            if (filterSystem.isChecked) selectedTypes.add("system")
            if (extendedLoggingCheckBox.isChecked) selectedTypes.add("extended")

            if (selectedTypes.isNotEmpty()) {
                eventLogDao.getRecentFilteredLimited(limit, selectedTypes)
                    .observe(this, logObserver!!)
            } else {
                adapter.submitList(emptyList())
            }
        }

        val filterChangeListener = { _: Any, _: Boolean ->
            prefs.edit()
                .putBoolean("filter_notifications", filterNotifications.isChecked)
                .putBoolean("filter_system", filterSystem.isChecked)
                .putBoolean("extended_logging", extendedLoggingCheckBox.isChecked)
                .apply()
            observeLogs(currentLimit)
        }

        filterNotifications.setOnCheckedChangeListener(filterChangeListener)
        filterSystem.setOnCheckedChangeListener(filterChangeListener)
        extendedLoggingCheckBox.setOnCheckedChangeListener(filterChangeListener)

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