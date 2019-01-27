package com.louiskirsch.quickdynalist

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import com.louiskirsch.quickdynalist.adapters.EmojiAdapter
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import kotlinx.android.synthetic.main.activity_shortcut.*

class ShortcutActivity : AppCompatActivity() {

    private val shortcutIntent = Intent()
    private val emojiAdapter: EmojiAdapter = EmojiAdapter()
    private var location: DynalistItem? = null

    companion object {
        const val EXTRA_LOCATION = "EXTRA_LOCATION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shortcut)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeAsUpIndicator(R.drawable.ic_action_discard)
        supportActionBar!!.title = ""
        actionBarView.transitionName = "toolbar"
        window.allowEnterTransitionOverlap = true

        if (intent.hasExtra(EXTRA_LOCATION)) {
            location = intent.getParcelableExtra(EXTRA_LOCATION) as DynalistItem
            shortcutLocation.visibility = View.GONE
            shortcutLocationHeader.visibility = View.GONE
            shortcutName.setText(location!!.strippedMarkersName.take(10))
        } else {
            val adapter = ArrayAdapter<DynalistItem>(this,
                    android.R.layout.simple_spinner_item, ArrayList())
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            shortcutLocation.adapter = adapter

            shortcutLocation.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    location = adapter.getItem(position)!!
                    shortcutName.setText(location!!.strippedMarkersName.take(10))
                }
            }

            val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
            model.bookmarksLiveData.observe(this, Observer<List<DynalistItem>> {
                if (location == null)
                    location = it[0]
                adapter.clear()
                adapter.addAll(it)
            })
        }

        shortcutTypeQuickDialog.isChecked = true
        shortcutIconList.layoutManager = GridLayoutManager(this, 7)
        shortcutIconList.adapter = emojiAdapter
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_shortcut, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item!!.itemId) {
            android.R.id.home -> discard()
            R.id.create_shortcut -> createShortcut()
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun createShortcut(): Boolean {
        if (shortcutTypeQuickDialog.isChecked)
            shortcutIntent.component = ComponentName(this, MainActivity::class.java)
        else
            shortcutIntent.component = ComponentName(this, NavigationActivity::class.java)
        shortcutIntent.apply {
            action = Intent.ACTION_VIEW
            putExtra(DynalistApp.EXTRA_DISPLAY_ITEM_ID, location!!.clientId)
            putExtra(DynalistApp.EXTRA_FROM_SHORTCUT, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        val shortcutType = if (shortcutTypeQuickDialog.isChecked) "dialog" else "list"
        val id = "shortcut-${location!!.clientId}-$shortcutType"
        val shortcutInfo = ShortcutInfoCompat.Builder(this, id).run {
            setAlwaysBadged()
            setIntent(shortcutIntent)
            setShortLabel(shortcutName.text)
            setIcon(IconCompat.createWithBitmap(
                    emojiAdapter.selectedValue.toBitmap(192f, Color.BLACK)))
            build()
        }
        if (intent.hasExtra(EXTRA_LOCATION)) {
            ShortcutManagerCompat.requestPinShortcut(this, shortcutInfo, null)
        } else {
            val resultIntent = ShortcutManagerCompat.createShortcutResultIntent(this, shortcutInfo)
            setResult(Activity.RESULT_OK, resultIntent)
        }
        fixedFinishAfterTransition()
        return true
    }

    private fun discard(): Boolean {
        fixedFinishAfterTransition()
        return true
    }
}
