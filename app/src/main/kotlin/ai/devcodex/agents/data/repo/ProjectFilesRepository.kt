package ai.devcodex.agents.data.repo

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class ProjectFile(
    val name: String,
    val uriString: String,
    val isDirectory: Boolean,
    val path: String
)

data class AttachedFile(
    val name: String,
    val path: String,
    val content: String,
    val truncated: Boolean = false,
    val error: String? = null
)

class ProjectFilesRepository(private val context: Context) {

    private val settings by lazy { SettingsRepository(context) }

    fun persistTree(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        settings.saveTreeUri(uri.toString())
    }

    fun getTreeUri(): Uri? = settings.loadTreeUri()?.let(Uri::parse)

    suspend fun listChildren(dirUriString: String?, dirPath: String): List<ProjectFile> = withContext(Dispatchers.IO) {
        val baseUri = if (dirUriString == null) getTreeUri() else Uri.parse(dirUriString)
        if (baseUri == null) return@withContext emptyList()
        val result = mutableListOf<ProjectFile>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            baseUri, DocumentsContract.getTreeDocumentId(baseUri)
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: "未命名"
                val mime = c.getString(2)
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val docUri = DocumentsContract.buildDocumentUriUsingTree(baseUri, id)
                val uriString = if (isDir) {
                    DocumentsContract.buildChildDocumentsUriUsingTree(baseUri, id).toString()
                } else {
                    docUri.toString()
                }
                result.add(
                    ProjectFile(
                        name = name,
                        uriString = uriString,
                        isDirectory = isDir,
                        path = if (dirPath.isEmpty()) name else "$dirPath/$name"
                    )
                )
            }
        }
        result.sortedWith(
            compareByDescending<ProjectFile> { it.isDirectory }.thenBy { it.name.lowercase() }
        )
    }

    suspend fun readFile(uriString: String): AttachedFile = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: uriString.substringAfterLast('/')
        val resolver = context.contentResolver
        val stream = resolver.openInputStream(uri) ?: throw IOException("无法打开文件")
        val maxBytes = 60 * 1024
        val (content, truncated) = stream.use { s ->
            val byteArray = s.readBytes()
            if (byteArray.size > maxBytes) {
                String(byteArray, 0, maxBytes) to true
            } else {
                String(byteArray) to false
            }
        }
        if (content.isBlank()) throw IOException("文件内容为空")
        AttachedFile(name = name, path = name, content = content, truncated = truncated)
    }

    private fun String.substringAfterLast(delimiter: Char): String {
        val index = lastIndexOf(delimiter)
        return if (index == -1) this else substring(index + 1)
    }
}