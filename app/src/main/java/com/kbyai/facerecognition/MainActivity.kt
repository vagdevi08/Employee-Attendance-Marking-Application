package com.kbyai.facerecognition

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

class MainActivity : AppCompatActivity() {

    companion object {
        private val SELECT_PHOTO_REQUEST_CODE = 1
    }

    private lateinit var dbManager: DBManager
    private lateinit var textWarning: TextView
    private lateinit var personAdapter: PersonAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textWarning = findViewById<TextView>(R.id.textWarning)
        
        // No license activation needed for Google ML Kit - it's free!
        // Just proceed with initialization
        initializeApp()
    }

    private fun initializeApp() {
        textWarning.visibility = View.GONE
        
        // Configure Python service URL
        val pythonServiceUrl = SettingsActivity.getPythonServiceUrl(this)
        PythonFaceService.setBaseUrl(pythonServiceUrl)
        
        dbManager = DBManager(this)
        dbManager.loadPerson()

        personAdapter = PersonAdapter(this, DBManager.personList)
        val listView: ListView = findViewById<View>(R.id.listPerson) as ListView
        listView.adapter = personAdapter

        findViewById<Button>(R.id.buttonEnroll).setOnClickListener {
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_PICK
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_picture)), SELECT_PHOTO_REQUEST_CODE)
        }

        findViewById<Button>(R.id.buttonIdentify).setOnClickListener {
            startActivity(Intent(this, CameraActivityKt::class.java))
        }

        findViewById<Button>(R.id.buttonSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.buttonAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        findViewById<Button>(R.id.buttonAttendance).setOnClickListener {
            startActivity(Intent(this, AttendanceActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        personAdapter.notifyDataSetChanged()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == SELECT_PHOTO_REQUEST_CODE && resultCode == RESULT_OK) {
            try {
                var bitmap: Bitmap = Utils.getCorrectlyOrientedImage(this, data?.data!!)

                // Show enrollment dialog - Python service will validate face
                showEnrollmentDialog(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEnrollmentDialog(bitmap: Bitmap) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_enroll_employee, null)
        val editEmployeeId = dialogView.findViewById<EditText>(R.id.editEmployeeId)
        val editEmployeeName = dialogView.findViewById<EditText>(R.id.editEmployeeName)
        val buttonCancel = dialogView.findViewById<Button>(R.id.buttonCancel)
        val buttonEnroll = dialogView.findViewById<Button>(R.id.buttonEnroll)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        buttonEnroll.setOnClickListener {
            val employeeId = editEmployeeId.text.toString().trim()
            val employeeName = editEmployeeName.text.toString().trim()

            if (employeeId.isEmpty()) {
                Toast.makeText(this, "Please enter Employee ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (employeeName.isEmpty()) {
                Toast.makeText(this, "Please enter Employee Name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if employee ID already exists
            val existingPerson = DBManager.personList.find { it.employeeId == employeeId }
            if (existingPerson != null) {
                Toast.makeText(this, "Employee ID already exists!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show progress
            buttonEnroll.isEnabled = false
            buttonEnroll.text = "Enrolling..."

            // Enroll using Python service for better face recognition
            PythonFaceService.enrollFace(employeeId, employeeName, bitmap) { success, message ->
                runOnUiThread {
                    if (success) {
                        // Also save to local database for offline access
                        // Create empty embeddings as they're stored in Python service
                        dbManager.insertPerson(employeeId, employeeName, bitmap, ByteArray(0))
                        personAdapter.notifyDataSetChanged()
                        Toast.makeText(this, "Employee enrolled successfully!", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(this, "Enrollment failed: $message", Toast.LENGTH_LONG).show()
                        buttonEnroll.isEnabled = true
                        buttonEnroll.text = "Enroll"
                    }
                }
            }
        }

        dialog.show()
    }

    private fun floatArrayToByteArray(floatArray: FloatArray): ByteArray {
        val byteArray = ByteArray(floatArray.size * 4)
        for (i in floatArray.indices) {
            val intBits = floatArray[i].toBits()
            byteArray[i * 4] = (intBits shr 24).toByte()
            byteArray[i * 4 + 1] = (intBits shr 16).toByte()
            byteArray[i * 4 + 2] = (intBits shr 8).toByte()
            byteArray[i * 4 + 3] = intBits.toByte()
        }
        return byteArray
    }
}