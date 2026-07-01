package com.example.geminimultimodalliveapi.document

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import android.content.Intent
import com.example.geminimultimodalliveapi.R
import com.example.geminimultimodalliveapi.utils.DocumentParser
import com.example.geminimultimodalliveapi.utils.GoogleDriveServiceHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import android.graphics.drawable.ColorDrawable
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.*
import java.io.File

class DocumentManager(
    private val activity: FragmentActivity,
    private val parentView: View,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun onGoogleSignedOut()
        fun launchGoogleSignIn(intent: Intent)
        fun launchDocumentPicker(mimeTypes: Array<String>)
    }
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // UI elements
    private val documentCard: View = parentView.findViewById(R.id.documentCard)
    private val uploadDocButton: View = parentView.findViewById(R.id.uploadDocButton)
    private val documentListContainer: LinearLayout = parentView.findViewById(R.id.documentListContainer)
    private val uploadProgressLayout: LinearLayout = parentView.findViewById(R.id.uploadProgressLayout)
    private val uploadStatusText: TextView = parentView.findViewById(R.id.uploadStatusText)
    private val connectDriveButton: Button = parentView.findViewById(R.id.connectDriveButton)
    private val driveStatusText: TextView = parentView.findViewById(R.id.driveStatusText)

    // Google Sign-In & Drive components
    private var googleSignInClient: GoogleSignInClient
    private var driveServiceHelper: GoogleDriveServiceHelper? = null

    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(DriveScopes.DRIVE_FILE),
                Scope("https://www.googleapis.com/auth/calendar"),
                Scope("https://www.googleapis.com/auth/calendar.events")
            )
            .build()
        googleSignInClient = GoogleSignIn.getClient(activity, gso)

        connectDriveButton.setOnClickListener {
            toggleDriveConnection()
        }

        uploadDocButton.setOnClickListener {
            openFilePicker()
        }

        refreshDocumentList()

        // Initial sign-in check to set correct visibility
        val lastAccount = GoogleSignIn.getLastSignedInAccount(activity)
        if (lastAccount != null) {
            onSignedIn(lastAccount)
        } else {
            onSignedOut()
        }
    }

    fun onSignedIn(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            activity,
            listOf(DriveScopes.DRIVE_FILE, "https://www.googleapis.com/auth/calendar", "https://www.googleapis.com/auth/calendar.events")
        ).apply {
            selectedAccount = account.account
        }
        val driveService = com.google.api.services.drive.Drive.Builder(
            com.google.api.client.http.javanet.NetHttpTransport(),
            com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
            credential
        )
        .setApplicationName("Gemini Live Demo")
        .build()
        
        driveServiceHelper = GoogleDriveServiceHelper(driveService)
        updateDriveUI(account.email)
        documentCard.visibility = View.VISIBLE
    }

    fun onSignedOut() {
        driveServiceHelper = null
        updateDriveUI(null)
        documentCard.visibility = View.GONE
    }

    fun onDestroy() {
        managerScope.cancel()
    }

    private fun updateDriveUI(email: String?) {
        activity.runOnUiThread {
            if (email != null) {
                driveStatusText.text = "เชื่อมต่อแล้ว: $email"
                driveStatusText.setTextColor(Color.parseColor("#4CAF50"))
                connectDriveButton.text = "Disconnect"
            } else {
                driveStatusText.text = "ยังไม่ได้เชื่อมต่อ Google Drive"
                driveStatusText.setTextColor(Color.parseColor("#FF5252"))
                connectDriveButton.text = "Connect Google Drive"
            }
        }
    }

    private fun toggleDriveConnection() {
        val account = GoogleSignIn.getLastSignedInAccount(activity)
        if (account != null) {
            googleSignInClient.signOut().addOnCompleteListener(activity) {
                onSignedOut()
                callbacks.onGoogleSignedOut() // Tell main activity to signed out calendar too
                Toast.makeText(activity, "เลิกเชื่อมต่อบัญชี Google สำเร็จ", Toast.LENGTH_SHORT).show()
            }
        } else {
            callbacks.launchGoogleSignIn(googleSignInClient.signInIntent)
        }
    }

    private fun openFilePicker() {
        callbacks.launchDocumentPicker(
            arrayOf(
                "text/plain",
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        )
    }

    fun handleSelectedDocument(uri: android.net.Uri) {
        uploadProgressLayout.visibility = View.VISIBLE
        uploadDocButton.isEnabled = false
        uploadStatusText.text = "กำลังอ่านและวิเคราะห์ไฟล์... โปรดรอสักครู่"
        
        managerScope.launch {
            val destFile = withContext(Dispatchers.IO) {
                try {
                    val contentResolver = activity.contentResolver
                    var tempName = "doc_${System.currentTimeMillis()}"
                    
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            tempName = cursor.getString(nameIndex)
                        }
                    }
                    
                    val baseName = tempName.substringBeforeLast(".")
                    
                    val mimeType = contentResolver.getType(uri)
                    val text = DocumentParser.extractText(activity, uri, mimeType)
                    if (text.isNullOrBlank()) {
                        Log.e("DocumentManager", "Extracted text is empty")
                        return@withContext null
                    }
                    
                    val cleanedText = DocumentParser.cleanText(text)
                    val dir = File(activity.filesDir, "documents")
                    if (!dir.exists()) dir.mkdirs()
                    
                    val saveFile = File(dir, "${baseName}.txt")
                    saveFile.writeText(cleanedText)
                    
                    // Trigger dynamic sync to Google Drive
                    driveServiceHelper?.let { driveHelper ->
                        Log.i("GDriveSync", "Starting background Drive sync for: ${saveFile.name}")
                        try {
                            val folderId = driveHelper.createOrGetFolder("GeminiLiveDemo_Docs")
                            val fileId = driveHelper.uploadOrUpdateFile(saveFile, "text/plain", folderId)
                            if (fileId != null) {
                                Log.i("GDriveSync", "Successfully synced file to Drive: $fileId")
                            } else {
                                Log.w("GDriveSync", "Failed to sync file to Google Drive.")
                            }
                        } catch (e: Exception) {
                            Log.e("GDriveSync", "Failed to sync file to Drive", e)
                        }
                    }
                    
                    saveFile
                } catch (e: Exception) {
                    Log.e("DocumentManager", "Failed to process selected file", e)
                    null
                }
            }

            uploadProgressLayout.visibility = View.GONE
            uploadDocButton.isEnabled = true
            
            if (destFile != null) {
                Toast.makeText(activity, "อัปโหลดและประมวลผลไฟล์สำเร็จ!", Toast.LENGTH_SHORT).show()
                refreshDocumentList()
            } else {
                Toast.makeText(activity, "ไม่สามารถอ่านไฟล์ได้ หรือเนื้อหาไฟล์ว่างเปล่า", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshDocumentList() {
        documentListContainer.removeAllViews()
        val dir = File(activity.filesDir, "documents")
        if (!dir.exists() || !dir.isDirectory) return

        val files = dir.listFiles() ?: return
        if (files.isEmpty()) {
            val emptyTv = TextView(activity).apply {
                text = "ยังไม่มีเอกสารที่อัปโหลด"
                setTextColor(Color.parseColor("#80FFFFFF"))
                textSize = spToPx(12).toFloat()
                setPadding(0, 8, 0, 8)
            }
            documentListContainer.addView(emptyTv)
            return
        }

        for (file in files) {
            val itemLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 6, 0, 6)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val fileTv = TextView(activity).apply {
                text = file.name
                setTextColor(Color.parseColor("#00BCD4")) // cyan clickable color
                textSize = 13f
                paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG // underline
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    showDocumentPreviewDialog(file)
                }
            }

            val deleteBtn = Button(activity).apply {
                text = "ลบ"
                setTextColor(Color.WHITE)
                textSize = 10f
                background = activity.getDrawable(android.R.drawable.btn_default)
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5252")) // red
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(32)
                )
                setOnClickListener {
                    AlertDialog.Builder(activity)
                        .setTitle("ยืนยันการลบ")
                        .setMessage("คุณต้องการลบไฟล์เอกสาร '${file.name}' ใช่หรือไม่?")
                        .setPositiveButton("ลบ") { _, _ ->
                            val fileName = file.name
                            file.delete()
                            refreshDocumentList()
                            Toast.makeText(activity, "กำลังลบไฟล์...", Toast.LENGTH_SHORT).show()
                            
                            // Delete from Google Drive if helper is available
                            driveServiceHelper?.let { helper ->
                                managerScope.launch {
                                    val folderId = helper.createOrGetFolder("GeminiLiveDemo_Docs")
                                    val deleted = helper.deleteFileByName(fileName, folderId)
                                    if (deleted) {
                                        Log.i("DocumentManager", "Drive file deleted sync: $fileName")
                                    } else {
                                        Log.w("DocumentManager", "Drive file delete failed sync or not found: $fileName")
                                    }
                                }
                            }
                            Toast.makeText(activity, "ลบเอกสารสำเร็จ!", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("ยกเลิก", null)
                        .show()
                }
            }

            itemLayout.addView(fileTv)
            itemLayout.addView(deleteBtn)
            documentListContainer.addView(itemLayout)
        }
    }

    private fun showDocumentPreviewDialog(file: File) {
        try {
            val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_document_preview, null)
            val titleTv = dialogView.findViewById<TextView>(R.id.previewTitle)
            val contentTv = dialogView.findViewById<TextView>(R.id.previewContent)
            val btnClose = dialogView.findViewById<Button>(R.id.closePreviewButton)
            val btnPrevPage = dialogView.findViewById<Button>(R.id.btnPrevPage)
            val btnNextPage = dialogView.findViewById<Button>(R.id.btnNextPage)
            val pageIndicatorText = dialogView.findViewById<TextView>(R.id.pageIndicatorText)

            titleTv.text = file.name.replace(".txt", "")
            
            val content = file.readText()
            val pageSize = 1000
            val totalLength = content.length
            val totalPages = if (totalLength == 0) 1 else ((totalLength - 1) / pageSize) + 1
            var currentPage = 1

            fun updatePageDisplay() {
                val startIdx = (currentPage - 1) * pageSize
                val endIdx = (startIdx + pageSize).coerceAtMost(totalLength)
                val pageText = if (totalLength == 0) "" else content.substring(startIdx, endIdx)
                contentTv.text = pageText
                pageIndicatorText.text = "หน้า $currentPage/$totalPages"
                btnPrevPage.isEnabled = currentPage > 1
                btnNextPage.isEnabled = currentPage < totalPages
            }

            updatePageDisplay()

            btnPrevPage.setOnClickListener {
                if (currentPage > 1) {
                    currentPage--
                    updatePageDisplay()
                }
            }

            btnNextPage.setOnClickListener {
                if (currentPage < totalPages) {
                    currentPage++
                    updatePageDisplay()
                }
            }

            val dialog = AlertDialog.Builder(activity)
                .setView(dialogView)
                .create()

            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            btnClose.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        } catch (e: Exception) {
            Log.e("DocPreview", "Failed to show document preview", e)
            Toast.makeText(activity, "ไม่สามารถเปิดดูตัวอย่างไฟล์ได้", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = activity.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun spToPx(sp: Int): Int {
        return (sp * activity.resources.displayMetrics.scaledDensity).toInt()
    }
}
