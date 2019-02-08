package com.louiskirsch.quickdynalist

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.louiskirsch.quickdynalist.jobs.SyncJob
import org.jetbrains.anko.toast

class SyncShortcutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncJob.forceSync()
        // TODO could also start this in foreground service and show notification
        toast(R.string.sync_started)
        finish()
    }
}
