package com.kbyai.facerecognition;

public class AttendanceLog {

    public String employeeId;
    public String name;
    public long timestamp;
    public String type; // "CHECK_IN" or "CHECK_OUT"

    public AttendanceLog(String employeeId, String name, long timestamp, String type) {
        this.employeeId = employeeId;
        this.name = name;
        this.timestamp = timestamp;
        this.type = type;
    }

    // Legacy constructor for backward compatibility
    public AttendanceLog(String employeeId, String name, long timestamp) {
        this(employeeId, name, timestamp, "CHECK_IN");
    }
}

