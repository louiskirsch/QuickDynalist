package com.louiskirsch.quickdynalist


import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlinx.android.synthetic.main.fragment_sync_status.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class SyncStatusFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_sync_status, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sync_progress.progress = 0
        sync_progress.max = 100
        sync_done.alpha = 0f
        sync_failed.alpha = 0f
        bug_report_btn.setOnClickListener {
            Dynalist(context!!).sendBugReport()
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        if (Dynalist(context!!).lastFullSync.time > 0)
            onSyncEvent(SyncEvent(SyncStatus.SUCCESS, false))
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    private fun updateProgress(progress: Float) {
        val progressInt = (progress * sync_progress.max).toInt()
        ObjectAnimator.ofInt(sync_progress, "progress", progressInt).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            setAutoCancel(true)
            start()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSyncProgressEvent(event: SyncProgressEvent) {
        updateProgress(event.progress)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSyncEvent(event: SyncEvent) {
        when (event.status) {
            SyncStatus.SUCCESS -> {
                updateProgress(1f)
                sync_done.animate().apply {
                    alpha(1f)
                    duration = 800
                    interpolator = DecelerateInterpolator()
                    withEndAction {
                        Handler().postDelayed({nextWizardScreen()}, 800)
                    }
                    start()
                }
            }
            SyncStatus.NO_SUCCESS -> {
                updateProgress(1f)
                sync_failed.animate().apply {
                    alpha(1f)
                    duration = 800
                    interpolator = DecelerateInterpolator()
                    withEndAction {
                        bug_report_btn.visibility = View.VISIBLE
                    }
                    start()
                }
            }
            else -> {}
        }
    }

    private fun nextWizardScreen() {
        val dynalist = Dynalist(context!!)
        val fragment = if (dynalist.inbox == null) {
            InboxConfigurationFragment()
        } else {
            DialogSetupFragment()
        }
        activity!!.supportFragmentManager.beginTransaction().apply {
            setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            replace(R.id.fragment_container, fragment)
            commit()
        }
    }

}
