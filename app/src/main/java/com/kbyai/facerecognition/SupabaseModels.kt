package com.kbyai.facerecognition

data class SupabaseAttendance(
    val employee_id: String,
    val name: String,
    val timestamp: String, // ISO-8601 UTC
    val type: String       // CHECK_IN or CHECK_OUT
)

