package com.louiskirsch.quickdynalist

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.LinkMovementMethod
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeAsUpIndicator(R.drawable.ic_action_discard)
        actionBarView.transitionName = "toolbar"
        window.allowEnterTransitionOverlap = true

        val displayItem: DynalistItem = intent.getParcelableExtra(DynalistApp.EXTRA_DISPLAY_ITEM)

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
                            imageCache.openInGallery(image, this@DetailsActivity)
                        }
                    }
                })
                into(imageCache.getPutInCacheCallback(image))
            }
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
