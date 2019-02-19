package com.louiskirsch.quickdynalist.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.louiskirsch.quickdynalist.DynalistApp
import com.louiskirsch.quickdynalist.FilterLocation
import com.louiskirsch.quickdynalist.ItemLocation
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItemFilter
import io.objectbox.kotlin.boxFor

class ListAppWidgetConfigurationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        val location = if (intent.hasExtra(DynalistApp.EXTRA_DISPLAY_ITEM_ID)) {
            val locationId = intent.getLongExtra(DynalistApp.EXTRA_DISPLAY_ITEM_ID, 1)
            val item = DynalistApp.instance.boxStore.boxFor<DynalistItem>().get(locationId)
            ItemLocation(item)
        } else {
            val locationId = intent.getLongExtra(DynalistApp.EXTRA_DISPLAY_FILTER_ID, 1)
            val filter = DynalistApp.instance.boxStore.boxFor<DynalistItemFilter>().get(locationId)
            FilterLocation(filter, context)
        }
        ListAppWidgetConfigureActivity.saveWidgetInfo(context, widgetId, location)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        ListAppWidget.updateAppWidget(context, appWidgetManager, widgetId)
    }


}
