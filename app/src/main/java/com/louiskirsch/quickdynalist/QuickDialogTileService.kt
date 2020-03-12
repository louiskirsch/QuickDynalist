package com.louiskirsch.quickdynalist

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(api = Build.VERSION_CODES.N)
class QuickDialogTileService : TileService() {

    private fun showQuickDialog() {
        startActivityAndCollapse(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
    }

    override fun onClick() {
        if (isLocked) {
            unlockAndRun { showQuickDialog() }
        } else {
            showQuickDialog()
        }
    }
}
