package com.louiskirsch.quickdynalist

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.louiskirsch.quickdynalist.jobs.AddItemJob
import com.louiskirsch.quickdynalist.jobs.SyncJob
import com.louiskirsch.quickdynalist.jobs.VerifyTokenJob
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItemFilter
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.*
import java.io.File
import java.util.*

class Dynalist(private val context: Context) {
    private var isSyncing: Boolean = false

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences("PREFERENCES", Context.MODE_PRIVATE)

    private val settings: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(context)

    private val syncFrequency
        get() = settings.getInt("sync_frequency", 5)

    val displayChildrenCount
        get() = settings.getString("display_children_count", "5")!!.toInt()

    val displayChildrenDepth
        get() = settings.getString("display_children_depth", "0")!!.toInt()

    val displayLinksInline
        get() = settings.getBoolean("display_links_inline", false)

    val addToTopOfList
        get() = settings.getString("edit_insert_position", "bottom") == "top"

    val insertPosition
        get() = if (addToTopOfList) 0 else -1

    val shouldDetectTags
        get() = settings.getBoolean("edit_detect_tags", true)

    val speechAutoSubmit
        get() = settings.getBoolean("edit_speech_auto_submit", false)

    val preferredTheme: Int get() {
        val default = context.resources.getInteger(R.integer.pref_default_display_theme)
        return settings.getString("display_theme", null)?.toInt() ?: default
    }

    var syncMobileData: Boolean
        get() = settings.getBoolean("sync_mobile_data", false)
        set(value) = settings.edit().putBoolean("sync_mobile_data", value).apply()

    var token: String?
        get() = preferences.getString("TOKEN", "NONE")
        set(newToken) = preferences.edit().putString("TOKEN", newToken).apply()

    val isAuthenticated: Boolean
        get() = this.preferences.contains("TOKEN")

    var preferencesVersion: Int
        get() = preferences.getInt("PREFS_VERSION", 1)
        set(version) = preferences.edit().putInt("PREFS_VERSION", version).apply()

    var lastFullSync: Date
        get() = Date(preferences.getLong("BOOKMARK_UPDATE", 0))
        set(lastQuery) = preferences.edit().putLong("BOOKMARK_UPDATE", lastQuery.time).apply()

    var notifiedDynalistImageSetting: Boolean
        get() = preferences.getBoolean("IMAGE_SETTING_NOTIFICATION", false)
        set(value) = preferences.edit().putBoolean("IMAGE_SETTING_NOTIFICATION", value).apply()

    private val itemBox: Box<DynalistItem>
        get() = DynalistApp.instance.boxStore.boxFor()

    private val filterBox: Box<DynalistItemFilter>
        get() = DynalistApp.instance.boxStore.boxFor()

    val inbox: DynalistItem?
        get() = itemBox.query { equal(DynalistItem_.isInbox, true) } .findFirst()

    fun subscribe() {
        EventBus.getDefault().register(this)

        val syncRequired = lastFullSync.time < Date().time - syncFrequency * 60 * 1000L
        if (isAuthenticated && syncRequired) {
            sync()
        }
    }

    fun unsubscribe() {
        EventBus.getDefault().unregister(this)
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAuthenticationEvent(event: AuthenticatedEvent) {
        if (!event.success) {
            authenticate()
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onInboxEvent(event: InboxEvent) {
        if (!event.configured) {
            authenticate(configureOnlyInbox = true)
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onSyncEvent(event: SyncEvent) {
        when (event.status) {
            SyncStatus.RUNNING -> isSyncing = true
            SyncStatus.NOT_RUNNING -> isSyncing = false
            SyncStatus.SUCCESS -> if (event.isManual) {
                context.toast(R.string.alert_sync_success)
            }
            SyncStatus.NO_SUCCESS -> context.toast(R.string.alert_sync_no_success)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onRateLimitDelay(event: RateLimitDelay) {
        context.toast(R.string.alert_rate_limit_delay)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onForbiddenImageEvent(event: ForbiddenImageEvent) {
        if (notifiedDynalistImageSetting)
            return
        context.alert {
            titleResource = R.string.dialog_title_image_setting
            messageResource = R.string.dialog_message_image_setting
            okButton {}
            show()
        }
        notifiedDynalistImageSetting = true
    }

    fun addItem(contents: String, parent: DynalistItem, note: String = "") {
        val jobManager = DynalistApp.instance.jobManager
        val job = AddItemJob(contents, note, parent)
        jobManager.addJobInBackground(job)
    }

    fun sync(isManual: Boolean = false) {
        if (isSyncing || !settings.getBoolean("sync_automatic", true))
            return
        val job = SyncJob(!syncMobileData, isManual)
        DynalistApp.instance.jobManager.addJobInBackground(job)
    }

    fun createAuthenticationIntent(configureOnlyInbox: Boolean = false): Intent {
        return Intent(context, WizardActivity::class.java).apply {
            putExtra(WizardActivity.EXTRA_CONFIG_INBOX_ONLY, configureOnlyInbox)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }

    fun authenticate(configureOnlyInbox: Boolean = false) {
        context.startActivity(createAuthenticationIntent(configureOnlyInbox))
    }

    fun resolveFilterInBundle(bundle: Bundle): DynalistItemFilter? {
        if (bundle.containsKey(DynalistApp.EXTRA_DISPLAY_FILTER))
            return bundle.getParcelable(DynalistApp.EXTRA_DISPLAY_FILTER) as DynalistItemFilter
        if (bundle.containsKey(DynalistApp.EXTRA_DISPLAY_FILTER_ID)) {
            val clientId = bundle.getLong(DynalistApp.EXTRA_DISPLAY_FILTER_ID)
            val item = filterBox.get(clientId)
            if (item == null && bundle.getBoolean(DynalistApp.EXTRA_FROM_SHORTCUT, false))
                context.toast(R.string.error_invalid_shortcut)
            return item
        }
        return null
    }

    fun resolveItemInBundle(bundle: Bundle): DynalistItem? {
        if (bundle.containsKey(DynalistApp.EXTRA_DISPLAY_ITEM))
            return bundle.getParcelable(DynalistApp.EXTRA_DISPLAY_ITEM) as DynalistItem
        if (bundle.containsKey(DynalistApp.EXTRA_DISPLAY_ITEM_ID)) {
            val clientId = bundle.getLong(DynalistApp.EXTRA_DISPLAY_ITEM_ID)
            val item = itemBox.get(clientId)
            if (item == null && bundle.getBoolean(DynalistApp.EXTRA_FROM_SHORTCUT, false))
                context.toast(R.string.error_invalid_shortcut)
            return item
        }
        return null
    }

    fun sendBugReport(): Boolean {
        val logsPath = File(context.cacheDir, "logs-cache")
        logsPath.mkdir()
        val outputFile = logsPath.resolve("quick-dynalist-logs.txt")
        try {
            Runtime.getRuntime().exec( "logcat -f " + outputFile.absolutePath)

            val logUri = FileProvider.getUriForFile(context,
                    "com.louiskirsch.quickdynalist.fileprovider", outputFile)
            Intent(Intent.ACTION_SEND).apply {
                type = "vnd.android.cursor.dir/email"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.bug_report_email)))
                putExtra(Intent.EXTRA_STREAM, logUri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                val subject = context.getString(R.string.bug_report_subject, BuildConfig.VERSION_NAME)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                val intentTitle = context.getString(R.string.bug_report_intent_title)
                context.startActivity(Intent.createChooser(this, intentTitle))
            }
        } catch (e: Exception) {
            context.toast(R.string.error_log_collection)
        }
        return true
    }
}