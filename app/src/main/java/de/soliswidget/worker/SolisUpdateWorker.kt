package de.soliswidget.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.soliswidget.data.SolisRepository
import de.soliswidget.widget.SolisWidgetReceiver

class SolisUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            SolisRepository.fetch(applicationContext)
            SolisWidgetReceiver.updateAll(applicationContext)
            Result.success()
        } catch (_: Exception) {
            SolisWidgetReceiver.updateAll(applicationContext)
            Result.retry()
        }
    }
}
