package com.louiskirsch.quickdynalist

import android.os.Bundle
import com.google.android.material.navigation.NavigationView
import androidx.core.view.GravityCompat
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.louiskirsch.quickdynalist.jobs.SyncJob
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.utils.fixedFinishAfterTransition
import com.louiskirsch.quickdynalist.utils.inputMethodManager
import kotlinx.android.synthetic.main.activity_navigation.*
import kotlinx.android.synthetic.main.app_bar_navigation.*
import kotlinx.android.synthetic.main.fragment_item_list.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.alert
import org.jetbrains.anko.okButton
import org.jetbrains.anko.toast
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import android.content.ActivityNotFoundException




class NavigationActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val dynalist: Dynalist = Dynalist(this)

    private var inboxes: List<DynalistItem>? = null
    private var documents: List<DynalistItem>? = null

    companion object {
        const val EXTRA_ITEM_TEXT = "EXTRA_ITEM_TEXT"
        private const val MAX_SUBMENU_ITEMS = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)
        setSupportActionBar(toolbar)
        window.allowEnterTransitionOverlap = true

        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        drawer_layout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerStateChanged(newState: Int) {}
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerOpened(drawerView: View) {
                inputMethodManager.hideSoftInputFromWindow(drawerView.windowToken, 0)
            }
        })
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)

        val itemsModel = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        val fragmentModel = ViewModelProviders.of(this).get(ItemListFragmentViewModel::class.java)

        itemsModel.bookmarksLiveData.observe(this, Observer<List<DynalistItem>> { inboxes ->
            this.inboxes = inboxes
            nav_view.menu.findItem(R.id.submenu_bookmarks_list).subMenu.run {
                fillMenuWithItems(inboxes, R.id.group_bookmarks_list, 0)
                if (inboxes.size <= 1) {
                    add(Menu.NONE, R.id.menu_item_bookmarks_hint, 1, R.string.add_inbox).apply {
                        isCheckable = false
                    }
                }
            }
        })
        itemsModel.documentsLiveData.observe(this, Observer<List<DynalistItem>> { docs ->
            this.documents = docs
            nav_view.menu.findItem(R.id.submenu_documents_list).subMenu.run {
                fillMenuWithItems(docs, R.id.group_documents_list, 1)
            }
        })
        fragmentModel.selectedDynalistItem.observe(this, Observer { item ->
            inboxes?.run {
                val menu = nav_view.menu.findItem(R.id.submenu_bookmarks_list).subMenu
                updateCheckedBookmark(menu, item, this)
            }
            documents?.run {
                val menu = nav_view.menu.findItem(R.id.submenu_documents_list).subMenu
                updateCheckedBookmark(menu, item, this)
            }
        })

        if (savedInstanceState == null) {
            val parent = intent.extras?.let { dynalist.resolveItemInBundle(it) } ?: dynalist.inbox
            val itemText = intent.getCharSequenceExtra(EXTRA_ITEM_TEXT) ?: ""
            val fragment = ItemListFragment.newInstance(parent, itemText)
            supportFragmentManager.beginTransaction()
                    .add(R.id.fragment_container, fragment).commit()
        }
    }

    private fun SubMenu.fillMenuWithItems(items: List<DynalistItem>, groupId: Int, menuIndex: Int) {
        clear()
        items.forEachIndexed { i, item ->
            val itemId = i + menuIndex * MAX_SUBMENU_ITEMS
            add(groupId, itemId, i, item.shortenedName).apply {
                isCheckable = true
            }
        }
        ViewModelProviders.of(this@NavigationActivity)
                .get(ItemListFragmentViewModel::class.java).let { fragmentModel ->
                    fragmentModel.selectedDynalistItem.value?.let {
                        updateCheckedBookmark(this, it, items)
                    }
                }
    }

    private fun updateCheckedBookmark(menu: Menu, item: DynalistItem, itemList: List<DynalistItem>) {
        val itemIndex = itemList.indexOf(item)
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
            openDynalistItem(inboxes!![item.itemId])
        } else if (item.groupId == R.id.group_documents_list) {
            openDynalistItem(documents!![item.itemId % MAX_SUBMENU_ITEMS])
        }
        when(item.itemId) {
            R.id.open_quick_dialog -> fixedFinishAfterTransition()
            R.id.menu_item_bookmarks_hint -> alert {
                messageResource = R.string.bookmark_hint
                okButton { dynalist.sync() }
                show()
            }
            R.id.send_bug_report -> sendBugReport()
            R.id.open_settings -> openSettings()
            R.id.share_quickdynalist -> shareQuickDynalist()
            R.id.rate_quickdynalist -> rateQuickDynalist()
            R.id.action_sync_now -> SyncJob.forceSync()
        }
        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun openSettings() {
        Intent(this, SettingsActivity::class.java).apply {
            startActivity(this)
        }
    }

    private fun rateQuickDynalist() {
        val uri = Uri.parse("market://details?id=" + BuildConfig.APPLICATION_ID)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            val browserUri = Uri.parse("https://play.google.com/store/apps/details?id="
                    + BuildConfig.APPLICATION_ID)
            startActivity(Intent(Intent.ACTION_VIEW, browserUri))
        }
    }

    private fun shareQuickDynalist() {
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_quickdynalist_text))
            type = "text/plain"
            startActivity(Intent.createChooser(this, getString(R.string.share_quickdynalist)))
        }
    }

    private fun sendBugReport(): Boolean {
        // save logcat in file
        val logsPath = File(cacheDir, "logs-cache")
        logsPath.mkdir()
        val outputFile = logsPath.resolve("quick-dynalist-logs.txt")
        try {
            Runtime.getRuntime().exec( "logcat -f " + outputFile.absolutePath)

            val logUri = FileProvider.getUriForFile(this,
                    "com.louiskirsch.quickdynalist.fileprovider", outputFile)
            Intent(Intent.ACTION_SEND).apply {
                type = "vnd.android.cursor.dir/email"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.bug_report_email)))
                putExtra(Intent.EXTRA_STREAM, logUri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                val subject = getString(R.string.bug_report_subject, BuildConfig.VERSION_NAME)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                val intentTitle = getString(R.string.bug_report_intent_title)
                startActivity(Intent.createChooser(this, intentTitle))
            }
        } catch (e: Exception) {
            toast(R.string.error_log_collection)
        }
        return true
    }

    private fun openDynalistItem(itemToOpen: DynalistItem) {
        val currentFragment = supportFragmentManager
                .findFragmentById(R.id.fragment_container) as ItemListFragment
        val itemText = currentFragment.itemContents.text
        val fragment = ItemListFragment.newInstance(itemToOpen, itemText.toString())
        supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        itemText.clear()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onItemEvent(event: ItemEvent) {
        if (!event.success)
            toast(R.string.error_update_server)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDynalistLocateEvent(event: DynalistLocateEvent) {
        openDynalistItem(event.item)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onSyncEvent(event: SyncEvent) {
        when (event.status) {
            SyncStatus.RUNNING -> nav_view.menu.findItem(R.id.action_sync_now).apply {
                isEnabled = false
                setTitle(R.string.sync_in_progress)
            }
            SyncStatus.NOT_RUNNING -> nav_view.menu.findItem(R.id.action_sync_now).apply {
                isEnabled = true
                setTitle(R.string.action_sync_now)
            }
            else -> Unit
        }
    }
}
