package com.louiskirsch.quickdynalist

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.louiskirsch.quickdynalist.jobs.SyncJob
import org.jetbrains.anko.toast

class SyncShortcutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dynalist = Dynalist(this)
        if (dynalist.isAuthenticated) {
            SyncJob.forceSync()
            toast(R.string.sync_started)
            finish()
        } else {
            dynalist.authenticate()
        }
    }
}
