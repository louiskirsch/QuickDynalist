package com.louiskirsch.quickdynalist

import com.birbit.android.jobqueue.JobManager
import com.birbit.android.jobqueue.config.Configuration
import android.app.Application
import com.birbit.android.jobqueue.scheduling.FrameworkJobSchedulerService.*
import com.louiskirsch.quickdynalist.jobs.BookmarksJob
import com.louiskirsch.quickdynalist.jobs.JobService
import com.louiskirsch.quickdynalist.network.DynalistService
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.MyObjectBox
import com.squareup.picasso.LruCache
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import org.jetbrains.anko.doAsync
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class DynalistApp : Application() {
    lateinit var jobManager: JobManager
    lateinit var dynalistService: DynalistService
    lateinit var boxStore: BoxStore

    init {
        instance = this
    }

    companion object {
        lateinit var instance: DynalistApp
        const val EXTRA_DISPLAY_ITEM = "EXTRA_DISPLAY_ITEM"
        const val EXTRA_DISPLAY_ITEM_ID = "EXTRA_DISPLAY_ITEM_ID"
        const val EXTRA_FROM_SHORTCUT = "EXTRA_FROM_SHORTCUT"
    }

    override fun onCreate() {
        super.onCreate()
        jobManager = createJobManager()

        val retrofit = Retrofit.Builder()
                .baseUrl("https://dynalist.io/api/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        dynalistService = retrofit.create<DynalistService>(DynalistService::class.java)
        boxStore = MyObjectBox.builder().androidContext(this).build()

        Picasso.Builder(this).apply {
            val cacheSize = 200 * 1024 * 1024L // 200MB
            downloader(OkHttp3Downloader(applicationContext, cacheSize))
            Picasso.setSingletonInstance(build())
        }

        upgrade()
    }

    private fun upgrade() {
        val dynalist = Dynalist(applicationContext)
        val version = dynalist.preferencesVersion
        if (version < 12) {
            doAsync {
                jobManager.clear()
                val box: Box<DynalistItem> = boxStore.boxFor()
                box.put(DynalistItem.newInbox())
                if (dynalist.isAuthenticated)
                    jobManager.addJobInBackground(BookmarksJob())
            }
            dynalist.preferencesVersion = BuildConfig.VERSION_CODE
        }
    }

    private fun createJobManager(): JobManager {
        val builder = Configuration.Builder(this)
        builder.scheduler(createSchedulerFor(this, JobService::class.java), true)
        return JobManager(builder.build())
    }

}
