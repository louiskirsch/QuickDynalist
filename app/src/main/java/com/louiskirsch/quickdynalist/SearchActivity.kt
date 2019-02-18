package com.louiskirsch.quickdynalist

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.louiskirsch.quickdynalist.adapters.FilterItemListAdapter
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.utils.fixedFinishAfterTransition
import kotlinx.android.synthetic.main.activity_search.*

class SearchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        val adapter = FilterItemListAdapter(this)
        searchResults.layoutManager = LinearLayoutManager(this)
        searchResults.adapter = adapter

        adapter.onClickListener = { finishWithSelectedItem(it) }

        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.searchTerm.value = ""
        model.searchItemsLiveData.observe(this, Observer {
            adapter.updateItems(it)
        })

        searchBar.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val searchTerm = s.toString()
                model.searchTerm.value = searchTerm
                adapter.searchTerm = searchTerm
            }
        })
    }

    private fun finishWithSelectedItem(item: DynalistItem) {
        val result = Intent().apply {
            putExtra(DynalistApp.EXTRA_DISPLAY_ITEM, item as Parcelable)
            if (intent != null && intent.hasExtra("payload")) {
                putExtra("payload", intent.getBundleExtra("payload"))
            }
        }
        setResult(Activity.RESULT_OK, result)
        fixedFinishAfterTransition()
    }
}
