package com.louiskirsch.quickdynalist.jobs

import com.birbit.android.jobqueue.JobManager
import com.birbit.android.jobqueue.scheduling.FrameworkJobSchedulerService
import com.louiskirsch.quickdynalist.DynalistApp

class JobService: FrameworkJobSchedulerService() {

    override fun getJobManager(): JobManager {
        return DynalistApp.instance.jobManager
    }

}
