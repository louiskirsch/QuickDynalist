package com.louiskirsch.quickdynalist

import com.birbit.android.jobqueue.JobManager
import com.birbit.android.jobqueue.config.Configuration
import android.app.Application
import com.birbit.android.jobqueue.scheduling.FrameworkJobSchedulerService.*
import com.louiskirsch.quickdynalist.jobs.JobService
import com.louiskirsch.quickdynalist.network.DynalistService
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.MyObjectBox
import com.squareup.picasso.Picasso
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import org.greenrobot.eventbus.EventBus
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
            listener { _, uri, exception ->
                try {
                    // Picasso does not expose HTTP error code
                    exception.javaClass.getDeclaredField("code").apply {
                        isAccessible = true
                        val code = getInt(exception)
                        if (code == 403 || code == 401) {
                            EventBus.getDefault().post(ForbiddenImageEvent(uri))
                        }
                    }
                } catch (e: Exception) {}
            }
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
                    dynalist.sync()
            }
        }
        if (version < 16) {
            val box: Box<DynalistItem> = boxStore.boxFor()
            boxStore.runInTxAsync({
                box.put(box.all.apply { forEach {
                    it.hidden = false
                    it.areCheckedItemsVisible = false
                    it.isChecklist = false
                } })
            }, null)
        }
        dynalist.preferencesVersion = BuildConfig.VERSION_CODE
    }

    private fun createJobManager(): JobManager {
        val builder = Configuration.Builder(this)
        builder.scheduler(createSchedulerFor(this, JobService::class.java), true)
        return JobManager(builder.build())
    }

}
