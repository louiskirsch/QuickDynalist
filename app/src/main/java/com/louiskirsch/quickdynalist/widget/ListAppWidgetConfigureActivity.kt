package com.louiskirsch.quickdynalist.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.louiskirsch.quickdynalist.DynalistItemViewModel
import com.louiskirsch.quickdynalist.Location
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import kotlinx.android.synthetic.main.list_app_widget_configure.*

/**
 * The configuration screen for the [ListAppWidget] AppWidget.
 */
class ListAppWidgetConfigureActivity : AppCompatActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeAsUpIndicator(R.drawable.ic_action_discard)

        setResult(Activity.RESULT_CANCELED)
        setContentView(R.layout.list_app_widget_configure)

        widgetId = intent.extras?.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val adapter = ArrayAdapter<Location>(this,
                android.R.layout.simple_list_item_single_choice, ArrayList())
        widgetLocation.adapter = adapter
        widgetLocation.choiceMode = ListView.CHOICE_MODE_SINGLE

        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.locationsLiveData.observe(this, Observer { locations ->
            val initializing = adapter.isEmpty
            adapter.clear()
            adapter.addAll(locations)
            if (locations.isNotEmpty() && initializing) {
                initializeSelection(locations)
            }
        })
    }

    private fun initializeSelection(locations: List<Location>) {
        if (hasWidgetInfo(this, widgetId)) {
            val locationId = getLocation(this, widgetId)
            val type = getType(this, widgetId)
            val index = locations.indexOfFirst { it.id == locationId && it.typeName == type }
            widgetLocation.setItemChecked(index, true)
        } else {
            widgetLocation.setItemChecked(0, true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_list_app_widget_configure, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item!!.itemId) {
            android.R.id.home -> cancelWidgetCreation()
            R.id.create_list_app_widget -> createWidget()
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun cancelWidgetCreation(): Boolean {
        finish()
        return true
    }

    private fun createWidget(): Boolean {
        val selectedItem = widgetLocation.getItemAtPosition(widgetLocation.checkedItemPosition)
        saveWidgetInfo(this, widgetId, selectedItem as Location)

        val appWidgetManager = AppWidgetManager.getInstance(this)
        ListAppWidget.updateAppWidget(this, appWidgetManager, widgetId)

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
        return true
    }

    companion object {

        private const val PREFS_NAME = "com.louiskirsch.quickdynalist.widget.ListAppWidget"
        private const val PREF_PREFIX_KEY = "appwidget"

        private fun getPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFS_NAME, 0)
        }

        internal fun saveWidgetInfo(context: Context, appWidgetId: Int, item: Location) {
            val prefs = getPrefs(context).edit()
            prefs.putString("${PREF_PREFIX_KEY}_${appWidgetId}_title", item.name)
            prefs.putLong("${PREF_PREFIX_KEY}_${appWidgetId}_location", item.id)
            prefs.putString("${PREF_PREFIX_KEY}_${appWidgetId}_type", item.typeName)
            prefs.putString("${PREF_PREFIX_KEY}_${appWidgetId}_extraKey", item.extraIdKey)
            prefs.apply()
        }

        internal fun hasWidgetInfo(context: Context, appWidgetId: Int): Boolean {
            return getPrefs(context).contains("${PREF_PREFIX_KEY}_${appWidgetId}_location")
        }

        internal fun getTitle(context: Context, appWidgetId: Int): String {
            val prefs = getPrefs(context)
            return prefs.getString("${PREF_PREFIX_KEY}_${appWidgetId}_title", "")!!
        }

        internal fun getType(context: Context, appWidgetId: Int): String {
            val prefs = getPrefs(context)
            return prefs.getString("${PREF_PREFIX_KEY}_${appWidgetId}_type", "")!!
        }

        internal fun getEtraKey(context: Context, appWidgetId: Int): String {
            val prefs = getPrefs(context)
            return prefs.getString("${PREF_PREFIX_KEY}_${appWidgetId}_extraKey", "")!!
        }

        internal fun getLocation(context: Context, appWidgetId: Int): Long {
            val prefs = getPrefs(context)
            return prefs.getLong("${PREF_PREFIX_KEY}_${appWidgetId}_location", 1)
        }

        internal fun deleteWidgetInfo(context: Context, appWidgetId: Int) {
            val prefs = getPrefs(context).edit()
            prefs.remove("${PREF_PREFIX_KEY}_${appWidgetId}_title")
            prefs.remove("${PREF_PREFIX_KEY}_${appWidgetId}_location")
            prefs.apply()
        }
    }
}

