package com.kbyai.facerecognition;

public class AttendanceLog {

    public String employeeId;
    public String name;
    public long timestamp;

    public AttendanceLog(String employeeId, String name, long timestamp) {
        this.employeeId = employeeId;
        this.name = name;
        this.timestamp = timestamp;
    }
}

