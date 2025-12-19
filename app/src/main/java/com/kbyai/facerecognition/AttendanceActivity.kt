package com.kbyai.facerecognition

import android.os.Bundle
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AttendanceActivity : AppCompatActivity() {

    private lateinit var dbManager: DBManager
    private lateinit var adapter: AttendanceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        dbManager = DBManager(this)
        dbManager.loadAttendance()

        adapter = AttendanceAdapter(this, DBManager.attendanceList)
        val listView = findViewById<ListView>(R.id.listAttendance)
        listView.adapter = adapter

        updateEmptyState()

        findViewById<Button>(R.id.buttonClearLog).setOnClickListener {
            dbManager.clearAttendance()
            adapter.notifyDataSetChanged()
            updateEmptyState()
        }
    }

    private fun updateEmptyState() {
        val emptyView = findViewById<TextView>(R.id.textEmptyAttendance)
        if (DBManager.attendanceList.isEmpty()) {
            emptyView.text = getString(R.string.attendance_empty)
            emptyView.visibility = android.view.View.VISIBLE
        } else {
            emptyView.visibility = android.view.View.GONE
        }
    }
}

