package com.louiskirsch.quickdynalist

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import android.webkit.URLUtil
import org.jetbrains.anko.alert
import org.jetbrains.anko.toast
import androidx.appcompat.app.AppCompatActivity
import android.view.Gravity
import android.view.WindowManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.louiskirsch.quickdynalist.network.UploadFileRequest
import com.louiskirsch.quickdynalist.network.UploadResponse
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.utils.SpeechRecognitionHelper
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.util.*


class ProcessTextActivity : AppCompatActivity() {
    private val dynalist: Dynalist = Dynalist(this)
    private val speechRecognitionHelper = SpeechRecognitionHelper()
    private var text: List<String>? = null
    private var location: DynalistItem? = null
    private var bookmarks: List<DynalistItem> = emptyList()
    private var afterAuthentication: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        location = dynalist.inbox
        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.targetLocationsLiveData.observe(this, Observer<List<DynalistItem>> {
            bookmarks = it
        })

        if (savedInstanceState == null) {
            val action = intent.action
            when {
                action == getString(R.string.ACTION_RECORD_SPEECH) -> {
                    speechRecognitionHelper.startSpeechRecognition(this)
                }
                isUploadIntent && action == Intent.ACTION_SEND -> {
                    ensureAuthenticated { uploadSingle() }
                }
                isUploadIntent && action == Intent.ACTION_SEND_MULTIPLE -> {
                    ensureAuthenticated { uploadMultiple() }
                }
                else -> {
                    text = deriveText()
                    if (text == null) {
                        finishWithError()
                    } else {
                        ensureAuthenticated { showSnackbar() }
                    }
                }
            }
        }
    }

    private val isUploadIntent: Boolean get() {
        val action = intent.action
        val type = intent.type
        return (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE)
                && type != null
                && (type.startsWith("image/") || type.startsWith("video/")
                || type == "application/pdf")
    }

    private fun uploadUri(uri: Uri): Upload? {
        val service = DynalistApp.instance.dynalistService
        val token = dynalist.token
        val type = intent.type
        val data = contentResolver.openInputStream(uri)?.buffered()?.use { it.readBytes() }
        val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        }
        if (type == null || data == null || fileName == null || token == null) {
            return null
        }
        val encodingFlags = Base64.NO_PADDING or Base64.NO_WRAP
        val base64Data = Base64.encodeToString(data, encodingFlags)
        val request = UploadFileRequest(fileName, type, base64Data, token)
        return service.uploadFile(request).execute().body()?.let { Upload(request, it) }
    }

    private fun formatUpload(upload: Upload): String {
        val prefix = if (upload.request.content_type.startsWith("image/")) "!" else ""
        val filename = upload.request.filename
        val url = upload.response.url
        return "$prefix[$filename]($url)"
    }

    private fun uploadSingle() {
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val uploadingBar = showUploadingSnackbar()
        doAsync {
            val upload = uploadUri(uri)
            uiThread {
                uploadingBar.dismiss()
                if (handleUploadError(upload)) {
                    text = listOf(formatUpload(upload!!))
                    showSnackbar()
                }
            }
        }
    }

    private fun handleUploadError(upload: Upload?): Boolean {
        return when {
            upload == null -> {
                finishWithError()
                false
            }
            upload.response.isProNeeded -> {
                finishWithError(R.string.upload_error_pro_needed)
                false
            }
            upload.response.isQuotaExceeded -> {
                finishWithError(R.string.upload_error_quota_exceeded)
                false
            }
            upload.response.isOK -> true
            else -> {
                finishWithError()
                false
            }
        }

    }

    private fun uploadMultiple() {
        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        val uploadingBar = showUploadingSnackbar()
        doAsync {
            val uploads = LinkedList<Upload>()
            for (uri in uris) {
                val upload = uploadUri(uri)
                if (!handleUploadError(upload))
                    return@doAsync
                uploads.add(upload!!)
            }
            uiThread {
                uploadingBar.dismiss()
                text = uploads.map { formatUpload(it) }
                showSnackbar()
            }
        }
    }

    private fun showUploadingSnackbar(): Snackbar {
        return Snackbar.make(window.decorView, R.string.status_uploading,
                Snackbar.LENGTH_INDEFINITE).apply { show() }
    }

    private fun finishWithError(error: Int? = null) {
        toast(error ?: R.string.invalid_intent_error)
        finish()
    }

    private fun deriveText(): List<String>? {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                ?: return null
        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)
        return if (subject != null && URLUtil.isNetworkUrl(text))
            listOf("[$subject]($text)")
        else
            text.split('\n')
    }

    private fun ensureAuthenticated(callback: () -> Unit) {
        if (!dynalist.isAuthenticated || location == null) {
            afterAuthentication = callback
            val configureOnlyInbox = dynalist.isAuthenticated
            startActivityForResult(dynalist.createAuthenticationIntent(configureOnlyInbox),
                    resources.getInteger(R.integer.request_code_authenticate))
        } else {
            callback()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val lp = (window.decorView.layoutParams as WindowManager.LayoutParams).apply {
            gravity = Gravity.FILL_HORIZONTAL or Gravity.BOTTOM
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        windowManager.updateViewLayout(window.decorView, lp)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        speechRecognitionHelper.dispatchResult(this,
                requestCode, resultCode, data, ::finish) {
            text = it.split(". ")
            ensureAuthenticated { showSnackbar() }
        }
        when (requestCode) {
            resources.getInteger(R.integer.request_code_authenticate) -> {
                if (resultCode == Activity.RESULT_OK) {
                    if (location == null)
                        location = dynalist.inbox
                    afterAuthentication?.invoke()
                    afterAuthentication = null
                } else {
                    finishWithError()
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing && text != null && location != null)
            addItemsToDynalist()
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && location != null) {
            addItemsToDynalist()
            finish()
        }
    }

    private fun addItemsToDynalist() {
        text?.forEach { dynalist.addItem(it, location!!) }
    }

    private fun showSnackbar() {
        Snackbar.make(window.decorView, R.string.add_item_success, Snackbar.LENGTH_SHORT).apply {
            setAction(R.string.item_change_target_location) {
                alert {
                    items(bookmarks) { _, selectedLocation: DynalistItem, _ ->
                        location = selectedLocation
                        finish()
                    }
                    onCancelled { finish() }
                    show()
                }
            }
            addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event != DISMISS_EVENT_ACTION)
                        finish()
                }
            })
            show()
        }
    }

    private class Upload(val request: UploadFileRequest, val response: UploadResponse)
}
