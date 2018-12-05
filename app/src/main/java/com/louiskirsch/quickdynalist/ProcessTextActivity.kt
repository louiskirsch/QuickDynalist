package com.louiskirsch.quickdynalist

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString()
        val dynalist = Dynalist(this)

        val addItemAndFinish = {
            dynalist.addItem(text, showToastOnSuccess = true)
            finish()
        }

        if (!dynalist.isAuthenticated) {
            dynalist.authenticate(onDone = {
                if (dynalist.isAuthenticated) {
                    addItemAndFinish()
                }
            })
        } else {
            addItemAndFinish()
        }
    }
}
