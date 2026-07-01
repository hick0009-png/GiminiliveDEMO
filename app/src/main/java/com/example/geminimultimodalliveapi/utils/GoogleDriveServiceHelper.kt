package com.example.geminimultimodalliveapi.utils

import android.util.Log
import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Collections
import com.example.geminimultimodalliveapi.error.AppError
import com.example.geminimultimodalliveapi.session.SessionStateHolder

class GoogleDriveServiceHelper(private val driveService: Drive) {

    /**
     * Finds or creates a folder on Google Drive.
     * Returns the folder ID, or null on failure.
     */
    suspend fun createOrGetFolder(folderName: String): String? = withContext(Dispatchers.IO) {
        try {
            // Search for existing folder with the folderName
            val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false"
            val resultList = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val files = resultList.files
            if (!files.isNullOrEmpty()) {
                Log.i("GDriveHelper", "Found existing folder: $folderName with ID: ${files[0].id}")
                return@withContext files[0].id
            }

            // Folder does not exist, create it
            Log.i("GDriveHelper", "Folder $folderName not found. Creating it...")
            val folderMetadata = File().apply {
                name = folderName
                mimeType = "application/vnd.google-apps.folder"
            }

            val createdFolder = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute()

            Log.i("GDriveHelper", "Created new folder with ID: ${createdFolder.id}")
            return@withContext createdFolder.id
        } catch (e: Exception) {
            Log.e("GDriveHelper", "Failed to create or get folder", e)
            SessionStateHolder.postError(AppError.fromThrowable(e))
            return@withContext null
        }
    }

    /**
     * Uploads or updates a text file inside a specified Google Drive folder.
     * Returns the file ID on success, or null on failure.
     */
    suspend fun uploadOrUpdateFile(
        localFile: java.io.File,
        mimeType: String,
        folderId: String?
    ): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = localFile.name
            
            // Search if file already exists in the target folder
            var query = "name = '$fileName' and trashed = false"
            if (folderId != null) {
                query += " and '$folderId' in parents"
            }
            
            val resultList = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val files = resultList.files
            val mediaContent = FileContent(mimeType, localFile)

            if (!files.isNullOrEmpty()) {
                // File exists, update it
                val existingFileId = files[0].id
                Log.i("GDriveHelper", "File $fileName already exists (ID: $existingFileId). Updating content...")
                
                val fileMetadata = File() // Empty metadata since we only update content
                val updatedFile = driveService.files().update(existingFileId, fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
                
                Log.i("GDriveHelper", "File updated successfully (ID: ${updatedFile.id})")
                return@withContext updatedFile.id
            } else {
                // File does not exist, create (upload) it
                Log.i("GDriveHelper", "File $fileName not found. Uploading new file...")
                
                val fileMetadata = File().apply {
                    name = fileName
                    if (folderId != null) {
                        parents = Collections.singletonList(folderId)
                    }
                }

                val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()

                Log.i("GDriveHelper", "File uploaded successfully (ID: ${uploadedFile.id})")
                return@withContext uploadedFile.id
            }
        } catch (e: Exception) {
            Log.e("GDriveHelper", "Failed to upload or update file", e)
            SessionStateHolder.postError(AppError.fromThrowable(e))
            return@withContext null
        }
    }

    /**
     * Deletes a file by name from a folder in Google Drive.
     */
    suspend fun deleteFileByName(fileName: String, folderId: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            var query = "name = '$fileName' and trashed = false"
            if (folderId != null) {
                query += " and '$folderId' in parents"
            }
            val resultList = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val files = resultList.files
            if (!files.isNullOrEmpty()) {
                for (file in files) {
                    driveService.files().delete(file.id).execute()
                    Log.i("GDriveHelper", "Deleted file from Google Drive: ${file.name} (ID: ${file.id})")
                }
                true
            } else {
                Log.w("GDriveHelper", "File $fileName not found on Google Drive for deletion.")
                false
            }
        } catch (e: Exception) {
            Log.e("GDriveHelper", "Failed to delete file from Google Drive", e)
            SessionStateHolder.postError(AppError.fromThrowable(e))
            false
        }
    }
}
