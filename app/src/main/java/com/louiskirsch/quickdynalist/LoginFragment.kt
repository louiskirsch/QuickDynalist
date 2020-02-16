package com.louiskirsch.quickdynalist


import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.louiskirsch.quickdynalist.jobs.SyncJob
import com.louiskirsch.quickdynalist.jobs.VerifyTokenJob
import kotlinx.android.synthetic.main.fragment_login.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.browse
import org.jetbrains.anko.toast

class LoginFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth_open_browser.setOnClickListener {
            context!!.browse("https://dynalist.io/developer")
        }
        auth_submit_token.setOnClickListener {
            val token = auth_token.text.toString()
            val jobManager = DynalistApp.instance.jobManager
            val job = VerifyTokenJob(token)
            jobManager.addJobInBackground(job)
            auth_submit_token.isEnabled = false
        }
        auth_token.addTextChangedListener(object : TextWatcher{
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                auth_submit_token.isEnabled = s?.isNotBlank() ?: false
            }

        })
        val dynalist = Dynalist(context!!)
        sync_mobile_data.isChecked = dynalist.syncMobileData
        sync_mobile_data.setOnCheckedChangeListener { _, isChecked ->
            dynalist.syncMobileData = isChecked
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAuthenticationEvent(event: AuthenticatedEvent) {
        if (!event.success) {
            auth_token_layout.error = getString(R.string.token_invalid)
        } else {
            val job = SyncJob(requireUnmeteredNetwork = false, isManual = true)
            DynalistApp.instance.jobManager.addJobInBackground(job)
            activity!!.supportFragmentManager.beginTransaction().apply {
                setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                replace(R.id.fragment_container, SyncStatusFragment())
                commit()
            }
        }
    }

}
