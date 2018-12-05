package com.louiskirsch.quickdynalist

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONException
import org.json.JSONObject

class MainActivity : Activity() {

    private val authStartListener = DialogInterface.OnClickListener { dialogInterface, i ->
        showTokenDialog()
        openTokenGenerationBrowser()
    }

    private var authDialog: AlertDialog? = null
    private var queue: RequestQueue? = null
    private var itemLocation: Spinner? = null
    private var itemContents: EditText? = null
    private var submitButton: Button? = null

    private val preferences: SharedPreferences
        get() = this.getSharedPreferences("PREFERENCES", Context.MODE_PRIVATE)

    private val token: String?
        get() = preferences.getString("TOKEN", "NONE")

    private val isAuthenticated: Boolean
        get() = this.preferences.contains("TOKEN")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        itemLocation = findViewById(R.id.parent_spinner)
        itemContents = findViewById(R.id.item_content)
        submitButton = findViewById(R.id.btn_add_item)

        // These properties must be set programmatically because order of execution matters
        itemContents!!.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        itemContents!!.setSingleLine(true)
        itemContents!!.maxLines = 5
        itemContents!!.setHorizontallyScrolling(false)
        itemContents!!.imeOptions = EditorInfo.IME_ACTION_DONE
        itemContents!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

            override fun afterTextChanged(editable: Editable) {
                updateSubmitEnabled()
            }
        })
        itemContents!!.setOnEditorActionListener(TextView.OnEditorActionListener { textView, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitButton!!.performClick()
                return@OnEditorActionListener true
            }
            false
        })

        submitButton!!.setOnClickListener {
            addItem(itemLocation!!.selectedItem.toString(),
                    itemContents!!.text.toString())
            itemContents!!.text.clear()
        }

        val adapter = ArrayAdapter.createFromResource(this,
                R.array.item_locations, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        itemLocation!!.adapter = adapter

        queue = Volley.newRequestQueue(this)
    }

    override fun onResume() {
        super.onResume()
        updateSubmitEnabled()
        if (!isAuthenticated && authDialog == null) {
            authenticate()
        } else {
            itemContents!!.requestFocus()
        }
    }

    private fun updateSubmitEnabled() {
        val enabled = isAuthenticated && !itemContents!!.text.toString().isEmpty()
        submitButton!!.isEnabled = enabled
    }

    private fun addItem(parent: String, contents: String) {
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
                            // All OK, pass
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

    private fun showItemAddError() {
        Toast.makeText(this@MainActivity,
                R.string.add_item_error, Toast.LENGTH_SHORT).show()
    }

    private fun showTokenDialog() {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.activity_auth, null)
        val tokenAcceptListener = DialogInterface.OnClickListener { dialogInterface, i ->
            val tokenField = view.findViewById<EditText>(R.id.auth_token)
            val token = tokenField.text.toString()
            validateToken(token)
        }
        builder.setMessage(R.string.auth_copy_instructions)
                .setCancelable(false)
                .setView(view)
                .setPositiveButton(R.string.auth_accept_token, tokenAcceptListener)
                .setOnDismissListener { authDialog = null }
        authDialog = builder.create()
        authDialog!!.show()
    }

    private fun openTokenGenerationBrowser() {
        val url = "https://dynalist.io/developer"
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
    }

    private fun showValidationError() {
        Toast.makeText(this, R.string.token_invalid, Toast.LENGTH_SHORT).show()
        authenticate()
    }

    private fun saveToken(token: String) {
        preferences.edit().putString("TOKEN", token).apply()
    }

    private fun validateToken(token: String) {
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
                },
                Response.ErrorListener { showValidationError() })
        queue!!.add(request)
    }

    private fun createRequestPayload(token: String?): JSONObject {
        val payload = JSONObject()
        payload.put("token", token ?: this.token)
        return payload
    }

    private fun authenticate() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.auth_instructions)
                .setCancelable(false)
                .setPositiveButton(R.string.auth_start, authStartListener)
        builder.create().show()
    }
}
