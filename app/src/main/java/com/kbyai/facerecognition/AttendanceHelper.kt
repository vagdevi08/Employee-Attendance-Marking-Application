package com.kbyai.facerecognition

import android.content.Context
import java.util.*

object AttendanceHelper {
    
    fun determineAttendanceType(context: Context, employeeId: String, currentTime: Long): String {
        if (employeeId.isEmpty()) {
            return "CHECK_IN" // Default to CHECK_IN if no employee ID
        }
        
        val dbManager = DBManager(context)
        dbManager.loadAttendance()
        
        // Get last attendance record for today
        val lastAttendance = dbManager.getLastAttendanceToday(employeeId, currentTime)
        
        val cal = Calendar.getInstance()
        cal.timeInMillis = currentTime
        
        // If no attendance today, allow check-in if within check-in window
        if (lastAttendance == null) {
            val checkInStart = SettingsActivity.getCheckInStartTime(context)
            val checkInEnd = SettingsActivity.getCheckInEndTime(context)
            if (SettingsActivity.isTimeInWindow(cal, checkInStart, checkInEnd)) {
                return "CHECK_IN"
            }
            return "NONE" // Not within check-in window
        }
        
        // If last attendance was CHECK_IN, allow check-out if within check-out window
        if (lastAttendance.type == "CHECK_IN") {
            val checkOutStart = SettingsActivity.getCheckOutStartTime(context)
            val checkOutEnd = SettingsActivity.getCheckOutEndTime(context)
            if (SettingsActivity.isTimeInWindow(cal, checkOutStart, checkOutEnd)) {
                return "CHECK_OUT"
            }
            return "NONE" // Already checked in, but not within check-out window
        }
        
        // If last attendance was CHECK_OUT, allow check-in if within check-in window
        if (lastAttendance.type == "CHECK_OUT") {
            val checkInStart = SettingsActivity.getCheckInStartTime(context)
            val checkInEnd = SettingsActivity.getCheckInEndTime(context)
            if (SettingsActivity.isTimeInWindow(cal, checkInStart, checkInEnd)) {
                return "CHECK_IN"
            }
            return "NONE" // Already checked out, but not within check-in window
        }
        
        return "NONE"
    }
    
    fun getAttendanceTypeMessage(context: Context, employeeId: String, currentTime: Long): String {
        val attendanceType = determineAttendanceType(context, employeeId, currentTime)
        return when (attendanceType) {
            "CHECK_IN" -> "Check-in successful"
            "CHECK_OUT" -> "Check-out successful"
            "NONE" -> {
                val dbManager = DBManager(context)
                dbManager.loadAttendance()
                val lastAttendance = dbManager.getLastAttendanceToday(employeeId, currentTime)
                if (lastAttendance != null && lastAttendance.type == "CHECK_IN") {
                    "Already checked in. Next attendance is for leaving around exit time."
                } else {
                    "Attendance not allowed at this time. Please check-in/check-out during designated time windows."
                }
            }
            else -> "Unable to determine attendance type"
        }
    }
}

