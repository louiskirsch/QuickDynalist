package com.louiskirsch.quickdynalist

import android.text.style.ClickableSpan
import android.view.View
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItemFilter
import com.louiskirsch.quickdynalist.objectbox.DynalistTag
import io.objectbox.kotlin.boxFor
import org.greenrobot.eventbus.EventBus

class DynalistTagSpan(private val tag: DynalistTag): ClickableSpan() {

    override fun onClick(widget: View) {
        val filter = DynalistItemFilter().apply {
            name = widget.context.getString(R.string.filter_name_tag, tag.fullName)
            tags.add(tag)
            searchDepth = DynalistItemFilter.MAX_SEARCH_DEPTH
        }
        EventBus.getDefault().post(DynalistFilterEvent(filter))
    }

}
