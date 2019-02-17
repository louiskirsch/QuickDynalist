package com.louiskirsch.quickdynalist

import android.text.style.ClickableSpan
import android.view.View
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItemFilter
import com.louiskirsch.quickdynalist.objectbox.DynalistTag
import io.objectbox.kotlin.boxFor
import org.greenrobot.eventbus.EventBus

class DynalistTagSpan(private val tag: DynalistTag,
                      private val tagParent: DynalistItem?): ClickableSpan() {

    override fun onClick(widget: View) {
        val filter = DynalistItemFilter().apply {
            name = widget.context.getString(R.string.filter_name_tag)
            tags.add(tag)
            tagParent?.let { parent.target = it }
            searchDepth = Int.MAX_VALUE
        }
        EventBus.getDefault().post(DynalistFilterEvent(filter))
    }

}
