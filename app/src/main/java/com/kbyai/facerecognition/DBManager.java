package com.kbyai.facerecognition;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

public class DBManager extends SQLiteOpenHelper {

    public static ArrayList<Person> personList = new ArrayList<Person>();
    public static ArrayList<AttendanceLog> attendanceList = new ArrayList<>();

    private static final String DB_NAME = "mydb";
    private static final int DB_VERSION = 2;

    public DBManager(Context context) {
        super(context, DB_NAME , null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // TODO Auto-generated method stub
        db.execSQL(
                "create table person " +
                        "(name text, face blob, templates blob)"
        );

        db.execSQL(
                "create table attendance " +
                        "(id integer primary key autoincrement, name text, timestamp integer)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL(
                    "create table if not exists attendance " +
                            "(id integer primary key autoincrement, name text, timestamp integer)"
            );
        }
    }

    public void insertPerson (String name, Bitmap face, byte[] templates) {

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        face.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] faceJpg = byteArrayOutputStream.toByteArray();

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("name", name);
        contentValues.put("face", faceJpg);
        contentValues.put("templates", templates);
        db.insert("person", null, contentValues);

        personList.add(new Person(name, face, templates));
    }

    public Integer deletePerson (String name) {
        for(int i = 0; i < personList.size(); i ++) {
            if(personList.get(i).name.equals(name)) {
                personList.remove(i);
                i --;
            }
        }

        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete("person",
                "name = ? ",
                new String[] { name });
    }

    public Integer clearDB () {
        personList.clear();

        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("delete from person");
        return 0;
    }

    public void insertAttendance(String name, long timestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("name", name);
        contentValues.put("timestamp", timestamp);
        db.insert("attendance", null, contentValues);

        attendanceList.add(new AttendanceLog(name, timestamp));
    }

    public Integer clearAttendance() {
        attendanceList.clear();

        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("delete from attendance");
        return 0;
    }

    public void loadPerson() {
        personList.clear();

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res =  db.rawQuery( "select * from person", null );
        res.moveToFirst();

        while(res.isAfterLast() == false){
            String name = res.getString(res.getColumnIndex("name"));
            byte[] faceJpg = res.getBlob(res.getColumnIndex("face"));
            byte[] templates = res.getBlob(res.getColumnIndex("templates"));
            Bitmap face = BitmapFactory.decodeByteArray(faceJpg, 0, faceJpg.length);

            Person person = new Person(name, face, templates);
            personList.add(person);

            res.moveToNext();
        }
    }

    public void loadAttendance() {
        attendanceList.clear();

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res = db.rawQuery("select * from attendance order by timestamp desc", null);
        res.moveToFirst();

        while(res.isAfterLast() == false){
            String name = res.getString(res.getColumnIndex("name"));
            long timestamp = res.getLong(res.getColumnIndex("timestamp"));

            AttendanceLog attendanceLog = new AttendanceLog(name, timestamp);
            attendanceList.add(attendanceLog);

            res.moveToNext();
        }
    }
}