package com.louiskirsch.quickdynalist

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.chip.Chip
import com.louiskirsch.quickdynalist.adapters.FilterAdapter
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItemFilter
import com.louiskirsch.quickdynalist.objectbox.DynalistTag
import com.louiskirsch.quickdynalist.utils.actionBarView
import com.louiskirsch.quickdynalist.utils.children
import com.louiskirsch.quickdynalist.utils.fixedFinishAfterTransition
import kotlinx.android.synthetic.main.activity_edit_filter.*
import java.lang.Math.max

class EditFilterActivity : AppCompatActivity() {

    inner class DateFilter(private val displayName: Int, val minRelativeDate: Long?,
                           val maxRelativeDate: Long?) {
        override fun toString(): String = getString(displayName)
    }

    inner class SearchDepth(private val displayName: Int, val searchDepth: Int) {
        override fun toString(): String = getString(displayName)
    }

    private val searchDepthOptions = arrayOf(
            SearchDepth(R.string.filter_search_depth_child, 1),
            SearchDepth(R.string.filter_search_depth_2, 2),
            SearchDepth(R.string.filter_search_depth_any, 20)
    )

    private val dateFilters = arrayOf(
            DateFilter(R.string.filter_date_any, null, null),
            DateFilter(R.string.filter_date_today, 0, 24 * 60 * 60 * 1000L),
            DateFilter(R.string.filter_date_today_or_earlier, null,
                    24 * 60 * 60 * 1000L),
            DateFilter(R.string.filter_date_next_day, 0,
                    2 * 24 * 60 * 60 * 1000L),
            DateFilter(R.string.filter_date_next_7_days, 0,
                    7 * 24 * 60 * 60 * 1000L)
    )

    private val modifiedDateFilters = arrayOf(
            DateFilter(R.string.filter_date_any, null, null),
            DateFilter(R.string.filter_date_today, 0, null),
            DateFilter(R.string.filter_date_last_3_days, -3 * 24 * 60 * 60 * 1000L,
                    null),
            DateFilter(R.string.filter_date_last_7_days, -7 * 24 * 60 * 60 * 1000L,
                    null)
    )

