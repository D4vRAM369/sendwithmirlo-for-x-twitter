package io.github.d4vram369.sendwithmirlo

import android.Manifest
import android.app.Activity
import android.app.job.JobScheduler
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.util.TypedValue
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Una pantalla: elegir la carpeta compartida con Syncthing y ajustar los recordatorios (RF-05). Vistas a mano y sin AndroidX para
 * que el APK sea mínimo; el mismo APK se carga dentro del proceso de X como módulo.
 */
class SetupActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && !Prefs.onboarded(this)) startActivity(Intent(this, OnboardingActivity::class.java))
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
        reminders(column)
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true // targetSdk 35 dibuja de borde a borde
            addView(column)
        })
        refresh()
        // primera vez (o tras reinstalar): programa los avisos con los valores por defecto
        if (getSystemService(JobScheduler::class.java).allPendingJobs.isEmpty()) SyncJob.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }
    }

    private fun reminders(column: LinearLayout) {
        column.addView(TextView(this).apply {
            text = getString(R.string.remind_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(0, dp(28), 0, dp(4))
        })
        fun number(label: Int, key: String) = field(column, getString(label), Prefs.int(this, key).toString(), true)
        val days = number(R.string.remind_days, Prefs.REMIND_DAYS)
        val message = field(column, getString(R.string.remind_text), Prefs.remindText(this), false)
        val threshold = number(R.string.remind_threshold, Prefs.THRESHOLD)
        val hours = number(R.string.remind_hours, Prefs.CHECK_HOURS)
        column.addView(TextView(this).apply {
            text = getString(R.string.remind_help)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(8), 0, dp(8))
        })
        column.addView(Button(this).apply {
            setText(R.string.remind_save)
            setOnClickListener {
                fun EditText.int(min: Int) = (text.toString().toIntOrNull() ?: 0).coerceAtLeast(min)
                Prefs.save(
                    this@SetupActivity,
                    mapOf(Prefs.REMIND_DAYS to days.int(0), Prefs.THRESHOLD to threshold.int(1), Prefs.CHECK_HOURS to hours.int(0)),
                    message.text.toString(),
                )
                SyncJob.schedule(this@SetupActivity)
                Toast.makeText(this@SetupActivity, R.string.remind_saved, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun field(column: LinearLayout, label: String, value: String, numeric: Boolean): EditText {
        column.addView(TextView(this).apply {
            text = label
            setPadding(0, dp(12), 0, 0)
        })
        return EditText(this).apply {
            setText(value)
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            column.addView(this)
        }
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
