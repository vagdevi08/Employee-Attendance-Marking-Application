package com.kbyai.facerecognition;

import android.graphics.Bitmap;

public class Person {

    public String employeeId;
    public String name;
    public Bitmap face;
    public byte[] templates;

    public Person() {

    }

    public Person(String employeeId, String name, Bitmap face, byte[] templates) {
        this.employeeId = employeeId;
        this.name = name;
        this.face = face;
        this.templates = templates;
    }

    // Legacy constructor for backward compatibility
    public Person(String name, Bitmap face, byte[] templates) {
        this.employeeId = "";
        this.name = name;
        this.face = face;
        this.templates = templates;
    }
}
