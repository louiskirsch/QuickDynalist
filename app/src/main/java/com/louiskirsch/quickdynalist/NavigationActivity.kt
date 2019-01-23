package com.louiskirsch.quickdynalist

import android.os.Bundle
import com.google.android.material.navigation.NavigationView
import androidx.core.view.GravityCompat
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.activity_navigation.*
import kotlinx.android.synthetic.main.app_bar_navigation.*
import kotlinx.android.synthetic.main.fragment_item_list.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.toast

class NavigationActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val dynalist: Dynalist = Dynalist(this)

    private var inboxes: List<DynalistItem>? = null

    companion object {
        const val EXTRA_DISPLAY_ITEM = "EXTRA_DISPLAY_ITEM"
        const val EXTRA_ITEM_TEXT = "EXTRA_ITEM_TEXT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)
        setSupportActionBar(toolbar)
        window.allowEnterTransitionOverlap = true

        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)

        val itemsModel = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        val fragmentModel = ViewModelProviders.of(this).get(ItemListFragmentViewModel::class.java)

        itemsModel.bookmarksLiveData.observe(this, Observer<List<DynalistItem>> { inboxes ->
            this.inboxes = inboxes
            nav_view.menu.findItem(R.id.submenu_bookmarks_list).subMenu.run {
                clear()
                inboxes.forEachIndexed { i, item ->
                     add(R.id.group_bookmarks_list, i, i, item.shortenedName).apply {
                         isCheckable = true
                     }
                }
                fragmentModel.selectedBookmark.value?.let {
                    updateCheckedBookmark(this, it)
                }
            }
        })
        fragmentModel.selectedBookmark.observe(this, Observer { item ->
            inboxes?.run {
                val menu = nav_view.menu.findItem(R.id.submenu_bookmarks_list).subMenu
                updateCheckedBookmark(menu, item)
            }
        })

        if (savedInstanceState == null) {
            val parent: DynalistItem = intent.getParcelableExtra(EXTRA_DISPLAY_ITEM)
            val itemText = intent.getCharSequenceExtra(EXTRA_ITEM_TEXT)
            val fragment = ItemListFragment.newInstance(parent, itemText)
            supportFragmentManager.beginTransaction()
                    .add(R.id.fragment_container, fragment).commit()
        }
    }

    private fun updateCheckedBookmark(menu: Menu, item: DynalistItem) {
        val itemIndex = inboxes!!.indexOfFirst { it.clientId == item.clientId }
        if (itemIndex >= 0)
            nav_view.setCheckedItem(menu.getItem(itemIndex))
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

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.navigation, menu)
        return true
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (item.groupId == R.id.group_bookmarks_list) {
            val currentFragment = supportFragmentManager
                    .findFragmentById(R.id.fragment_container) as ItemListFragment
            val itemText = currentFragment.itemContents.text
            val fragment = ItemListFragment.newInstance(inboxes!![item.itemId], itemText.toString())
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            itemText.clear()
        }
        if (item.itemId == R.id.open_quick_dialog)
            fixedFinishAfterTransition()

        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onItemEvent(event: ItemEvent) {
        if (!event.success)
            toast(R.string.add_item_error)
    }
}
