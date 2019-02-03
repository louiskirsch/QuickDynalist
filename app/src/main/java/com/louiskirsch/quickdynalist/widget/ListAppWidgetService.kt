package com.louiskirsch.quickdynalist.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.louiskirsch.quickdynalist.DynalistApp
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.adapters.CachedDynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query


class ListAppWidgetService: RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        return ListViewsFactory(applicationContext, intent!!)
    }

}


class ListViewsFactory(private val context: Context, intent: Intent)
    : RemoteViewsService.RemoteViewsFactory {

    private val items = ArrayList<CachedDynalistItem>()
    private val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID)
    private val parentId = ListAppWidgetConfigureActivity.getLocation(context, widgetId)

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun getLoadingView(): RemoteViews? = null
    override fun getItemId(position: Int): Long = items[position].item.clientId
    override fun hasStableIds(): Boolean = true
    override fun getCount(): Int = items.size
    override fun getViewTypeCount(): Int = 1

    override fun onDataSetChanged() {
        val box = DynalistApp.instance.boxStore.boxFor<DynalistItem>()
        val newItems = box.query {
            equal(DynalistItem_.parentId, parentId)
            and()
            notEqual(DynalistItem_.name, "")
            and()
            equal(DynalistItem_.hidden, false)
            and()
            equal(DynalistItem_.isChecked, false)
            order(DynalistItem_.position)
            eager(100, DynalistItem_.children)
        }.find().map { item ->
            item.children.sortBy { child -> child.position }
            CachedDynalistItem(item, context)
        }
        items.clear()
        items.addAll(newItems)
    }

    override fun getViewAt(position: Int): RemoteViews {
        val item = items[position]
        val rv = RemoteViews(context.packageName, R.layout.list_app_widget_item)

        rv.setTextViewText(R.id.itemText, item.spannableText)

        rv.setTextViewText(R.id.itemNotes, item.spannableNotes)
        val notesVisibility = if (item.spannableNotes.isNotBlank()) View.VISIBLE else View.GONE
        rv.setViewVisibility(R.id.itemNotes, notesVisibility)

        rv.setTextViewText(R.id.itemChildren, item.spannableChildren)
        val childrenVisibility = if (item.spannableChildren.isNotBlank()) View.VISIBLE else View.GONE
        rv.setViewVisibility(R.id.itemChildren, childrenVisibility)

        val fillInIntent = Intent().apply {
            putExtra(DynalistApp.EXTRA_DISPLAY_ITEM_ID, item.item.clientId)
        }
        rv.setOnClickFillInIntent(R.id.widgetItem, fillInIntent)

        return rv
    }
}
