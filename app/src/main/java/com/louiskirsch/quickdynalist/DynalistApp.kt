package com.louiskirsch.quickdynalist

import com.birbit.android.jobqueue.JobManager
import com.birbit.android.jobqueue.scheduling.FrameworkJobSchedulerService
import com.birbit.android.jobqueue.config.Configuration
import android.app.Application
import com.birbit.android.jobqueue.TagConstraint
import com.birbit.android.jobqueue.scheduling.FrameworkJobSchedulerService.*
import com.louiskirsch.quickdynalist.jobs.Bookmark
import com.louiskirsch.quickdynalist.jobs.BookmarksJob
import com.louiskirsch.quickdynalist.jobs.JobService
import com.louiskirsch.quickdynalist.network.DynalistService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class DynalistApp : Application() {
    lateinit var jobManager: JobManager
    lateinit var dynalistService: DynalistService

    init {
        instance = this
    }

    companion object {
        lateinit var instance: DynalistApp
    }

    override fun onCreate() {
        super.onCreate()
        jobManager = createJobManager()

        val retrofit = Retrofit.Builder()
                .baseUrl("https://dynalist.io/api/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        dynalistService = retrofit.create<DynalistService>(DynalistService::class.java)
        upgrade()
    }

    private fun upgrade() {
        val dynalist = Dynalist(applicationContext)
        if (dynalist.preferencesVersion < 9) {
            jobManager.cancelJobsInBackground({}, TagConstraint.ALL, emptyArray())
            dynalist.bookmarks = arrayOf(Bookmark.newInbox())
            jobManager.addJobInBackground(BookmarksJob())
            dynalist.preferencesVersion = BuildConfig.VERSION_CODE
        }
    }

    private fun createJobManager(): JobManager {
        val builder = Configuration.Builder(this)
        builder.scheduler(createSchedulerFor(this, JobService::class.java), true)
        return JobManager(builder.build())
    }

}
