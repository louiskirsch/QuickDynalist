package com.louiskirsch.quickdynalist

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.view.LayoutInflater
import android.widget.EditText
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.jetbrains.anko.toast
import org.json.JSONException
import org.json.JSONObject

class Dynalist(private val context: Context) {
    private val queue = Volley.newRequestQueue(context)
    var authDialog: AlertDialog? = null

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences("PREFERENCES", Context.MODE_PRIVATE)

    private val token: String?
        get() = preferences.getString("TOKEN", "NONE")

    val isAuthenticated: Boolean
        get() = this.preferences.contains("TOKEN")

    fun addItem(contents: String, parent: String = "", showToastOnSuccess: Boolean = false) {
        // TODO addd item not to inbox but to `parent`
        val payload: JSONObject
        try {
            payload = createRequestPayload(null)
            payload.put("content", contents)
        } catch (e: JSONException) {
            e.printStackTrace()
            return
        }

        val request = JsonObjectRequest(
                Request.Method.POST,
                "https://dynalist.io/api/v1/inbox/add",
                payload,
                Response.Listener { response ->
                    try {
                        val code = response.getString("_code")
                        if (code == "Ok") {
                            if (showToastOnSuccess) {
                                showItemAddSuccess()
                            }
                        } else if (code == "InvalidToken") {
                            showItemAddError()
                            authenticate()
                        } else {
                            showItemAddError()
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                        showItemAddError()
                    }
                },
                Response.ErrorListener { error ->
                    error.printStackTrace()
                    showItemAddError()
                })
        queue!!.add(request)
    }

    private fun showItemAddSuccess() {
        context.toast(R.string.add_item_success)
    }

    private fun createRequestPayload(token: String?): JSONObject {
        val payload = JSONObject()
        payload.put("token", token ?: this.token)
        return payload
    }

    fun validateToken(token: String, onDone: () -> Unit) {
        val payload: JSONObject
        try {
            payload = createRequestPayload(token)
        } catch (e: JSONException) {
            e.printStackTrace()
            showValidationError()
            return
        }

        val request = JsonObjectRequest(
                Request.Method.POST,
                "https://dynalist.io/api/v1/file/list",
                payload,
                Response.Listener { response ->
                    try {
                        if (response.getString("_code") == "Ok") {
                            saveToken(token)
                        } else {
                            showValidationError()
                        }
                    } catch (e: JSONException) {
                        showValidationError()
                    }

                    onDone()
                },
                Response.ErrorListener {
                    showValidationError()
                    onDone()
                })
        queue.add(request)
    }

    private fun showItemAddError() {
        context.toast(R.string.add_item_error)
    }

    private fun showValidationError() {
        context.toast(R.string.token_invalid)
        authenticate()
    }

    fun saveToken(token: String) {
        preferences.edit().putString("TOKEN", token).apply()
    }

    fun authenticate(onDone: () -> Unit = {}) {
        val builder = AlertDialog.Builder(context)
        builder.setMessage(R.string.auth_instructions)
                .setCancelable(false)
                .setPositiveButton(R.string.auth_start) { _, _ ->
                    showTokenDialog(onDone)
                    openTokenGenerationBrowser()
                }

        builder.create().show()
    }

    private fun showTokenDialog(onDone: () -> Unit) {
        val builder = AlertDialog.Builder(context)
        val view = LayoutInflater.from(context).inflate(R.layout.activity_auth, null)
        builder.setMessage(R.string.auth_copy_instructions)
                .setCancelable(false)
                .setView(view)
                .setPositiveButton(R.string.auth_accept_token) { dialogInterface, i ->
                    val tokenField = view.findViewById<EditText>(R.id.auth_token)
                    val token = tokenField.text.toString()
                    validateToken(token, onDone)
                }
                .setOnDismissListener {
                    authDialog = null
                    onDone()
                }
        authDialog = builder.create()
        authDialog!!.show()
    }

    private fun openTokenGenerationBrowser() {
        val url = "https://dynalist.io/developer"
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(browserIntent)
    }
}