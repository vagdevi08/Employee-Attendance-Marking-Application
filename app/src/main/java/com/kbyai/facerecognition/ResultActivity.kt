//package com.kbyai.facerecognition
//
//import android.graphics.Bitmap
//import androidx.appcompat.app.AppCompatActivity
//import android.os.Bundle
//import android.os.Handler
//import android.os.Looper
//import android.widget.ImageView
//import android.widget.TextView
//import android.widget.Toast
//import java.text.SimpleDateFormat
//import java.util.*
//
//class ResultActivity : AppCompatActivity() {
//
//    private lateinit var dbManager: DBManager
//    private val AUTO_RETURN_DELAY_MS = 3000L // 3 seconds delay before auto-return
//    private var handler: Handler? = null
//    private var autoReturnRunnable: Runnable? = null
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_result)
//
//        dbManager = DBManager(this)
//
//        val identifyedFace = intent.getParcelableExtra("identified_face") as? Bitmap
//        val enrolledFace = intent.getParcelableExtra("enrolled_face") as? Bitmap
//        val identifiedName = intent.getStringExtra("identified_name")
//        val similarity = intent.getFloatExtra("similarity", 0f)
//        val livenessScore = intent.getFloatExtra("liveness", 0f)
//        val yaw = intent.getFloatExtra("yaw", 0f)
//        val roll = intent.getFloatExtra("roll", 0f)
//        val pitch = intent.getFloatExtra("pitch", 0f)
//
//        findViewById<ImageView>(R.id.imageEnrolled).setImageBitmap(enrolledFace)
//        findViewById<ImageView>(R.id.imageIdentified).setImageBitmap(identifyedFace)
//        findViewById<TextView>(R.id.textPerson).text = "Identified: " + identifiedName
//        findViewById<TextView>(R.id.textSimilarity).text = "Similarity: " + similarity
//        val attendanceTime = System.currentTimeMillis()
//        if (!identifiedName.isNullOrEmpty()) {
//            dbManager.insertAttendance(identifiedName, attendanceTime)
//            val formatter = SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault())
//            findViewById<TextView>(R.id.textAttendanceTime).text =
//                "Attendance marked: ${formatter.format(Date(attendanceTime))}"
//            Toast.makeText(
//                this,
//                getString(R.string.attendance_marked, identifiedName),
//                Toast.LENGTH_LONG
//            ).show()
//        }
//        findViewById<TextView>(R.id.textLiveness).text = "Liveness score: " + livenessScore
//        findViewById<TextView>(R.id.textYaw).text = "Yaw: " + yaw
//        findViewById<TextView>(R.id.textRoll).text = "Roll: " + roll
//        findViewById<TextView>(R.id.textPitch).text = "Pitch: " + pitch
//
//        // Automatically return to previous activity after delay
//        handler = Handler(Looper.getMainLooper())
//        autoReturnRunnable = Runnable {
//            finish()
//        }
//        handler?.postDelayed(autoReturnRunnable!!, AUTO_RETURN_DELAY_MS)
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        // Clean up handler to prevent memory leaks
//        autoReturnRunnable?.let { handler?.removeCallbacks(it) }
//    }
//
//    override fun onBackPressed() {
//        super.onBackPressed()
//        // Cancel auto-return if user manually goes back
//        autoReturnRunnable?.let { handler?.removeCallbacks(it) }
//    }
//}

package com.kbyai.facerecognition

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*
import java.time.Instant
import java.time.ZoneOffset

class ResultActivity : AppCompatActivity() {

    private lateinit var dbManager: DBManager
    private val AUTO_RETURN_DELAY_MS = 3000L
    private var handler: Handler? = null
    private var autoReturnRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        dbManager = DBManager(this)

        // ----- INTENT DATA -----
        val identifiedFace =
            intent.getParcelableExtra<Bitmap>("identified_face")

        val enrolledFace =
            intent.getParcelableExtra<Bitmap>("enrolled_face")

        val identifiedName =
            intent.getStringExtra("identified_name")

        val employeeId =
            intent.getStringExtra("employee_id") ?: ""

        val similarity =
            intent.getFloatExtra("similarity", 0f)

        val livenessScore =
            intent.getFloatExtra("liveness", 0f)

        val yaw =
            intent.getFloatExtra("yaw", 0f)

        val roll =
            intent.getFloatExtra("roll", 0f)

        val pitch =
            intent.getFloatExtra("pitch", 0f)

        // ----- UI -----
        findViewById<ImageView>(R.id.imageEnrolled)
            .setImageBitmap(enrolledFace)

        findViewById<ImageView>(R.id.imageIdentified)
            .setImageBitmap(identifiedFace)

        findViewById<TextView>(R.id.textPerson).text =
            "Identified: $identifiedName"

        findViewById<TextView>(R.id.textSimilarity).text =
            "Similarity: %.2f".format(similarity)

        // ----- ATTENDANCE -----
        val attendanceTime = System.currentTimeMillis()

        if (!identifiedName.isNullOrEmpty() && !employeeId.isEmpty()) {
            val attendanceType = AttendanceHelper.determineAttendanceType(this, employeeId, attendanceTime)
            
            if (attendanceType != "NONE") {
                dbManager.insertAttendance(
                    employeeId,
                    identifiedName,
                    attendanceTime,
                    attendanceType
                )

                val formatter =
                    SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault())
                
                val typeText = if (attendanceType == "CHECK_IN") "Check-in" else "Check-out"

                findViewById<TextView>(R.id.textAttendanceTime).text =
                    "$typeText marked: ${formatter.format(Date(attendanceTime))}"

                Toast.makeText(
                    this,
                    "$typeText successful for $identifiedName",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                val message = AttendanceHelper.getAttendanceTypeMessage(this, employeeId, attendanceTime)
                findViewById<TextView>(R.id.textAttendanceTime).text = message
                Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (!identifiedName.isNullOrEmpty()) {
            // Fallback for employees without ID
            val attendanceType = "CHECK_IN"
            dbManager.insertAttendance(
                "",
                identifiedName,
                attendanceTime,
                attendanceType
            )

            val formatter =
                SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault())

            findViewById<TextView>(R.id.textAttendanceTime).text =
                "Attendance marked: ${formatter.format(Date(attendanceTime))}"

            Toast.makeText(
                this,
                getString(R.string.attendance_marked, identifiedName),
                Toast.LENGTH_LONG
            ).show()
        }

        // ----- EXTRA INFO -----
        findViewById<TextView>(R.id.textLiveness).text =
            "Liveness score: %.2f".format(livenessScore)

        findViewById<TextView>(R.id.textYaw).text = "Yaw: $yaw"
        findViewById<TextView>(R.id.textRoll).text = "Roll: $roll"
        findViewById<TextView>(R.id.textPitch).text = "Pitch: $pitch"

        // ----- AUTO RETURN -----
        handler = Handler(Looper.getMainLooper())
        autoReturnRunnable = Runnable { finish() }
        handler?.postDelayed(autoReturnRunnable!!, AUTO_RETURN_DELAY_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        autoReturnRunnable?.let { handler?.removeCallbacks(it) }
    }

    override fun onBackPressed() {
        autoReturnRunnable?.let { handler?.removeCallbacks(it) }
        super.onBackPressed()
    }
}
