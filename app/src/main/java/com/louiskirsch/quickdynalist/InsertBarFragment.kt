package com.louiskirsch.quickdynalist

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.util.Pair
import android.view.*
import androidx.fragment.app.Fragment
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getColor
import com.louiskirsch.quickdynalist.jobs.EditItemJob
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistTag
import com.louiskirsch.quickdynalist.utils.DynalistEditActionHelper
import com.louiskirsch.quickdynalist.utils.SpeechRecognitionHelper
import com.louiskirsch.quickdynalist.utils.clearSpans
import com.louiskirsch.quickdynalist.utils.setupGrowingMultiline
import kotlinx.android.synthetic.main.app_bar_navigation.*
import kotlinx.android.synthetic.main.fragment_insert_bar.*
import org.jetbrains.anko.toast
import java.util.*

class InsertBarFragment : Fragment() {
    interface InteractionListener {
        fun onEditingItemChanged(view: TextView, editingItem: DynalistItem?)
    }

    interface LocationProvider {
        val addItemLocation: DynalistItem?
    }

    private val speechRecognitionHelper = SpeechRecognitionHelper()
    private val dynalistEditActionHelper = DynalistEditActionHelper(this)
    private var listeners = LinkedList<InteractionListener>()
    private lateinit var dynalist: Dynalist

    private var locationProvider: LocationProvider? = null
    private var hideIfNotEditing: Boolean = false
        set(value) {
            if (editingItem == null && value) hide()
            field = value
        }

    var editingItem: DynalistItem? = null
        set(value) {
            if (field != null && value == null) {
                onClearEditingItem()
            }
            if (value != null) {
                onSetEditingItem(value)
            }
            listeners.forEach { it.onEditingItemChanged(itemContents, value) }
            field = value
        }
    var text: Editable
        get() = itemContents.text
        set(value) {
            itemContents.text = DynalistItem.markdownToSpans(SpannableStringBuilder(value),
                    context!!)
        }

    private fun onClearEditingItem() {
        itemContents.text.clear()
        itemContents.text.clearSpans(DynalistItem.markdownSpanTypes)
        if (hideIfNotEditing)
            hide()
    }

    private fun onSetEditingItem(value: DynalistItem) {
        setText(value.name)
        itemContents.apply {
            DynalistTag.highlightTags(context, text)
            requestFocus()
            setSelection(text.length)
        }
        show()
    }

    private fun clearItemContents() {
        itemContents.text.clear()
        itemContents.text.clearSpans(DynalistItem.markdownSpanTypes)
        editingItem = null
    }

    private val transitionBundle: Bundle
        get() = ActivityOptions.makeSceneTransitionAnimation(
                activity, activity!!.appBar, "toolbar").toBundle()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("EDITING_ITEM", editingItem)
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

    private fun updateSubmitEnabled() {
        submitButton.isEnabled = itemContents.text.isNotEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dynalist = Dynalist(context!!)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_insert_bar, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setupItemContentsTextField()
        editingItem = savedInstanceState?.getParcelable("EDITING_ITEM")

        if (itemContents.text.isNotBlank())
            itemContents.setSelection(itemContents.text.length)

        submitButton.setOnClickListener {
            if (locationProvider == null) return@setOnClickListener
            val text = DynalistItem.spannedToMarkdown(itemContents.text)
            if (editingItem == null) {
                dynalist.addItem(text, locationProvider!!.addItemLocation!!)
            } else if (editingItem!!.name != text) {
                editingItem!!.name = text
                DynalistApp.instance.jobManager.addJobInBackground(
                        EditItemJob(editingItem!!)
                )
            }
            clearItemContents()
        }
        updateSubmitEnabled()

        advancedItemButton.setOnClickListener {
            if (locationProvider == null) return@setOnClickListener
            val intent = Intent(context, AdvancedItemActivity::class.java)
            val text = DynalistItem.spannedToMarkdown(itemContents.text)
            if (editingItem != null) {
                editingItem!!.name = text
                intent.putExtra(AdvancedItemActivity.EXTRA_EDIT_ITEM, editingItem as Parcelable)
            } else {
                intent.putExtra(DynalistApp.EXTRA_DISPLAY_ITEM,
                        locationProvider!!.addItemLocation as Parcelable)
                intent.putExtra(AdvancedItemActivity.EXTRA_ITEM_TEXT, text)
            }
            clearItemContents()
            val activity = activity as AppCompatActivity
            val transitionBundle = if (view!!.visibility == View.VISIBLE) {
                ActivityOptions.makeSceneTransitionAnimation(activity,
                        Pair.create(activity.appBar as View, "toolbar"),
                        Pair.create(itemContents as View, "itemContents")).toBundle()
            } else {
                transitionBundle
            }
            startActivity(intent, transitionBundle)
        }
        recordSpeechButton.setOnClickListener {
            speechRecognitionHelper.startSpeechRecognition(this)
        }

        DynalistTag.highlightTags(context!!, itemContents.text)
        DynalistTag.setupTagDetection(itemContents) { dynalist.shouldDetectTags }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is InteractionListener) {
            listeners.add(context)
        }
    }

    override fun onDetach() {
        super.onDetach()
        context?.let { context ->
            if (context is InteractionListener) {
                listeners.remove(context)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        speechRecognitionHelper.dispatchResult(context!!, requestCode, resultCode, data) {
            appendText(it)
            if (dynalist.speechAutoSubmit)
                submit()
        }
        dynalistEditActionHelper.dispatchResult(requestCode, resultCode, data) { _, newText ->
            itemContents.apply { text.insert(selectionStart, newText) }
            submit()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            dynalistEditActionHelper.setupEditActions(itemContents)
        }
    }

    private fun hide() {
        view?.visibility = View.GONE
    }

    private fun show() {
        view?.visibility = View.VISIBLE
    }

    fun setText(text: CharSequence?) {
        itemContents.text = DynalistItem.markdownToSpans(SpannableStringBuilder(text), context!!)
    }

    private fun appendText(text: CharSequence) {
        val sb = SpannableStringBuilder(text)
        DynalistTag.highlightTags(context!!, sb)
        itemContents.text.apply { if (isNotBlank()) append(' ') }
        itemContents.text.append(sb)
    }

    private fun submit() {
        submitButton.performClick()
    }

    fun registerListener(listener: InteractionListener) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: InteractionListener) {
        listeners.remove(listener)
    }

    fun configure(locationProvider: LocationProvider?, hideIfNotEditing: Boolean) {
        this.locationProvider = locationProvider
        this.hideIfNotEditing = hideIfNotEditing
        if (view == null)
            return
        editingItem = null
        if (hideIfNotEditing || locationProvider == null)
            hide()
        else
            show()
    }
}
