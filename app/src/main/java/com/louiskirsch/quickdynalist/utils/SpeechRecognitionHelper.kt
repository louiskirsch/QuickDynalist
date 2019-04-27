package com.louiskirsch.quickdynalist.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.fragment.app.Fragment
import com.louiskirsch.quickdynalist.R
import org.jetbrains.anko.toast

class SpeechRecognitionHelper {

    fun startSpeechRecognition(activity: Activity) {
        startSpeechRecognition(activity, activity::startActivityForResult)
    }

    fun startSpeechRecognition(fragment: Fragment) {
        startSpeechRecognition(fragment.context!!, fragment::startActivityForResult)
    }

    private fun startSpeechRecognition(context: Context, starter: (Intent, Int) -> Unit) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        val speechRecognitionRequestCode =
                context.resources.getInteger(R.integer.request_code_speech_recognition)
        try {
            starter(intent, speechRecognitionRequestCode)
        } catch (e: ActivityNotFoundException) {
            context.toast(R.string.error_speech_recognition_not_available)
        }
    }

    fun dispatchResult(context:Context, requestCode: Int, resultCode: Int, data: Intent?,
                       cancelCallback: (() -> Unit)? = null, callback: (String) -> Unit) {
        val speechRecognitionRequestCode =
                context.resources.getInteger(R.integer.request_code_speech_recognition)
        if (requestCode == speechRecognitionRequestCode) {
            if (resultCode == Activity.RESULT_OK) {
                val spokenText = data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        .let { it[0] }
                callback(spokenText)
            } else {
                cancelCallback?.invoke()
            }
        }
    }
}