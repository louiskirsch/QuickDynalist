package com.louiskirsch.quickdynalist.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.louiskirsch.quickdynalist.DynalistApp
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.SearchActivity
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import org.jetbrains.anko.toast

class DynalistEditActionHelper {

    private val getContext: () -> Context?
    private val context get() = getContext()!!
    private val getMenuInflater: () -> MenuInflater
    private val menuInflater get() = getMenuInflater()
    private val startActivityForResult: (Intent, Int) -> Unit

    constructor(activity: Activity) {
        getContext = { activity }
        getMenuInflater = activity::getMenuInflater
        startActivityForResult = activity::startActivityForResult
    }
    constructor(fragment: Fragment) {
        getContext = fragment::getContext
        getMenuInflater = { fragment.activity!!.menuInflater }
        startActivityForResult = fragment::startActivityForResult
    }

    fun dispatchResult(requestCode: Int, resultCode: Int, data: Intent?,
                       callback: (Int, String) -> Unit) {
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                context.resources.getInteger(R.integer.request_code_link) -> {
                    val fromViewId = data!!.getBundleExtra("payload")!!.getInt(EXTRA_VIEW_ID)
                    val linkTarget = data.getParcelableExtra<DynalistItem>(
                            DynalistApp.EXTRA_DISPLAY_ITEM)!!
                    if (linkTarget.serverFileId != null && linkTarget.serverItemId != null) {
                        linkTarget.linkText?.let { callback (fromViewId, it) }
                    } else {
                        context.toast(R.string.error_link_offline)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun setupEditActions(itemContents: EditText) {
        itemContents.customInsertionActionModeCallback = object: ActionMode.Callback2() {
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                return false
            }
            override fun onDestroyActionMode(mode: ActionMode?) {}
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                menuInflater.inflate(R.menu.item_text_insert_context_menu, menu)
                return true
            }
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                return when (item?.itemId) {
                    R.id.action_link_to -> {
                        val intent = Intent(context, SearchActivity::class.java).apply {
                            putExtra("payload", Bundle().apply {
                                putInt(EXTRA_VIEW_ID, itemContents.id)
                            })
                        }
                        startActivityForResult(intent,
                                context.resources.getInteger(R.integer.request_code_link))
                        true
                    }
                    else -> false
                }
            }
        }
        itemContents.customSelectionActionModeCallback = object: ActionMode.Callback2() {
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                menu!!.findItem(android.R.id.shareText)?.isVisible = false
                (0 until menu.size()).map { menu.getItem(it) }.firstOrNull {
                    it.intent?.component?.className == context.getString(R.string.ACTION_PROCESS_TEXT)
                }?.isVisible = false
                return true
            }
            override fun onDestroyActionMode(mode: ActionMode?) {}
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                menuInflater.inflate(R.menu.item_text_selection_context_menu, menu)
                return true
            }
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                val spanHighlight = ContextCompat.getColor(context, R.color.spanHighlight)
                val codeColor = ContextCompat.getColor(context, R.color.codeColor)
                val toggleSpan = { span: Any ->
                    itemContents.apply {
                        val spans = text.getSpans(selectionStart, selectionEnd,
                                span.javaClass).filter {
                            when {
                                it is StyleSpan && span is StyleSpan -> {
                                    it.style == span.style
                                }
                                it is BackgroundColorSpan && span is BackgroundColorSpan -> {
                                    it.backgroundColor == span.backgroundColor
                                }
                                it is ForegroundColorSpan && span is ForegroundColorSpan -> {
                                    it.foregroundColor == span.foregroundColor
                                }
                                else -> true
                            }
                        }
                        if (spans.isEmpty())
                            text.setSpan(span, selectionStart, selectionEnd, 0)
                        else
                            spans.forEach { text.removeSpan(it) }
                    }
                    true
                }
                return when (item?.itemId) {
                    R.id.selection_bold -> {
                        toggleSpan(StyleSpan(Typeface.BOLD))
                    }
                    R.id.selection_italic -> {
                        toggleSpan(StyleSpan(Typeface.ITALIC))
                    }
                    R.id.selection_strikethrough ->
                    {
                        toggleSpan(StrikethroughSpan())
                    }
                    R.id.selection_code -> {
                        toggleSpan(BackgroundColorSpan(spanHighlight))
                        toggleSpan(ForegroundColorSpan(codeColor))
                    }
                    R.id.selection_regular -> {
                        itemContents.apply {
                            text.clearSpans(DynalistItem.markdownSpanTypes,
                                    selectionStart, selectionEnd)
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    companion object {
        private const val EXTRA_VIEW_ID = "EXTRA_VIEW_ID"
    }
}
