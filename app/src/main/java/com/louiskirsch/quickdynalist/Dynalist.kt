package com.louiskirsch.quickdynalist

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import com.louiskirsch.quickdynalist.jobs.AddItemJob
import com.louiskirsch.quickdynalist.jobs.BookmarksJob
import com.louiskirsch.quickdynalist.jobs.VerifyTokenJob
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
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

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences("PREFERENCES", Context.MODE_PRIVATE)

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

    var lastBookmarkQuery: Date
        get() = Date(preferences.getLong("BOOKMARK_UPDATE", 0))
        set(lastQuery) = preferences.edit().putLong("BOOKMARK_UPDATE", lastQuery.time).apply()

    private val itemBox: Box<DynalistItem>
        get() = DynalistApp.instance.boxStore.boxFor()

    val inbox: DynalistItem
        get() = itemBox.query { equal(DynalistItem_.isInbox, true) } .findFirst()!!

    fun subscribe() {
        EventBus.getDefault().register(this)

        val bookmarksOutdated = lastBookmarkQuery.time < Date().time - 60 * 1000L
        if (isAuthenticated && bookmarksOutdated) {
            val jobManager = DynalistApp.instance.jobManager
            jobManager.addJobInBackground(BookmarksJob())
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
            DynalistApp.instance.jobManager.addJobInBackground(BookmarksJob())
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSyncEvent(event: SyncEvent) {
        if (event.success && !event.requiredUnmeteredNetwork) {
            context.toast(R.string.alert_sync_success)
        }
    }

    fun addItem(contents: String, parent: DynalistItem, note: String = "") {
        val jobManager = DynalistApp.instance.jobManager
        val job = AddItemJob(contents, note, parent)
        jobManager.addJobInBackground(job)
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

    fun resolveItemInBundle(bundle: Bundle): DynalistItem? {
        if (bundle.containsKey(DynalistApp.EXTRA_DISPLAY_ITEM))
            return bundle.getParcelable(DynalistApp.EXTRA_DISPLAY_ITEM) as DynalistItem
        if (bundle.containsKey(DynalistApp.EXTRA_DISPLAY_ITEM_ID)) {
            // TODO query this asynchronously
            val clientId = bundle.getLong(DynalistApp.EXTRA_DISPLAY_ITEM_ID)
            val item = itemBox.get(clientId)
            if (item == null && bundle.getBoolean(DynalistApp.EXTRA_FROM_SHORTCUT, false))
                context.toast(R.string.error_invalid_shortcut)
            return item
        }
        return null
    }
}