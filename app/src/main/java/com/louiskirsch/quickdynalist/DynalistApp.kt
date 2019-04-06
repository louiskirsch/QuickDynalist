package com.louiskirsch.quickdynalist

import com.birbit.android.jobqueue.JobManager
import com.birbit.android.jobqueue.config.Configuration
import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.birbit.android.jobqueue.scheduling.FrameworkJobSchedulerService.*
import com.louiskirsch.quickdynalist.jobs.JobService
import com.louiskirsch.quickdynalist.network.DynalistService
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItemFilter
import com.louiskirsch.quickdynalist.objectbox.DynalistTag
import com.louiskirsch.quickdynalist.objectbox.MyObjectBox
import com.squareup.picasso.Picasso
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import org.greenrobot.eventbus.EventBus
import org.jetbrains.anko.doAsync
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*


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
        const val EXTRA_DISPLAY_FILTER = "EXTRA_DISPLAY_FILTER"
        const val EXTRA_DISPLAY_FILTER_ID = "EXTRA_DISPLAY_FILTER_ID"
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

        val dynalist = Dynalist(applicationContext)
        AppCompatDelegate.setDefaultNightMode(dynalist.preferredTheme)
        upgrade(dynalist)
    }

    private fun upgrade(dynalist: Dynalist) {
        val version = dynalist.preferencesVersion
        if (version < 12) {
            doAsync {
                jobManager.clear()
                DynalistItem.box.put(DynalistItem.newInbox().apply { notifyModified() })
                if (dynalist.isAuthenticated)
                    dynalist.sync()
            }
        }
        if (version in 2..15) {
            val box = DynalistItem.box
            boxStore.runInTxAsync({
                box.put(box.all.apply { forEach {
                    it.hidden = false
                    it.areCheckedItemsVisible = false
                    it.isChecklist = false
                } })
            }, null)
        }
        if (version in 2..20) {
            val box = DynalistItem.box
            boxStore.runInTxAsync({
                val now = Date()
                box.put(box.all.apply { forEach { it.notifyModified(now) } })
            }, null)
        } else if (version == 21) {
            val box = DynalistItem.box
            boxStore.runInTxAsync({
                box.put(box.all.apply { forEach { it.updateMetaData() } })
            }, null)
        }
        if (version in 2..26) {
            val itemBox = DynalistItem.box
            val filterBox = DynalistItemFilter.box
            val tagBox = DynalistTag.box
            boxStore.runInTxAsync({
                val filters = filterBox.all
                val tags = filters.map { filter -> filter.tags.map { it.fullName } }
                tagBox.removeAll()
                DynalistTag.clearCache()
                itemBox.put(itemBox.all.apply { forEach { it.updateMetaData() } })
                filters.zip(tags).forEach { (filter, filterTags) ->
                    filter.tags.clear()
                    filter.tags.addAll(filterTags.map { DynalistTag.find(it) })
                }
                filterBox.put(filters)
            }, null)
        }
        if (version in 2..28) {
            val box = DynalistItem.box
            boxStore.runInTxAsync({
                val now = Date()
                box.put(box.all.apply { forEach {
                    it.created = now
                    it.color = 0
                    it.heading = 0
                    it.notifyModified(now)
                }})
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
