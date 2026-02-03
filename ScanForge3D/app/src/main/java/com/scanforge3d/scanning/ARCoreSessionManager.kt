package com.scanforge3d.scanning

import android.content.Context
import com.google.ar.core.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ARCoreSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var session: Session? = null

    fun createSession(): Session {
        val session = Session(context)

        val config = Config(session).apply {
            depthMode = Config.DepthMode.RAW_DEPTH_ONLY
            focusMode = Config.FocusMode.AUTO
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        }

        session.configure(config)
        this.session = session
        return session
    }

    fun isDepthSupported(): Boolean {
        return session?.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY) == true
    }

    fun resume() { session?.resume() }
    fun pause() { session?.pause() }
    fun close() { session?.close(); session = null }

    fun getSession(): Session = session
        ?: throw IllegalStateException("ARCore Session not initialized")
}
