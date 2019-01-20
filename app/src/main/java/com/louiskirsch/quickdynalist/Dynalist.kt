package com.louiskirsch.quickdynalist

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.util.EventLog
import android.view.LayoutInflater
import android.widget.EditText
import com.google.gson.Gson
import com.louiskirsch.quickdynalist.jobs.AddItemJob
import com.louiskirsch.quickdynalist.jobs.Bookmark
import com.louiskirsch.quickdynalist.jobs.BookmarksJob
import com.louiskirsch.quickdynalist.jobs.VerifyTokenJob
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.*
import org.jetbrains.anko.custom.ankoView
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class Dynalist(private val context: Context) {
    val gson: Gson = Gson()
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

    var bookmarks: Array<Bookmark>
        get() {
            return preferences.getString("BOOKMARKS", null)?.let {
                gson.fromJson(it, Array<Bookmark>::class.java)
            } ?: arrayOf(Bookmark.newInbox())
        }
        set(x) {
            val editor = preferences.edit()
            editor.putString("BOOKMARKS", gson.toJson(x))
            editor.putLong("BOOKMARK_UPDATE", Date().time)
            editor.apply()
        }

    private val lastBookmarkQuery: Date
        get() = Date(preferences.getLong("BOOKMARK_UPDATE", 0))

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

    fun addItem(contents: String, parent: Bookmark, note: String = "") {
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
}