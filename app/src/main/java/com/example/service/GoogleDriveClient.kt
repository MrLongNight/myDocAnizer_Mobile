package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Nativer Client für die Google Drive REST API v3.
 * Überträgt verschlüsselte Dokument-Archive (.enc) und Disaster-Recovery-Indizes
 * direkt an den Google Drive Cloud-Speicher.
 */
object GoogleDriveClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Prüft die Gültigkeit eines Google OAuth-Tokens und ruft Account-Informationen ab.
     */
    suspend fun checkAccountInfo(accessToken: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (accessToken.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Kein Google OAuth-Token hinterlegt."))
            }

            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=user,storageQuota")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    val user = json.optJSONObject("user")
                    val email = user?.optString("emailAddress") ?: "Google-Konto"
                    val displayName = user?.optString("displayName") ?: ""
                    Result.success("Verbunden mit $displayName ($email)")
                } else {
                    Result.failure(IOException("Google Drive API Fehler (HTTP ${resp.code}): ${resp.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val folderCache = java.util.concurrent.ConcurrentHashMap<Pair<String, Int>, String>()

    /**
     * Sucht nach einem bestehenden Ordner oder erstellt ihn, falls noch nicht vorhanden.
     */
    suspend fun findOrCreateFolder(accessToken: String, folderName: String): String? = withContext(Dispatchers.IO) {
        if (folderName.isBlank()) return@withContext null
        val cacheKey = Pair(folderName, accessToken.hashCode())
        folderCache[cacheKey]?.let { return@withContext it }

        try {
            val q = "mimeType = 'application/vnd.google-apps.folder' and name = '${folderName.replace("'", "\\'")}' and trashed = false"
            val encodedQ = java.net.URLEncoder.encode(q, "UTF-8")
            val searchRequest = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=$encodedQ&fields=files(id,name)")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(searchRequest).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        val folderId = files.getJSONObject(0).optString("id")
                        if (folderId.isNotBlank()) {
                            folderCache[cacheKey] = folderId
                            return@withContext folderId
                        }
                    }
                }
            }

            // Ordner neu anlegen
            val folderMetadata = JSONObject().apply {
                put("name", folderName)
                put("mimeType", "application/vnd.google-apps.folder")
            }.toString()

            val createRequest = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files")
                .header("Authorization", "Bearer $accessToken")
                .post(folderMetadata.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                .build()

            httpClient.newCall(createRequest).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    val id = json.optString("id", "")
                    if (id.isNotBlank()) {
                        folderCache[cacheKey] = id
                        return@withContext id
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }

    /**
     * Prüft, ob bereits eine Datei mit `remoteName` (z.B. doc_index.enc) existiert.
     */
    suspend fun findExistingFileId(accessToken: String, remoteName: String, parentFolderId: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val qBuilder = StringBuilder("name = '${remoteName.replace("'", "\\'")}' and trashed = false")
            if (!parentFolderId.isNullOrBlank()) {
                qBuilder.append(" and '${parentFolderId}' in parents")
            }
            val encodedQ = java.net.URLEncoder.encode(qBuilder.toString(), "UTF-8")
            val searchRequest = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=$encodedQ&fields=files(id,name)&orderBy=modifiedTime desc")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(searchRequest).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        return@withContext files.getJSONObject(0).optString("id")
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }

    /**
     * Lädt eine verschlüsselte Datei (.enc) per Google Drive v3 Multipart-Upload hoch.
     * Nutzt PATCH, wenn die Datei bereits existiert (um Duplikate zu vermeiden),
     * andernfalls POST mit optionaler Ordner-Zuweisung.
     */
    suspend fun uploadEncryptedFile(
        accessToken: String,
        file: File,
        remoteName: String = file.name,
        folderName: String = "myDocAnizer_Vault"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (accessToken.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Google Drive Synchronisation erfordert ein gültiges OAuth-Token."))
            }
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Lokale Datei existiert nicht: ${file.name}"))
            }

            val parentFolderId = if (folderName.isNotBlank()) findOrCreateFolder(accessToken, folderName) else null
            val existingFileId = findExistingFileId(accessToken, remoteName, parentFolderId)

            // Metadaten JSON
            val metadataJson = JSONObject().apply {
                put("name", remoteName)
                put("description", "myDocAnizer Zero-Knowledge Encrypted Backup")
                if (parentFolderId != null && existingFileId == null) {
                    put("parents", org.json.JSONArray().put(parentFolderId))
                }
            }.toString()

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "metadata",
                    "metadata.json",
                    metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull())
                )
                .addFormDataPart(
                    "file",
                    remoteName,
                    file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                )
                .build()

            val request = if (existingFileId != null) {
                // Bestehende Datei per PATCH aktualisieren
                Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=multipart")
                    .header("Authorization", "Bearer $accessToken")
                    .patch(multipartBody)
                    .build()
            } else {
                // Neue Datei per POST anlegen
                Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                    .header("Authorization", "Bearer $accessToken")
                    .post(multipartBody)
                    .build()
            }

            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val respJson = JSONObject(resp.body?.string() ?: "{}")
                    val fileId = respJson.optString("id", existingFileId ?: "")
                    Result.success(fileId)
                } else {
                    Result.failure(IOException("Google Drive Upload fehlgeschlagen: HTTP ${resp.code} ${resp.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ruft die Liste der Änderungen seit dem letzten Sync-Token ab (Incremental Delta-Sync).
     */
    suspend fun fetchChanges(accessToken: String, pageToken: String): Result<Pair<String, List<JSONObject>>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.googleapis.com/drive/v3/changes?pageToken=$pageToken&fields=nextPageToken,newStartPageToken,changes(fileId,removed,file(name,mimeType,modifiedTime,md5Checksum))"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    val changes = mutableListOf<JSONObject>()
                    val changesArr = json.optJSONArray("changes")
                    if (changesArr != null) {
                        for (i in 0 until changesArr.length()) {
                            changes.add(changesArr.getJSONObject(i))
                        }
                    }
                    val nextToken = json.optString("nextPageToken", json.optString("newStartPageToken", ""))
                    Result.success(Pair(nextToken, changes))
                } else {
                    Result.failure(IOException("Google Drive Changes API Fehler: HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ruft ein initiales Start-Token für die Changes-API ab.
     */
    suspend fun getStartPageToken(accessToken: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/changes/startPageToken")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    Result.success(json.optString("startPageToken", ""))
                } else {
                    Result.failure(IOException("Google Drive StartToken Fehler: HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
