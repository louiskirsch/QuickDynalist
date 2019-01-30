package com.louiskirsch.quickdynalist

import android.text.style.ClickableSpan
import android.view.View
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import io.objectbox.kotlin.boxFor
import org.greenrobot.eventbus.EventBus

class DynalistLinkSpan(val item: DynalistItem): ClickableSpan() {

    override fun onClick(widget: View) {
        EventBus.getDefault().post(DynalistLocateEvent(item))
    }

}
