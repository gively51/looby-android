package com.viple.looby.core.network

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.viple.looby.core.model.SectionTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Upload direct vers Azure Blob via ticket SAS (voir MOBILE_API.md §6). */
@Singleton
class MediaUploader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogApi: CatalogApi,
    @Named("plain") private val plainClient: OkHttpClient
) {
    suspend fun upload(uri: Uri, section: SectionTheme = SectionTheme.Looby): Result<String> = withContext(Dispatchers.IO) {
        apiCall {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "image/jpeg"
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
            val fileName = "upload_${System.currentTimeMillis()}.$ext"
            val ticket = catalogApi.uploadTicket(section.value, fileName)

            // Photos taken with the phone's camera are commonly 8-20 MB. Reading the
            // whole file into a ByteArray (resolver.openInputStream(uri).readBytes())
            // can throw an OutOfMemoryError on lower-end devices and silently aborts
            // the upload without any photo ever reaching S3. Stream the content://
            // Uri directly to the presigned PUT instead, with a known Content-Length
            // (required for the S3 signature to match), so large camera photos no
            // longer fail to import.
            val length = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (length <= 0L) error("Impossible de lire le fichier.")
            val mediaType = ticket.contentType.toMediaType()
            val requestBody = object : RequestBody() {
                override fun contentType() = mediaType
                override fun contentLength() = length
                override fun writeTo(sink: BufferedSink) {
                    val input = resolver.openInputStream(uri) ?: error("Impossible de lire le fichier.")
                    input.use { sink.writeAll(it.source()) }
                }
            }

            // The upload URL points at an AWS S3 pre-signed PUT (see
            // S3MediaStorageService). "x-ms-blob-type" is an Azure Blob Storage-only
            // header left over from before the migration to S3; S3 doesn't expect it
            // and some bucket configurations reject unsigned headers with a
            // SignatureDoesNotMatch error, silently failing the upload (the listing
            // then publishes without its photo).
            val request = Request.Builder()
                .url(ticket.uploadUrl)
                .put(requestBody)
                .header("Content-Type", ticket.contentType)
                .build()
            plainClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApiError.Server(response.code, "Échec de l'envoi du média (${response.code}).")
            }
            ticket.blobUrl
        }
    }
}
