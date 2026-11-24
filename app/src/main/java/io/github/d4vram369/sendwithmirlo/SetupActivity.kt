package io.github.d4vram369.sendwithmirlo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Una pantalla: elegir la carpeta compartida con Syncthing. Vistas a mano y sin AndroidX para
 * que el APK sea mínimo; el mismo APK se carga dentro del proceso de X como módulo.
 */
class SetupActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(20)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        column.addView(TextView(this).apply {
            text = getString(R.string.setup_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        })
        column.addView(TextView(this).apply {
            text = getString(R.string.setup_help)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(12), 0, dp(12))
        })
        status = TextView(this).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) }
        column.addView(status)
        column.addView(Button(this).apply {
            text = getString(R.string.setup_pick)
            setOnClickListener { pick() }
        })
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true // targetSdk 35 dibuja de borde a borde
            addView(column)
        })
        refresh()
    }

    @Suppress("DEPRECATION") // Activity sin AndroidX: no hay registerForActivityResult
    private fun pick() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
        startActivityForResult(intent, REQUEST_TREE)
    }

    @Deprecated("Activity sin AndroidX: no hay registerForActivityResult")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQUEST_TREE || resultCode != RESULT_OK || uri == null) return
        // sin esto el permiso muere al cerrar la app
        contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        Prefs.setTree(this, uri)
        refresh()
        intent.getStringExtra(EXTRA_PENDING)?.let { id ->
            intent.removeExtra(EXTRA_PENDING)
            startActivity(Intent(this, SendActivity::class.java).putExtra(Intent.EXTRA_TEXT, PostId.url(id)))
        }
    }

    private fun refresh() {
        val tree: Uri? = Prefs.tree(this)
        status.text = if (tree == null) getString(R.string.setup_no_folder)
        else getString(R.string.setup_folder, DocumentsContract.getTreeDocumentId(tree))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PENDING = "io.github.d4vram369.sendwithmirlo.PENDING_ID"
        private const val REQUEST_TREE = 1
    }
}
