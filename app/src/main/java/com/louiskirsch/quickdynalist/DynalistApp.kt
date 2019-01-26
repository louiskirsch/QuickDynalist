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
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
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
        upgrade()
    }

    private fun upgrade() {
        val dynalist = Dynalist(applicationContext)
        if (dynalist.preferencesVersion < 12) {
            doAsync {
                jobManager.clear()
                val box: Box<DynalistItem> = boxStore.boxFor()
                box.put(DynalistItem.newInbox())
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
