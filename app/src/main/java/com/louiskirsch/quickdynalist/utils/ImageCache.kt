package com.louiskirsch.quickdynalist.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import com.louiskirsch.quickdynalist.R
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

class ImageCache(context: Context) {

    private val cachePath = File(context.cacheDir, "image-cache")

    fun putInCache(source: String, bitmap: Bitmap) {
        doAsync {
            val identifier = Base64.encodeToString(source.toByteArray(), 0)
            cachePath.mkdir()
            val file = cachePath.resolve("$identifier.png")
            if (!file.exists()) {
                val stream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                Log.d("ImageCache", "Saved: $source")
            } else {
                Log.d("ImageCache", "Already exists: $source")
            }
        }
    }

    fun getPutInCacheCallback(source: String): Target {
        return object: Target {
            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
            override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {}
            override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                putInCache(source, bitmap!!)
            }
        }
    }

    fun getFile(source: String): File? {
        val identifier = Base64.encodeToString(source.toByteArray(), 0)
        val file = cachePath.resolve("$identifier.png")
        return if (file.exists())
            file
        else
            null
    }

    fun getUri(source: String, context: Context): Uri? {
        return getFile(source)?.let {
            FileProvider.getUriForFile(context,
                    "com.louiskirsch.quickdynalist.fileprovider", it)
        }
    }

    fun openInGallery(source: String, context: Context) {
        val uri = getUri(source, context)
        if (uri == null) {
            Log.d("ImageCache", "Can not find: $source")
            context.toast(R.string.error_image_loading)
            return
        }
        Intent().apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            setDataAndType(uri, "image/*")
            startActivity(context, this, null)
        }
    }
}