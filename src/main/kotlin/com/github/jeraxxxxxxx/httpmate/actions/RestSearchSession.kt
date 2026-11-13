package com.github.jeraxxxxxxx.httpmate.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Key
import java.util.concurrent.atomic.AtomicReference

/** The project slot remains occupied until a canceled worker has actually exited. EDT owned. */
internal class RestSearchSession {
    val indicator = AtomicReference<ProgressIndicator?>()
    var closed = false
        private set
    private var finished = false
    var focus: () -> Unit = {}

    fun close(project: Project) {
        closed = true
        indicator.get()?.cancel()
        releaseIfDone(project)
    }

    fun finish(project: Project) {
        finished = true
        releaseIfDone(project)
    }

    private fun releaseIfDone(project: Project) {
        if (closed && finished && project.getUserData(KEY) === this) project.putUserData(KEY, null)
    }

    companion object {
        private val KEY = Key.create<RestSearchSession>("httpmate.rest.search.session")
        fun current(project: Project): RestSearchSession? = project.getUserData(KEY)
        fun acquire(project: Project): RestSearchSession? {
            if (current(project) != null) return null
            return RestSearchSession().also { project.putUserData(KEY, it) }
        }
    }
}
