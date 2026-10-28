package com.mosu.app.domain.repair

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

class MissingOsuBackfillCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CANCEL) return
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(MissingOsuBackfillWorker.WORK_NAME)
    }

    companion object {
        const val ACTION_CANCEL = "com.mosu.app.action.CANCEL_MISSING_OSU_BACKFILL"
    }
}
