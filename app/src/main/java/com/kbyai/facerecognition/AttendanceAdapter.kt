package com.kbyai.facerecognition

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class AttendanceAdapter(
    context: Context,
    attendance: ArrayList<AttendanceLog>
) : ArrayAdapter<AttendanceLog>(context, 0, attendance) {

    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault())

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val rowView = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_attendance, parent, false)

        val attendanceLog = getItem(position)
        val textEmployeeInfo = rowView.findViewById<TextView>(R.id.textEmployeeInfo)
        val textTime = rowView.findViewById<TextView>(R.id.textTimestamp)

        // Display as "ID - Name [Type]" format
        val employeeId = attendanceLog?.employeeId?.takeIf { it.isNotEmpty() }
        val employeeName = attendanceLog?.name ?: ""
        val attendanceType = attendanceLog?.type ?: "CHECK_IN"
        val typeText = if (attendanceType == "CHECK_IN") " [IN]" else " [OUT]"
        
        if (employeeId != null && employeeId.isNotEmpty()) {
            textEmployeeInfo.text = "$employeeId - $employeeName$typeText"
        } else {
            textEmployeeInfo.text = "$employeeName$typeText"
        }

        textTime.text = attendanceLog?.timestamp?.let { dateFormatter.format(Date(it)) } ?: ""

        return rowView
    }
}
