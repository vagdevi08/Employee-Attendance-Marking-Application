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
    private static final int DB_VERSION = 4;

    public DBManager(Context context) {
        super(context, DB_NAME , null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // TODO Auto-generated method stub
        db.execSQL(
                "create table person " +
                        "(employeeId text, name text, face blob, templates blob)"
        );

        // db.execSQL(
        //         "create table attendance " +
        //                 "(id integer primary key autoincrement, name text, timestamp integer)"
        // );
        db.execSQL(
            "create table attendance (" +
                "id integer primary key autoincrement, " +
                "employeeId text, " +
                "name text, " +
                "timestamp integer, " +
                "type text DEFAULT 'CHECK_IN')"
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
        if (oldVersion < 3) {
            // Add employeeId column to person table
            try {
                db.execSQL("ALTER TABLE person ADD COLUMN employeeId text DEFAULT ''");
            } catch (Exception e) {
                // Column might already exist, ignore
            }
        }
        if (oldVersion < 4) {
            // Add type column to attendance table
            try {
                db.execSQL("ALTER TABLE attendance ADD COLUMN type text DEFAULT 'CHECK_IN'");
            } catch (Exception e) {
                // Column might already exist, ignore
            }
        }
    }

    public void insertPerson (String employeeId, String name, Bitmap face, byte[] templates) {

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        face.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] faceJpg = byteArrayOutputStream.toByteArray();

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("employeeId", employeeId);
        contentValues.put("name", name);
        contentValues.put("face", faceJpg);
        contentValues.put("templates", templates);
        db.insert("person", null, contentValues);

        personList.add(new Person(employeeId, name, face, templates));
    }

    // Legacy method for backward compatibility
    public void insertPerson (String name, Bitmap face, byte[] templates) {
        insertPerson("", name, face, templates);
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

    public void insertAttendance(String employeeId, String name, long timestamp, String type) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("employeeId", employeeId);
        contentValues.put("name", name);
        contentValues.put("timestamp", timestamp);
        contentValues.put("type", type);
        db.insert("attendance", null, contentValues);

        attendanceList.add(new AttendanceLog(employeeId, name, timestamp, type));
    }

    // Legacy method for backward compatibility
    public void insertAttendance(String employeeId, String name, long timestamp) {
        insertAttendance(employeeId, name, timestamp, "CHECK_IN");
    }

    public AttendanceLog getLastAttendanceToday(String employeeId, long currentTime) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(currentTime);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();
        
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
        cal.set(java.util.Calendar.MINUTE, 59);
        cal.set(java.util.Calendar.SECOND, 59);
        cal.set(java.util.Calendar.MILLISECOND, 999);
        long endOfDay = cal.getTimeInMillis();
        
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res = db.rawQuery(
            "select * from attendance where employeeId = ? and timestamp >= ? and timestamp <= ? order by timestamp desc limit 1",
            new String[] { employeeId, String.valueOf(startOfDay), String.valueOf(endOfDay) }
        );
        
        AttendanceLog lastLog = null;
        if (res.moveToFirst()) {
            String name = res.getString(res.getColumnIndex("name"));
            long timestamp = res.getLong(res.getColumnIndex("timestamp"));
            String type = "CHECK_IN";
            try {
                int typeIndex = res.getColumnIndex("type");
                if (typeIndex >= 0) {
                    String typeValue = res.getString(typeIndex);
                    if (typeValue != null) {
                        type = typeValue;
                    }
                }
            } catch (Exception e) {
                // Column doesn't exist in old database
            }
            lastLog = new AttendanceLog(employeeId, name, timestamp, type);
        }
        res.close();
        return lastLog;
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
            String employeeId = "";
            try {
                int employeeIdIndex = res.getColumnIndex("employeeId");
                if (employeeIdIndex >= 0) {
                    employeeId = res.getString(employeeIdIndex);
                    if (employeeId == null) employeeId = "";
                }
            } catch (Exception e) {
                // Column doesn't exist in old database, use empty string
            }
            String name = res.getString(res.getColumnIndex("name"));
            byte[] faceJpg = res.getBlob(res.getColumnIndex("face"));
            byte[] templates = res.getBlob(res.getColumnIndex("templates"));
            Bitmap face = BitmapFactory.decodeByteArray(faceJpg, 0, faceJpg.length);

            Person person = new Person(employeeId, name, face, templates);
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
            String employeeId = res.getString(res.getColumnIndex("employeeId"));
            String name = res.getString(res.getColumnIndex("name"));
            long timestamp = res.getLong(res.getColumnIndex("timestamp"));
            String type = "CHECK_IN";
            try {
                int typeIndex = res.getColumnIndex("type");
                if (typeIndex >= 0) {
                    String typeValue = res.getString(typeIndex);
                    if (typeValue != null) {
                        type = typeValue;
                    }
                }
            } catch (Exception e) {
                // Column doesn't exist in old database
            }

            AttendanceLog attendanceLog = new AttendanceLog(employeeId, name, timestamp, type);
            attendanceList.add(attendanceLog);

            res.moveToNext();
        }
    }
}