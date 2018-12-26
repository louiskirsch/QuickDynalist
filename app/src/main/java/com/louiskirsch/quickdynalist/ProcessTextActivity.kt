package com.louiskirsch.quickdynalist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.toast

class ProcessTextActivity : Activity() {
    private val dynalist: Dynalist = Dynalist(this)
    private lateinit var text: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString()
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        dynalist.subscribe()

        if (!dynalist.isAuthenticated) {
            dynalist.authenticate()
        } else {
            dynalist.addItem(text)
        }
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        dynalist.unsubscribe()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAuthenticationEvent(event: AuthenticatedEvent) {
        if (event.success) {
            dynalist.addItem(text)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onItemEvent(event: ItemEvent) {
        if (event.success)
            toast(R.string.add_item_success)
        else
            toast(R.string.add_item_error)
        finish()
    }
}
