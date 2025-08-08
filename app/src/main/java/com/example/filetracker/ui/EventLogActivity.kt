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
    private lateinit var filterSystem: CheckBox
    private lateinit var packageSpinner: MultiSelectSpinner
    private val selectedPackages = mutableSetOf<String>()
    private var allPackages = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_log)

        prefs = getSharedPreferences("eventlog_prefs", MODE_PRIVATE)
        val extendedLoggingCheckBox = findViewById<CheckBox>(R.id.extendedLoggingCheckBox)
        filterSystem = findViewById(R.id.filterSystem)
        packageSpinner = findViewById(R.id.packageSpinner)

        filterSystem.isChecked = prefs.getBoolean("filter_system", true)
        extendedLoggingCheckBox.isChecked = prefs.getBoolean("extended_logging", false)

        // Load selected packages from preferences
        val savedPackages = prefs.getStringSet("selected_packages", setOf("All")) ?: setOf("All")
        selectedPackages.addAll(savedPackages)

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
            val hasNotificationPackages =
                selectedPackages.isNotEmpty() && !selectedPackages.contains("All")
            if (hasNotificationPackages || selectedPackages.contains("All")) selectedTypes.add("notification")
            if (filterSystem.isChecked) selectedTypes.add("system")
            if (extendedLoggingCheckBox.isChecked) selectedTypes.add("extended")

            if (selectedTypes.isNotEmpty()) {
                val showAllPackages = selectedPackages.contains("All") || selectedPackages.isEmpty()
                val packageFilter = if (showAllPackages) emptyList() else selectedPackages.toList()
                eventLogDao.getRecentFilteredByPackage(
                    limit,
                    selectedTypes,
                    packageFilter,
                    showAllPackages
                )
                    .observe(this, logObserver!!)
            } else {
                adapter.submitList(emptyList())
            }
        }

        val filterChangeListener = { _: Any, _: Boolean ->
            prefs.edit()
                .putBoolean("filter_system", filterSystem.isChecked)
                .putBoolean("extended_logging", extendedLoggingCheckBox.isChecked)
                .putStringSet("selected_packages", selectedPackages)
                .apply()
            observeLogs(currentLimit)
        }

        filterSystem.setOnCheckedChangeListener(filterChangeListener)
        extendedLoggingCheckBox.setOnCheckedChangeListener(filterChangeListener)

        // Setup package spinner
        packageSpinner.setOnSelectionChangedListener {
            selectedPackages.clear()
            selectedPackages.addAll(packageSpinner.getSelectedItems())
            filterChangeListener(Unit, true)
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
        // Observe package names
        eventLogDao.getDistinctPackageNames().observe(this) { packages ->
            allPackages = listOf("All") + packages
            packageSpinner.setItems(allPackages, selectedPackages)
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