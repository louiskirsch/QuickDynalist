package com.louiskirsch.quickdynalist.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.louiskirsch.quickdynalist.DynalistApp
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import io.objectbox.kotlin.boxFor

class ListAppWidgetConfigurationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        val locationId = intent.getLongExtra(DynalistApp.EXTRA_DISPLAY_ITEM_ID, 1)
        val location = DynalistApp.instance.boxStore.boxFor<DynalistItem>().get(locationId)
        ListAppWidgetConfigureActivity.saveWidgetInfo(context, widgetId, location)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        ListAppWidget.updateAppWidget(context, appWidgetManager, widgetId)
    }
}
