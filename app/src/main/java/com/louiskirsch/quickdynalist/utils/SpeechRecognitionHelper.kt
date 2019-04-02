package com.louiskirsch.quickdynalist.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import com.louiskirsch.quickdynalist.R

class SpeechRecognitionHelper {

    fun startSpeechRecognition(activity: Activity) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        val speechRecognitionRequestCode =
                activity.resources.getInteger(R.integer.request_code_speech_recognition)
        activity.startActivityForResult(intent, speechRecognitionRequestCode)
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