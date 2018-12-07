package com.louiskirsch.quickdynalist

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : Activity() {
    private var dynalist: Dynalist? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        with(itemContents!!) {
            // These properties must be set programmatically because order of execution matters
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            maxLines = 5
            setHorizontallyScrolling(false)
            imeOptions = EditorInfo.IME_ACTION_DONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

                override fun afterTextChanged(editable: Editable) {
                    updateSubmitEnabled()
                }
            })
            setOnEditorActionListener { textView, actionId, keyEvent ->
                val isDone = actionId == EditorInfo.IME_ACTION_DONE
                if (isDone) {
                    submitButton!!.performClick()
                }
                isDone
            }
        }

        submitButton!!.setOnClickListener {
            dynalist!!.addItem(itemContents!!.text.toString(),
                    itemLocation!!.selectedItem.toString())
            itemContents!!.text.clear()
        }

        val adapter = ArrayAdapter.createFromResource(this,
                R.array.item_locations, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        itemLocation!!.adapter = adapter

        dynalist = Dynalist(this)
    }

    override fun onResume() {
        super.onResume()
        updateSubmitEnabled()
        if (!dynalist!!.isAuthenticated && !dynalist!!.isAuthenticating) {
            dynalist!!.authenticate()
        } else {
            itemContents!!.requestFocus()
        }
    }

    private fun updateSubmitEnabled() {
        val enabled = dynalist!!.isAuthenticated && !itemContents!!.text.toString().isEmpty()
        submitButton!!.isEnabled = enabled
    }
}
