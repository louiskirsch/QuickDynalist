package com.louiskirsch.quickdynalist


import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.chip.Chip
import com.louiskirsch.quickdynalist.adapters.FilterAdapter
import com.louiskirsch.quickdynalist.objectbox.*
import com.louiskirsch.quickdynalist.utils.children
import io.objectbox.kotlin.query
import kotlinx.android.synthetic.main.fragment_tag_manager.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

class TagManagerFragment : DialogFragment() {
    private lateinit var item: DynalistItem
    private lateinit var tagsAdapter: FilterAdapter<DynalistTag>
    private var dialogView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        item = arguments?.getParcelable(DynalistApp.EXTRA_DISPLAY_ITEM)!!
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return dialogView ?: inflater.inflate(R.layout.fragment_tag_manager, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        dialogView = LayoutInflater.from(context).inflate(R.layout.fragment_tag_manager, null)
        return AlertDialog.Builder(context!!).apply {
            setTitle(R.string.manage_tags_title)
            setView(dialogView)
            setPositiveButton(android.R.string.ok) { _, _ -> dismiss() }
        }.create()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupTagsManager()
    }

    override fun onDestroyView() {
        dialogView = null
        super.onDestroyView()
    }

    private fun setupTagsManager() {
        tagsAdapter = FilterAdapter(context!!,
                android.R.layout.simple_dropdown_item_1line, ArrayList())

        addTagsInput.setAdapter(tagsAdapter)
        addTagsInput.setOnItemClickListener { parent, _, position, _ ->
            addTagsInput.text = null
            val selected = parent.getItemAtPosition(position) as DynalistTag
            addTag(selected)
        }
        currentTagsChipGroup.removeAllViews()
        val initialTags = item.tags.map { DynalistTag.find(it) }
        initialTags.forEach { addTag(it) }

        val model = ViewModelProviders.of(this).get(DynalistTagViewModel::class.java)
        model.tagsLiveData.observe(this, Observer {
            tagsAdapter.updateFilterableItems(it)
        })

        doAsync {
            val suggestedTags = DynalistItem.box.query {
                orderDesc(DynalistItem_.modified)
                eager(DynalistItem_.metaTags)
            }.find(0, 1000).flatMap { it.metaTags }.distinct().subtract(initialTags).take(10)
            uiThread {
                suggestedTags.forEach { addTagSuggestion(it) }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        DynalistItem.updateGlobally(item) {
            it.tags = currentTagsChipGroup.children.map { view ->
                (view.getTag(R.id.tag_dynalist_tag) as DynalistTag).fullName
            }
        }
    }

    private fun addTag(tag: DynalistTag): View {
        tagsAdapter.excludeItem(tag)
        val chip = createChip(tag)
        currentTagsChipGroup.addView(chip as View)
        val listener = { _: View ->
            currentTagsChipGroup.removeView(chip as View)
            tagsAdapter.includeItem(tag)
        }
        chip.setOnClickListener(listener)
        chip.setOnCloseIconClickListener(listener)
        return chip
    }

    private fun addTagSuggestion(tag: DynalistTag) {
        val chip = createChip(tag).apply {
            closeIcon = context!!.getDrawable(R.drawable.ic_chip_add)
            setCloseIconTintResource(R.color.chipIconAddTint)
        }
        suggestedTagsChipGroup.addView(chip as View)
        val listener = { _: View ->
            val newChip = addTag(tag)
            newChip.visibility = View.INVISIBLE
            newChip.post {
                chip.animate().apply {
                    val newLoc = IntArray(2).also { newChip.getLocationInWindow(it) }
                    val loc = IntArray(2).also { chip.getLocationInWindow(it) }
                    translationX((newLoc[0] - loc[0]).toFloat())
                    translationY((newLoc[1] - loc[1]).toFloat())
                    duration = 300
                    interpolator = DecelerateInterpolator()
                    withEndAction {
                        newChip.visibility = View.VISIBLE
                        if (isAdded)
                            suggestedTagsChipGroup.removeView(chip as View)
                    }
                    start()
                }
            }
            Unit
        }
        chip.setOnClickListener(listener)
        chip.setOnCloseIconClickListener(listener)
    }

    private fun createChip(tag: DynalistTag): Chip {
        return Chip(context!!).apply {
            setTag(R.id.tag_dynalist_tag, tag)
            text = tag.toString()
            isCloseIconVisible = true
            setChipIconTintResource(R.color.chipIconTint)

            // necessary to get single selection working
            isClickable = true
            isCheckable = false
        }
    }

    companion object {
        fun newInstance(item: DynalistItem): TagManagerFragment {
            return TagManagerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(DynalistApp.EXTRA_DISPLAY_ITEM, item)
                }
            }
        }
    }
}
