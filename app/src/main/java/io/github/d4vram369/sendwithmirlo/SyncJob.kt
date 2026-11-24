package io.github.d4vram369.sendwithmirlo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * RF-05: que no se olvide sincronizar. Tres trabajos de JobScheduler (del sistema: sin WorkManager ni AndroidX).
 * - REMIND: cada N días, si hay posts pendientes, lo recuerda con el texto de la persona.
 * - CHECK: cada N horas y solo con WiFi, si hay al menos `umbral` pendientes, pregunta y ofrece «Aplicar».
 * - APPLY: lo lanza «Aplicar». Fuerza el arranque de Syncthing-Fork, espera a que la carpeta se vacíe
 *   (Mirlo-desktop borra cada .mirlo al guardarlo) y cuenta el resultado.
 */
class SyncJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                run(params.jobId)
            } catch (e: Exception) {
                Log.w(TAG, "trabajo ${params.jobId}", e)
            }
            jobFinished(params, false)
        }.start()
        return true // sigue en el hilo
    }

    override fun onStopJob(params: JobParameters) = false

    private fun run(job: Int) {
        val tree = Prefs.tree(this) ?: return
        val pending = Inbox.count(this, tree)
        Log.i(TAG, "trabajo $job: $pending pendientes")
        when (job) {
            REMIND -> if (pending > 0) notify(Prefs.remindText(this), pending, offerApply = true)
            CHECK -> if (pending >= Prefs.int(this, Prefs.THRESHOLD)) {
                notify(getString(R.string.check_question), pending, offerApply = true)
            }
            APPLY -> apply(pending) { Inbox.count(this, tree) }
        }
    }

    private fun apply(before: Int, count: () -> Int) {
        if (before == 0) return notify(getString(R.string.apply_nothing), 0, offerApply = false)
        notify(getString(R.string.apply_running), before, offerApply = false)
        syncthing("START")
        val end = SystemClock.elapsedRealtime() + APPLY_WAIT_MS
        var left = before
        while (left > 0 && SystemClock.elapsedRealtime() < end) {
            Thread.sleep(5_000)
            left = count()
        }
        syncthing("FOLLOW") // vuelve a sus propias condiciones de ejecución
        Log.i(TAG, "aplicar: $before → $left")
        if (left == 0) notify(getString(R.string.apply_done, before), 0, offerApply = false)
        else notify(getString(R.string.apply_left), left, offerApply = true)
    }

    /** Syncthing-Fork solo hace caso si tiene activado «Control del servicio mediante difusión». */
    private fun syncthing(action: String) {
        // ponytail: solo Syncthing-Fork; el Syncthing de Android original está abandonado
        sendBroadcast(Intent("$ST_PKG.action.$action").setClassName(ST_PKG, ST_RECEIVER))
    }

    private fun notify(text: String, pending: Int, offerApply: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, SetupActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mirlo)
            .setContentTitle(if (pending > 0) getString(R.string.notif_title, pending) else getString(R.string.app_name))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (offerApply) {
            val apply = PendingIntent.getBroadcast(
                this, 0, Intent(this, ApplyReceiver::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(Notification.Action.Builder(null, getString(R.string.apply), apply).build())
        }
        nm.notify(NOTIFICATION, b.build())
    }

    /** El botón «Aplicar» de la notificación. */
    class ApplyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = start(context)
    }

    companion object {
        private const val TAG = "MirloX"
        private const val CHANNEL = "sync"
        private const val NOTIFICATION = 1
        const val REMIND = 1
        const val CHECK = 2
        const val APPLY = 3
        private const val HOUR = 3_600_000L
        private const val APPLY_WAIT_MS = 3 * 60_000L
        private const val ST_PKG = "com.github.catfriend1.syncthingfork"
        private const val ST_RECEIVER = "com.nutomic.syncthingandroid.receiver.AppConfigReceiver"

        /** Programa (o cancela, con 0) los dos trabajos periódicos según los ajustes. */
        fun schedule(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java)
            val cn = ComponentName(context, SyncJob::class.java)
            val days = Prefs.int(context, Prefs.REMIND_DAYS)
            val hours = Prefs.int(context, Prefs.CHECK_HOURS)
            if (days > 0) {
                js.schedule(JobInfo.Builder(REMIND, cn).setPeriodic(days * 24 * HOUR).setPersisted(true).build())
            } else js.cancel(REMIND)
            if (hours > 0) {
                // ponytail: «tu WiFi» = cualquier red sin límite de datos; el nombre de la red exige permiso de ubicación
                js.schedule(
                    JobInfo.Builder(CHECK, cn).setPeriodic(hours * HOUR)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).setPersisted(true).build(),
                )
            } else js.cancel(CHECK)
        }

        fun start(context: Context) {
            context.getSystemService(JobScheduler::class.java)
                .schedule(JobInfo.Builder(APPLY, ComponentName(context, SyncJob::class.java)).setOverrideDeadline(0).build())
        }
    }
}
