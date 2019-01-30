package com.louiskirsch.quickdynalist

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.content.FileProvider
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import kotlinx.android.synthetic.main.activity_details.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File
import java.io.FileOutputStream
import java.util.*

class DetailsActivity : AppCompatActivity() {

    private var cachedImage: File? = null

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
            Picasso.get().load(it).run {
                into(itemImage, object: Callback {
                    override fun onError(e: Exception?) {}
                    override fun onSuccess() {
                        itemImage.visibility = View.VISIBLE
                    }
                })
                into(object: Target {
                    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
                    override fun onBitmapFailed(e: java.lang.Exception?, errorDrawable: Drawable?) {}
                    override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                        itemImage.setOnClickListener {
                            val contentUri = generateContentURI(bitmap!!)
                            openInGallery(contentUri)
                        }
                    }
                })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearCache()
    }

    private fun clearCache() {
        if (cachedImage != null) {
            cachedImage!!.delete()
        }
    }

    private fun generateContentURI(bitmap: Bitmap): Uri {
        if (cachedImage == null) {
            val path = File(cacheDir, "image-cache")
            path.mkdir()
            cachedImage = File(path, UUID.randomUUID().toString() + ".png")
            val stream = FileOutputStream(cachedImage)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return FileProvider.getUriForFile(this,
                "com.louiskirsch.quickdynalist.fileprovider", cachedImage!!)
    }

    private fun openInGallery(contentUri: Uri) {
        Intent().apply {
            action = Intent.ACTION_VIEW
            setDataAndType(contentUri, "image/*")
            startActivity(this)
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
