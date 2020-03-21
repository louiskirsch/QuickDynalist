package com.louiskirsch.quickdynalist

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import org.jetbrains.anko.toast

class WizardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONFIG_INBOX_ONLY = "EXTRA_CONFIG_INBOX_ONLY"
        const val EXTRA_DIALOG_SETUP_ONLY = "EXTRA_DIALOG_SETUP_ONLY"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wizard)

        if (savedInstanceState == null) {
            val fragment = when {
                intent.getBooleanExtra(EXTRA_CONFIG_INBOX_ONLY, false) ->
                    InboxConfigurationFragment.newInstance(finishAfter = true)
                intent.getBooleanExtra(EXTRA_DIALOG_SETUP_ONLY, false) ->
                    DialogSetupFragment()
                else -> LoginFragment()
            }
            supportFragmentManager.beginTransaction()
                    .add(R.id.fragment_container, fragment).commit()
        }
    }

    override fun onBackPressed() {
        toast(R.string.wizard_not_completed)
    }
}
