package com.louiskirsch.quickdynalist

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import kotlinx.android.synthetic.main.activity_advanced_item.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class AdvancedItemActivity : AppCompatActivity() {
    private val dynalist: Dynalist = Dynalist(this)

    private lateinit var adapter: ArrayAdapter<DynalistItem>
    private var location: DynalistItem? = null
    private val calendar = Calendar.getInstance()

    companion object {
        const val EXTRA_LOCATION = "EXTRA_LOCATION"
        const val EXTRA_ITEM_TEXT = "EXTRA_ITEM_TEXT"
        const val EXTRA_SELECT_BOOKMARK = "EXTRA_SELECT_BOOKMARK"
        private const val MAX_SUBMENU_ITEMS = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_item)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeAsUpIndicator(R.drawable.ic_action_discard)
        actionBarView.transitionName = "toolbar"
        window.allowEnterTransitionOverlap = true

        location = intent.getParcelableExtra(EXTRA_LOCATION)
        itemContents.setText(intent.getCharSequenceExtra(EXTRA_ITEM_TEXT))

        val selectBookmark = intent.getBooleanExtra(EXTRA_SELECT_BOOKMARK, false)
        if (location!!.isBookmark && selectBookmark) {
            adapter = ArrayAdapter(this,
                    android.R.layout.simple_spinner_item, ArrayList())
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            itemLocation.adapter = adapter

            val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
            model.bookmarksLiveData.observe(this, Observer<List<DynalistItem>> {
                val initializing = adapter.count == 0
                adapter.clear()
                adapter.addAll(it)
                if (initializing) {
                    val index = it.indexOf(location!!)
                    itemLocation.setSelection(max(index, 0))
                }
            })
        } else {
            itemLocation.visibility = View.GONE
            title = location!!.shortenedName
        }

        setupDatePicker()
        setupTimePicker()
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        dynalist.subscribe()
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        dynalist.unsubscribe()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.advanced_item_activity_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item!!.itemId) {
            android.R.id.home -> discard()
            R.id.send_item -> sendItem()
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun discard(): Boolean {
        fixedFinishAfterTransition()
        return true
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState!!.putSerializable("CALENDAR", calendar)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        val restoredCalendar = savedInstanceState!!.getSerializable("CALENDAR") as Calendar
        calendar.time = restoredCalendar.time
    }

    private fun setupDatePicker() {
        itemDate.setOnClickListener {
            val dialog = DatePickerDialog(this, { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                val dateFormat = android.text.format.DateFormat.getDateFormat(this)
                itemDate.setText(dateFormat.format(calendar.time))
            }, calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH))
            dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                    getString(R.string.unset)) { _, which ->
                if (which == DialogInterface.BUTTON_NEGATIVE) {
                    itemDate.text.clear()
                }
            }
            dialog.show()
        }
    }

    private fun setupTimePicker() {
        itemTime.setOnClickListener {
            val dialog = TimePickerDialog(this, { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                val timeFormat = android.text.format.DateFormat.getTimeFormat(this)
                itemTime.setText(timeFormat.format(calendar.time))
            }, calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(this))
            dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                    getString(R.string.unset)) { _, which ->
                if (which == DialogInterface.BUTTON_NEGATIVE) {
                    itemTime.text.clear()
                }
            }
            dialog.show()
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun sendItem(): Boolean {
        if (itemContents.text.isEmpty()) {
            itemContents.error = getString(R.string.item_contents_required)
            return true
        }
        if (itemTime.text.isNotEmpty() && itemDate.text.isEmpty()) {
            itemDate.error = getText(R.string.item_date_required)
            return true
        }

        val dateString = if (itemDate.text.isNotEmpty() && itemTime.text.isNotEmpty()) {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm")
            " !(${formatter.format(calendar.time)})"
        } else if (itemDate.text.isNotEmpty()) {
            val formatter = SimpleDateFormat("yyyy-MM-dd")
            " !(${formatter.format(calendar.time)})"
        } else {
            ""
        }
        val contents = itemContents.text.toString() + dateString
        val targetLocation = if (itemLocation.visibility == View.VISIBLE)
            itemLocation.selectedItem as DynalistItem
        else
            location!!
        dynalist.addItem(contents, targetLocation, itemNotes.text.toString())
        fixedFinishAfterTransition()
        return true
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onItemEvent(event: ItemEvent) {
        if (!event.success)
            toast(R.string.add_item_error)
    }
}
