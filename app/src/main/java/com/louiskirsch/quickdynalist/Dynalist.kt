package com.louiskirsch.quickdynalist

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
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
import java.util.*

class Dynalist(private val context: Context) {
    var authDialog: AlertDialog? = null
    var errorDialogShown: Boolean = false
    private var isSyncing: Boolean = false

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences("PREFERENCES", Context.MODE_PRIVATE)

    private val settings: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(context)

    private val syncFrequency
        get() = settings.getInt("sync_frequency", 5)

   val displayChildrenCount
        get() = settings.getString("display_children_count", "5")!!.toInt()

    val shouldDetectTags
        get() = settings.getBoolean("edit_detect_tags", true)

    val speechAutoSubmit
        get() = settings.getBoolean("edit_speech_auto_submit", false)

    var token: String?
        get() = preferences.getString("TOKEN", "NONE")
        set(newToken) = preferences.edit().putString("TOKEN", newToken).apply()

    val isAuthenticated: Boolean
        get() = this.preferences.contains("TOKEN")

    val isAuthenticating: Boolean
        get() = authDialog != null

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

    val inbox: DynalistItem
        get() = itemBox.query { equal(DynalistItem_.isInbox, true) } .findFirst()!!

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
            context.toast(R.string.token_invalid)
            authenticate()
        } else {
            val job = SyncJob(requireUnmeteredNetwork = false, isManual = true)
            DynalistApp.instance.jobManager.addJobInBackground(job)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNoInboxEvent(event: NoInboxEvent) {
        if (errorDialogShown)
            return

        errorDialogShown = true
        context.alert {
            messageResource = R.string.no_inbox_configured
            titleResource = R.string.dialog_title_error
            onCancelled {
                errorDialogShown = false
            }
            positiveButton(android.R.string.ok) {
                errorDialogShown = false
            }
        }.show()
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
        if (!settings.getBoolean("sync_automatic", true))
            return
        if (!isManual && !settings.contains("sync_mobile_data")) {
            context.alert {
                isCancelable = false
                titleResource = R.string.dialog_title_sync_mobile_data
                messageResource = R.string.dialog_message_sync_mobile_data
                positiveButton(R.string.confirm_sync_mobile_data) {
                    settings.edit().putBoolean("sync_mobile_data", true).apply()
                    sync(isManual)
                }
                negativeButton(R.string.confirm_no_sync_mobile_data) {
                    settings.edit().putBoolean("sync_mobile_data", false).apply()
                    sync(isManual)
                }
                show()
            }
            return
        }
        if (isSyncing) return
        val syncMobileData = settings.getBoolean("sync_mobile_data", false)
        val job = SyncJob(!syncMobileData, isManual)
        DynalistApp.instance.jobManager.addJobInBackground(job)
    }

    fun authenticate() {
        if (isAuthenticating)
            return

        context.alert(R.string.auth_instructions) {
            isCancelable = false
            positiveButton(R.string.auth_start) {
                showTokenDialog()
                openTokenGenerationBrowser()
            }
        }.show()
    }

    private fun showTokenDialog() {
        val builder = AlertDialog.Builder(context)
        val view = LayoutInflater.from(context).inflate(R.layout.activity_auth, null)
        builder.setMessage(R.string.auth_copy_instructions)
                .setCancelable(false)
                .setView(view)
                .setPositiveButton(R.string.auth_accept_token) { _, _ ->
                    val tokenField = view.findViewById<EditText>(R.id.auth_token)
                    val token = tokenField.text.toString()
                    val jobManager = DynalistApp.instance.jobManager
                    val job = VerifyTokenJob(token)
                    authDialog = null
                    jobManager.addJobInBackground(job)
                }
                .setOnDismissListener {
                    authDialog = null
                }
        authDialog = builder.create()
        authDialog!!.show()
    }

    private fun openTokenGenerationBrowser() {
        context.browse("https://dynalist.io/developer")
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
}