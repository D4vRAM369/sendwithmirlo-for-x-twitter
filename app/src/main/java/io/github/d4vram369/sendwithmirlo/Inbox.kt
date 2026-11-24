package io.github.d4vram369.sendwithmirlo

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException

/**
 * Escribe `<ID>.mirlo` en la carpeta elegida, por el Storage Access Framework: así no hace falta
 * el permiso de "todos los archivos". Mirlo-desktop solo mira el nombre; el contenido es el ID
 * por si alguien abre el archivo a mano.
 */
object Inbox {
    enum class Result { WRITTEN, ALREADY_THERE }

    fun write(context: Context, tree: Uri, id: String): Result {
        require(id.isNotEmpty() && id.length <= 20 && id.all { it in '0'..'9' }) { "ID no válido" }
        val resolver = context.contentResolver
        val name = "$id.mirlo"
        if (find(resolver, tree, name) != null) return Result.ALREADY_THERE

        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        var doc = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
            ?: throw IOException("createDocument devolvió null")
        // Algunos proveedores añaden extensión según el MIME ("20.mirlo.bin"); Mirlo lo ignoraría.
        if (displayName(resolver, doc) != name) {
            doc = DocumentsContract.renameDocument(resolver, doc, name) ?: doc
        }
        resolver.openOutputStream(doc, "wt")?.use { it.write("$id\n".toByteArray()) }
            ?: throw IOException("no se pudo abrir el archivo")
        return Result.WRITTEN
    }

    private fun find(resolver: ContentResolver, tree: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        resolver.query(children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                }
            }
        return null
    }

    private fun displayName(resolver: ContentResolver, doc: Uri): String? =
        resolver.query(doc, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
