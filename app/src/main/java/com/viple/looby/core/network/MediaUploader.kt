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
import okhttp3.RequestBody.Companion.toRequestBody
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

            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Impossible de lire le fichier.")
            val request = Request.Builder()
                .url(ticket.uploadUrl)
                .put(bytes.toRequestBody(ticket.contentType.toMediaType()))
                .header("x-ms-blob-type", "BlockBlob")
                .header("Content-Type", ticket.contentType)
                .build()
            plainClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApiError.Server(response.code, "Échec de l'envoi du média (${response.code}).")
            }
            ticket.blobUrl
        }
    }
}
