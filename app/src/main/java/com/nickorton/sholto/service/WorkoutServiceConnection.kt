package com.nickorton.sholto.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Binds to [WorkoutService] and exposes its [WorkoutController] as a
 * [StateFlow]. Used by `TimerViewModel` so the connection survives screen
 * rotation and unbinds cleanly when the UI is closed.
 */
class WorkoutServiceConnection(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val _controller = MutableStateFlow<WorkoutController?>(null)
    val controller: StateFlow<WorkoutController?> = _controller.asStateFlow()

    private var bound = false

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                val binder = service as? WorkoutService.LocalBinder ?: return
                _controller.value = binder.service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                _controller.value = null
            }
        }

    fun bind() {
        if (bound) return
        bound =
            appContext.bindService(
                Intent(appContext, WorkoutService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
    }

    fun unbind() {
        if (!bound) return
        bound = false
        _controller.value = null
        runCatching { appContext.unbindService(connection) }
    }
}
