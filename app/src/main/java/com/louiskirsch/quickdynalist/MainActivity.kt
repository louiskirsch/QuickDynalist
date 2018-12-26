package com.louiskirsch.quickdynalist

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_main.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.contentView
import org.jetbrains.anko.toast

class MainActivity : Activity() {
    private val dynalist: Dynalist = Dynalist(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupItemContentsTextField()

        submitButton!!.setOnClickListener {
            dynalist!!.addItem(itemContents!!.text.toString(),
                    itemLocation!!.selectedItem.toString())
            itemContents!!.text.clear()
        }

        val adapter = ArrayAdapter.createFromResource(this,
                R.array.item_locations, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        itemLocation!!.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        dynalist.subscribe()
    }

    private fun setupItemContentsTextField() {
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

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        dynalist.unsubscribe()
    }

    private fun updateSubmitEnabled() {
        val enabled = dynalist!!.isAuthenticated && !itemContents!!.text.toString().isEmpty()
        submitButton!!.isEnabled = enabled
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAuthenticationEvent(event: AuthenticatedEvent) {
        updateSubmitEnabled()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onItemEvent(event: ItemEvent) {
        if (!event.success)
            toast(R.string.add_item_error)
    }
}
