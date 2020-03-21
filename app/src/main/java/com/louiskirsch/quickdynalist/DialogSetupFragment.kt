package com.louiskirsch.quickdynalist


import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.louiskirsch.quickdynalist.utils.shortcutManager
import kotlinx.android.synthetic.main.fragment_dialog_setup.*

class DialogSetupFragment : Fragment() {

    companion object {
        private const val SHORTCUT_ID = "shortcut-quick-dialog"

        private fun getPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences("dialog_setup", Context.MODE_PRIVATE)
        }

        fun wasShownBefore(context: Context): Boolean {
            val preferences = getPreferences(context)
            val shown = preferences.getBoolean("shown_before", false)
            if (!shown && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val pinned = context.shortcutManager?.let { mgr ->
                    mgr.pinnedShortcuts.any {
                        it.id in listOf(SHORTCUT_ID, "quick_dialog")
                    }
                } ?: false
                if (pinned)
                    setShown(context)
                return pinned
            }
            return shown
        }

        private fun setShown(context: Context) {
            getPreferences(context).edit().putBoolean("shown_before", true).apply()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dialog_setup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog_setup_video.apply {
            setOnPreparedListener {
                it.isLooping = true
            }
            val uriString = "android.resource://${context!!.packageName}/${R.raw.dialog_tutorial}"
            setVideoURI(Uri.parse(uriString))
            start()
        }
        create_dialog_button.setOnClickListener {
            createShortcut()
            activity!!.finishAfterTransition()
        }
        no_dialog_button.setOnClickListener {
            activity!!.finishAfterTransition()
        }
        setShown(context!!)
    }

    private fun createShortcut() {
        val shortcutInfo = ShortcutInfoCompat.Builder(context!!, SHORTCUT_ID).run {
            setAlwaysBadged()
            setIntents(arrayOf(Intent().apply {
                action = "com.louiskirsch.quickdynalist.SHOW_DIALOG"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }))
            setShortLabel(getString(R.string.shortcut_quick_dialog))
            setIcon(IconCompat.createWithResource(context, R.mipmap.ic_shortcut_quick_dialog))
            setDisabledMessage(getString(R.string.error_disabled_shortcut))
            build()
        }
        ShortcutManagerCompat.requestPinShortcut(context!!, shortcutInfo, null)
    }

}
