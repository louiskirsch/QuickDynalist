package com.louiskirsch.quickdynalist

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.utils.setupGrowingMultiline
import kotlinx.android.synthetic.main.activity_main.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.*
import android.util.Pair as UtilPair

class MainActivity : AppCompatActivity() {
    private val dynalist: Dynalist = Dynalist(this)
    private lateinit var adapter: ArrayAdapter<DynalistItem>
    private var location: DynalistItem? = null

    private val preferences: SharedPreferences
        get() = getSharedPreferences("MAIN_ACTIVITY", Context.MODE_PRIVATE)

    private var bookmarkHintCounter: Int
        get() = preferences.getInt("BOOKMARK_HINT_COUNTER", 0)
        set(value) = preferences.edit().putInt("BOOKMARK_HINT_COUNTER", value).apply()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toolbar.inflateMenu(R.menu.quick_dialog_menu)

        intent.extras?.let { dynalist.resolveItemInBundle(it) }?.let { location = it }

        if (location != null) {
            toolbar.title = location!!.strippedMarkersName
            itemLocation.visibility = View.GONE
        } else {
            itemLocation.setOnTouchListener { _, e ->
                if ((e.action == MotionEvent.ACTION_DOWN ||
                                e.action == MotionEvent.ACTION_POINTER_DOWN) && bookmarkHintCounter < 5) {
                    longToast(R.string.bookmark_hint)
                    bookmarkHintCounter += 1
                }
                false
            }

            itemLocation.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    location = itemLocation.selectedItem as DynalistItem
                }

            }

            adapter = ArrayAdapter(this,
                    android.R.layout.simple_spinner_item, ArrayList())
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            itemLocation!!.adapter = adapter

            val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
            model.bookmarksLiveData.observe(this, Observer<List<DynalistItem>> {
                if (location == null && it.isNotEmpty())
                    location = it[0]
                adapter.clear()
                adapter.addAll(it)
            })
        }

        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.open_item_list -> {
                    val bundle = Bundle()
                    bundle.putParcelable(DynalistApp.EXTRA_DISPLAY_ITEM, location)
                    bundle.putString(NavigationActivity.EXTRA_ITEM_TEXT, itemContents.text.toString())

                    val intent = Intent(this, NavigationActivity::class.java)
                    intent.putExtras(bundle)
                    val transition = ActivityOptions.makeSceneTransitionAnimation(this,
                            UtilPair.create(toolbar as View, "toolbar"),
                            UtilPair.create(itemContents as View, "itemContents"))
                    startActivity(intent, transition.toBundle())
                    itemContents.text.clear()
                }
                R.id.open_large -> {
                    val intent = Intent(this, AdvancedItemActivity::class.java)
                    intent.putExtra(DynalistApp.EXTRA_DISPLAY_ITEM, location as Parcelable)
                    intent.putExtra(AdvancedItemActivity.EXTRA_ITEM_TEXT, itemContents.text)
                    intent.putExtra(AdvancedItemActivity.EXTRA_SELECT_BOOKMARK,
                            itemLocation.visibility == View.VISIBLE)
                    val transition = if (itemLocation.visibility == View.GONE) {
                        ActivityOptions.makeSceneTransitionAnimation(this,
                                UtilPair.create(toolbar as View, "toolbar"),
                                UtilPair.create(itemContents as View, "itemContents"))
                    } else {
                        ActivityOptions.makeSceneTransitionAnimation(this,
                                UtilPair.create(toolbar as View, "toolbar"),
                                UtilPair.create(itemLocation as View, "itemLocation"),
                                UtilPair.create(itemContents as View, "itemContents"))
                    }
                    startActivity(intent, transition.toBundle())
                    itemContents.text.clear()
                }
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }

        setupItemContentsTextField()

        submitButton.setOnClickListener {
            dynalist.addItem(itemContents!!.text.toString(), location!!)
            itemContents.text.clear()
        }
        submitCloseButton.setOnClickListener {
            dynalist.addItem(itemContents.text.toString(), location!!)
            finish()
        }
    }

    override fun finish() {
        finishAndRemoveTask()
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        dynalist.subscribe()
    }

    private fun setupItemContentsTextField() {
        with(itemContents!!) {
            setupGrowingMultiline(5)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

                override fun afterTextChanged(editable: Editable) {
                    updateSubmitEnabled()
                }
            })
            setOnEditorActionListener { _, actionId, _ ->
                val isDone = actionId == EditorInfo.IME_ACTION_DONE
                if (isDone && submitButton.isEnabled) {
                    submitButton!!.performClick()
                }
                isDone
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateSubmitEnabled()
        if (!dynalist.isAuthenticated && !dynalist.isAuthenticating) {
            dynalist.authenticate()
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
        val enabled = dynalist.isAuthenticated && !itemContents!!.text.toString().isEmpty()
        submitButton!!.isEnabled = enabled
        submitCloseButton!!.isEnabled = enabled
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAuthenticationEvent(event: AuthenticatedEvent) {
        updateSubmitEnabled()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onItemEvent(event: ItemEvent) {
        if (!event.success)
            toast(R.string.error_update_server)
    }
}
