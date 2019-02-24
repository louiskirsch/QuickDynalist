package com.louiskirsch.quickdynalist.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.louiskirsch.quickdynalist.Dynalist
import com.louiskirsch.quickdynalist.DynalistApp
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.adapters.CachedDynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItemFilter
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

    private var displayParentText = false

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun getLoadingView(): RemoteViews? = null
    override fun getItemId(position: Int): Long = items[position].item.clientId
    override fun hasStableIds(): Boolean = true
    override fun getCount(): Int = items.size
    override fun getViewTypeCount(): Int = 1

    override fun onDataSetChanged() {
        val locationId = ListAppWidgetConfigureActivity.getLocation(context, widgetId)
        val locationType = ListAppWidgetConfigureActivity.getType(context, widgetId)
        val queryResult = when (locationType) {
            DynalistItem.LOCATION_TYPE -> getDynalistItemChildren(locationId)
            DynalistItemFilter.LOCATION_TYPE -> getDynalistFilterItems(locationId)
            else -> throw Exception("Invalid location")
        }
        displayParentText = when (locationType) {
            DynalistItem.LOCATION_TYPE -> false
            DynalistItemFilter.LOCATION_TYPE -> true
            else -> throw Exception("Invalid location")
        }
        val maxChildren = Dynalist(context).displayChildrenCount
        val newItems = queryResult.map { item ->
            item.children.sortBy { child -> child.position }
            CachedDynalistItem(item, context, maxChildren).apply {
                eagerInitialize(displayParentText)
            }
        }
        items.clear()
        items.addAll(newItems)
    }

    private fun getDynalistItemChildren(parentId: Long): List<DynalistItem> {
        val box = DynalistApp.instance.boxStore.boxFor<DynalistItem>()
        return box.query {
            equal(DynalistItem_.parentId, parentId)
            and()
            notEqual(DynalistItem_.name, "")
            and()
            equal(DynalistItem_.hidden, false)
            and()
            equal(DynalistItem_.isChecked, false)
            order(DynalistItem_.position)
            if (displayParentText)
                eager(DynalistItem_.children, DynalistItem_.parent)
            else
                eager(DynalistItem_.children)
        }.find()
    }

    private fun getDynalistFilterItems(filterId: Long): List<DynalistItem> {
        val box = DynalistApp.instance.boxStore.boxFor<DynalistItemFilter>()
        return box.get(filterId)?.items ?: emptyList()
    }

    override fun getViewAt(position: Int): RemoteViews {
        val item = items[position]
        val rv = RemoteViews(context.packageName, R.layout.list_app_widget_item)

        if (displayParentText) {
            val visibility = if (item.spannableParent.isNotBlank())
                View.VISIBLE else View.GONE
            rv.setViewVisibility(R.id.itemParent, visibility)
            rv.setTextViewText(R.id.itemText, item.spannableParent)
        }

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
