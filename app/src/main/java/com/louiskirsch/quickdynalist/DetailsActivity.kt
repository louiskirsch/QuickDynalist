package com.louiskirsch.quickdynalist

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcelable
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.utils.ImageCache
import com.louiskirsch.quickdynalist.utils.actionBarView
import com.louiskirsch.quickdynalist.utils.fixedFinishAfterTransition
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_details.*

class DetailsActivity : AppCompatActivity() {

    private lateinit var displayItem: DynalistItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeAsUpIndicator(R.drawable.ic_action_discard)
        actionBarView.transitionName = "toolbar"
        window.allowEnterTransitionOverlap = true

        displayItem = intent.getParcelableExtra(DynalistApp.EXTRA_DISPLAY_ITEM)

        itemText.apply {
            text = displayItem.getSpannableText(context)
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            movementMethod = LinkMovementMethod()
        }
        itemNotes.apply {
            text = displayItem.getSpannableNotes(context)
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            movementMethod = LinkMovementMethod()
        }

        itemImage.visibility = View.GONE
        displayItem.image?.also { image ->
            val picasso = Picasso.get()
            val imageCache = ImageCache(this)
            val request = imageCache.getFile(image)?.let { picasso.load(it) } ?: picasso.load(image)
            request.apply {
                into(itemImage, object: Callback {
                    override fun onError(e: Exception?) {}
                    override fun onSuccess() {
                        itemImage.visibility = View.VISIBLE
                        itemImage.setOnClickListener {
                            imageCache.openInGallery(image)
                        }
                    }
                })
                into(imageCache.getPutInCacheCallback(image))
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_details, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item!!.itemId) {
            android.R.id.home -> {
                fixedFinishAfterTransition()
                true
            }
            R.id.action_edit -> {
                val intent = Intent(this, AdvancedItemActivity::class.java).apply {
                    putExtra(AdvancedItemActivity.EXTRA_EDIT_ITEM, displayItem as Parcelable)
                }
                finish()
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
