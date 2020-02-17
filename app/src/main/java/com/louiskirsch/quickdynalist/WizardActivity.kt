package com.louiskirsch.quickdynalist

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import org.jetbrains.anko.toast

class WizardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wizard)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .add(R.id.fragment_container, LoginFragment()).commit()
        }
    }

    override fun onBackPressed() {
        toast(R.string.wizard_not_completed)
    }
}
