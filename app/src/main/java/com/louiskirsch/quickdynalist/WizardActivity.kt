package com.louiskirsch.quickdynalist

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class WizardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wizard)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .add(R.id.fragment_container, LoginFragment()).commit()
        }
    }
}