    private lateinit var filter: DynalistItemFilter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_filter)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeAsUpIndicator(R.drawable.ic_action_discard)
        supportActionBar!!.title = ""
        actionBarView.transitionName = "toolbar"
        window.allowEnterTransitionOverlap = true

        filter = if (savedInstanceState == null)
            intent.getParcelableExtra(DynalistApp.EXTRA_DISPLAY_FILTER)
        else
            savedInstanceState.getParcelable(DynalistApp.EXTRA_DISPLAY_FILTER)!!
        DynalistItemFilter.box.attach(filter)

        updateFromFilter()
    }

    private fun reset(): Boolean {
        filter = DynalistItemFilter().apply {
            id = filter.id
            name = filterName.text.toString()
            DynalistItemFilter.box.attach(this)
            tags.clear()
        }
        updateFromFilter()
        return true
    }

    private fun updateFromFilter() {
        filterName.setText(filter.name)
        filterShowAsChecklist.isChecked = filter.showAsChecklist
        setupLogicMode()
        setupDateFilter()
        setupModifiedDateFilter()
        setupTagsFilter()
        setupLocation()
        setupItemProperties()
        setupSortOrder()
    }

    private fun setupSortOrder() {
        when(filter.sortOrder) {
            DynalistItemFilter.Order.MANUAL -> filterSortOrderManual.isChecked = true
            DynalistItemFilter.Order.DATE -> filterSortOrderDate.isChecked = true
            DynalistItemFilter.Order.MODIFIED_DATE -> filterSortOrderModifiedDate.isChecked = true
            else -> filterSortOrderManual.isChecked = true
        }
    }

    private fun setupItemProperties() {
        filterContainsText.setText(filter.containsText)
        filterHideIfParentIncluded.isChecked = filter.hideIfParentIncluded
        when (filter.isCompleted) {
            null -> filterIsCompletedAny.isChecked = true
            true -> filterIsCompletedTrue.isChecked = true
            false -> filterIsCompletedFalse.isChecked = true
        }
        when (filter.hasImage) {
            null -> filterHasImageAny.isChecked = true
            true -> filterHasImageTrue.isChecked = true
            false -> filterHasImageFalse.isChecked = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        saveToFilter()
        outState.putParcelable(DynalistApp.EXTRA_DISPLAY_FILTER, filter)
    }

    private fun saveToFilter() {
        filter.apply {
            name = if (filterName.text!!.isNotBlank())
                    filterName.text.toString()
            else
                null
            logicMode = if (filterMatchAny.isChecked)
                DynalistItemFilter.LogicMode.ANY
            else
                DynalistItemFilter.LogicMode.ALL
            minRelativeDate = (filterDate.selectedItem as DateFilter).minRelativeDate
            maxRelativeDate = (filterDate.selectedItem as DateFilter).maxRelativeDate
            minRelativeModifiedDate = (filterModifiedDate.selectedItem as DateFilter).minRelativeDate
            maxRelativeModifiedDate = (filterModifiedDate.selectedItem as DateFilter).maxRelativeDate
            tags.clear()
            tags.addAll(filterTagsChipGroup.children.map {
                it.getTag(R.id.tag_dynalist_tag) as DynalistTag
            })
            parent.target = if (filterLocationEnabled.isChecked)
                filterParent.selectedItem as DynalistItem
            else
                null
            searchDepth = (filterParentSearchDepth.selectedItem as SearchDepth).searchDepth
            containsText = if (filterContainsText.text!!.isNotBlank())
                filterContainsText.text.toString()
            else
                null
            hideIfParentIncluded = filterHideIfParentIncluded.isChecked
            showAsChecklist = filterShowAsChecklist.isChecked
            sortOrder = when {
                filterSortOrderDate.isChecked -> DynalistItemFilter.Order.DATE
                filterSortOrderModifiedDate.isChecked -> DynalistItemFilter.Order.MODIFIED_DATE
                else -> DynalistItemFilter.Order.MANUAL
            }
            isCompleted = when {
                filterIsCompletedTrue.isChecked -> true
                filterIsCompletedFalse.isChecked -> false
                else -> null
            }
            hasImage = when {
                filterHasImageTrue.isChecked -> true
                filterHasImageFalse.isChecked -> false
                else -> null
            }
        }
    }

    private fun setupLocation() {
        filterParent.isEnabled = false
        filterParentSearchDepth.isEnabled = false
        filterLocationEnabled.setOnCheckedChangeListener { _, isChecked ->
            filterParent.isEnabled = isChecked
            filterParentSearchDepth.isEnabled = isChecked
        }
        filterLocationEnabled.isChecked = !filter.parent.isNull

        val adapter = ArrayAdapter<DynalistItem>(this,
                android.R.layout.simple_spinner_item, ArrayList())
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterParent.adapter = adapter

        val searchDepthAdapter = ArrayAdapter(this,
                android.R.layout.simple_spinner_item, searchDepthOptions)
        searchDepthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterParentSearchDepth.adapter = searchDepthAdapter
        filterParentSearchDepth.setSelection(
                max(searchDepthOptions.indexOfFirst { it.searchDepth == filter.searchDepth }, 0))

        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.bookmarksLiveData.observe(this, Observer {
            val initializing = adapter.count == 0
            adapter.clear()
            adapter.addAll(it)
            if (initializing && !filter.parent.isNull) {
                val index = it.indexOf(filter.parent.target)
                filterParent.setSelection(max(index, 0))
            }
        })
    }

    private fun setupLogicMode() {
        when (filter.logicMode) {
            DynalistItemFilter.LogicMode.UNKNOWN -> filterMatchAll.isChecked = true
            DynalistItemFilter.LogicMode.ALL -> filterMatchAll.isChecked = true
            DynalistItemFilter.LogicMode.ANY -> filterMatchAny.isChecked = true
        }
    }

    private fun setupDateFilter() {
        val dateAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
                dateFilters)
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterDate.adapter = dateAdapter
        if (filter.minRelativeDate != null || filter.maxRelativeDate != null) {
            val index = dateFilters.indexOfFirst {
                it.minRelativeDate == filter.minRelativeDate &&
                        it.maxRelativeDate == filter.maxRelativeDate
            }
            filterDate.setSelection(max(index, 0))
        }
    }

    private fun setupModifiedDateFilter() {
        val modifiedDateAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
                modifiedDateFilters)
        modifiedDateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterModifiedDate.adapter = modifiedDateAdapter
        if (filter.minRelativeModifiedDate != null || filter.maxRelativeModifiedDate != null) {
            val index = modifiedDateFilters.indexOfFirst {
                it.minRelativeDate == filter.minRelativeModifiedDate &&
                        it.maxRelativeDate == filter.maxRelativeModifiedDate
            }
            filterModifiedDate.setSelection(if (index >= 0) index else 0)
        }
    }

    private fun setupTagsFilter() {
        val tagsAdapter = FilterAdapter<DynalistTag>(this,
                android.R.layout.simple_dropdown_item_1line, ArrayList())

        filterTagsInput.setAdapter(tagsAdapter)
        filterTagsInput.setOnItemClickListener { parent, _, position, _ ->
            filterTagsInput.text = null
            val selected = parent.getItemAtPosition(position) as DynalistTag
            addTag(selected, tagsAdapter)
        }
        filterTagsChipGroup.removeAllViews()
        filter.tags.forEach { addTag(it, tagsAdapter) }

        val model = ViewModelProviders.of(this).get(DynalistTagViewModel::class.java)
        model.tagsLiveData.observe(this, Observer {
            tagsAdapter.updateFilterableItems(it)
        })
    }

    private fun addTag(tag: DynalistTag, adapter: FilterAdapter<DynalistTag>) {
        val chip = Chip(this)
        adapter.excludeItem(tag)
        chip.setTag(R.id.tag_dynalist_tag, tag)
        chip.text = tag.toString()
        chip.isCloseIconVisible = true
        chip.setChipIconTintResource(R.color.chipIconTint)

        // necessary to get single selection working
        chip.isClickable = true
        chip.isCheckable = false
        filterTagsChipGroup.addView(chip as View)
        chip.setOnCloseIconClickListener {
            filterTagsChipGroup.removeView(chip as View)
            adapter.includeItem(tag)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_edit_filter, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_apply_filter -> saveAndFinish()
            R.id.action_reset_filter -> reset()
            android.R.id.home -> discardAndFinish()
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun discardAndFinish(): Boolean {
        setResult(Activity.RESULT_CANCELED)
        fixedFinishAfterTransition()
        return true
    }

    private fun saveAndFinish(): Boolean {
        saveToFilter()
        val result = Intent().apply {
            putExtra(DynalistApp.EXTRA_DISPLAY_FILTER, filter as Parcelable)
        }
        setResult(Activity.RESULT_OK, result)
        fixedFinishAfterTransition()
        return true
    }
}
