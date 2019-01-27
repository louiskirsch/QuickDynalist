package com.louiskirsch.quickdynalist

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_details.*

class DetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeAsUpIndicator(R.drawable.ic_action_discard)
        actionBarView.transitionName = "toolbar"
        window.allowEnterTransitionOverlap = true

        val displayItem: DynalistItem = intent.getParcelableExtra(DynalistApp.EXTRA_DISPLAY_ITEM)

        itemText.text = displayItem.getSpannableText(this)
        itemText.visibility = if (itemText.text.isBlank()) View.GONE else View.VISIBLE
        itemNotes.text = displayItem.getSpannableNotes(this)
        itemNotes.visibility = if (itemNotes.text.isBlank()) View.GONE else View.VISIBLE

        itemImage.visibility = View.GONE
        displayItem.image?.let {
            Picasso.get().load(it).into(itemImage, object: Callback {
                override fun onError(e: Exception?) {}
                override fun onSuccess() {
                    itemImage.visibility = View.VISIBLE
                }
            })
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item!!.itemId == android.R.id.home) {
            fixedFinishAfterTransition()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
