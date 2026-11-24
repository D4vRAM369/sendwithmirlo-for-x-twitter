package io.github.d4vram369.mirlox

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Sin interfaz: recibe el enlace, deja el archivo, avisa con un toast y se cierra. */
class SendActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = PostId.fromText(intent?.getStringExtra(Intent.EXTRA_TEXT))
        if (id == null) {
            toast(getString(R.string.not_a_post))
            finish()
            return
        }
        val tree = Prefs.tree(this)
        if (tree == null) {
            // primera vez: se elige la carpeta y el post no se pierde
            startActivity(Intent(this, SetupActivity::class.java).putExtra(SetupActivity.EXTRA_PENDING, id))
            finish()
            return
        }
        Thread {
            val message = try {
                when (Inbox.write(this, tree, id)) {
                    Inbox.Result.WRITTEN -> getString(R.string.sent)
                    Inbox.Result.ALREADY_THERE -> getString(R.string.already_queued)
                }
            } catch (e: SecurityException) {
                getString(R.string.lost_access)
            } catch (e: Exception) {
                getString(R.string.send_failed, e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread {
                toast(message)
                finish()
            }
        }.start()
    }

    private fun toast(text: String) = Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
}
