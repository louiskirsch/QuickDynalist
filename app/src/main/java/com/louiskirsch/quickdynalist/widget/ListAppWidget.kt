package com.louiskirsch.quickdynalist.widget

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.louiskirsch.quickdynalist.R
import android.content.Intent
import android.net.Uri
import com.louiskirsch.quickdynalist.DynalistApp
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItemFilter


/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in [ListAppWidgetConfigureActivity]
 */
class ListAppWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager,
                          appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            ListAppWidgetConfigureActivity.deleteWidgetInfo(context, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {}
    override fun onDisabled(context: Context) {}

    companion object {

        fun notifyAllDataChanged(context: Context) {
            AppWidgetManager.getInstance(context).apply {
                val widgetIds = getAppWidgetIds(ComponentName(context, ListAppWidget::class.java))
                notifyAppWidgetViewDataChanged(widgetIds, R.id.appwidget_list)
            }
        }

        fun notifyItemChanged(context: Context, item: DynalistItem) {
            val affectedItems = listOf(item.clientId, item.parent.targetId,
                    item.parent.target?.parent?.targetId)
            AppWidgetManager.getInstance(context).apply {
                val component = ComponentName(context, ListAppWidget::class.java)
                val changedWidgets = getAppWidgetIds(component).filter {
                    val type = ListAppWidgetConfigureActivity.getType(context, it)
                    // Can we do something smarter here instead of refreshing them all?
                    if (type == DynalistItemFilter.LOCATION_TYPE) {
                        true
                    } else {
                        val location = ListAppWidgetConfigureActivity.getLocation(context, it)
                        location in affectedItems
                    }
                }.toIntArray()
                notifyAppWidgetViewDataChanged(changedWidgets, R.id.appwidget_list)
            }
        }

        fun notifyFilterChanged(context: Context, filter: DynalistItemFilter) {
            AppWidgetManager.getInstance(context).apply {
                val component = ComponentName(context, ListAppWidget::class.java)
                val changedWidgets = getAppWidgetIds(component).filter {
                    val type = ListAppWidgetConfigureActivity.getType(context, it)
                    val location = ListAppWidgetConfigureActivity.getLocation(context, it)
                    type == DynalistItemFilter.LOCATION_TYPE  &&
                            location == filter.id
                }.toIntArray()
                notifyAppWidgetViewDataChanged(changedWidgets, R.id.appwidget_list)
            }
        }

        internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager,
                                     appWidgetId: Int) {

            val title = ListAppWidgetConfigureActivity.getTitle(context, appWidgetId)
            val locationId = ListAppWidgetConfigureActivity.getLocation(context, appWidgetId)
            val extraKey = ListAppWidgetConfigureActivity.getEtraKey(context, appWidgetId)

            val views = RemoteViews(context.packageName, R.layout.list_app_widget)
            val intent = Intent(context, ListAppWidgetService::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            intent.data = Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))
            views.setRemoteAdapter(R.id.appwidget_list, intent)
            views.setTextViewText(R.id.appwidget_header, title)

            val viewListIntent = createViewListPendingIntent(
                    context, appWidgetId, extraKey, locationId)
            views.setOnClickPendingIntent(R.id.appwidget_header, viewListIntent)

            val itemListIntent = createViewListPendingIntent(context, appWidgetId + 100000)
            views.setPendingIntentTemplate(R.id.appwidget_list, itemListIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.appwidget_list)
        }

        private fun createViewListPendingIntent(context: Context, requestCode: Int,
                                                extraKey: String? = null,
                                                locationId: Long? = null): PendingIntent? {
            return Intent("com.louiskirsch.quickdynalist.SHOW_LIST").apply {
                locationId?.let { putExtra(extraKey, it) }
                putExtra(DynalistApp.EXTRA_FROM_SHORTCUT, true)
            }.let {
                TaskStackBuilder.create(context).addNextIntentWithParentStack(it).intents
            }.let {
                it[0].apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                PendingIntent.getActivities(context, requestCode, it,
                        PendingIntent.FLAG_UPDATE_CURRENT)
            }
        }
    }
}

