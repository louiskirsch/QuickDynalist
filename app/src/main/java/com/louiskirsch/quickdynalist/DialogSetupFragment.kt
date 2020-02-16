package com.louiskirsch.quickdynalist


import android.app.Activity
import android.app.TaskStackBuilder
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.louiskirsch.quickdynalist.utils.toBitmap
import kotlinx.android.synthetic.main.activity_shortcut.*
import kotlinx.android.synthetic.main.fragment_dialog_setup.*

class DialogSetupFragment : Fragment() {

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
    }

    private fun createShortcut() {
        val id = "shortcut-quick-dialog"
        val shortcutInfo = ShortcutInfoCompat.Builder(context!!, id).run {
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
