package com.templatefinder.manager

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.templatefinder.model.SearchResult
import com.templatefinder.service.CoordinateFinderService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manager for handling communication between UI and CoordinateFinderService
 */
class ServiceCommunicationManager(private val context: Context) {

    companion object {
        private const val TAG = "ServiceCommunicationManager"
    }

    private var coordinateFinderService: CoordinateFinderService? = null
    private val isServiceBound = AtomicBoolean(false)
    private val callbacks = mutableSetOf<ServiceCommunicationCallback>()

    /**
     * Interface for service communication callbacks
     */
    interface ServiceCommunicationCallback {
        fun onServiceConnected()
        fun onServiceDisconnected()
        fun onSearchStarted()
        fun onSearchStopped()
        fun onResultFound(result: SearchResult)
        fun onSearchError(error: String)
        fun onStatusUpdate(status: CoordinateFinderService.ServiceStatus)
    }

    /**
     * Service connection for binding to CoordinateFinderService
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            
            val binder = service as CoordinateFinderService.LocalBinder
            coordinateFinderService = binder.getService()
            isServiceBound.set(true)
            
            // Register service callback
            coordinateFinderService?.addCallback(serviceCallback)
            
            // Notify callbacks
            notifyCallbacks { it.onServiceConnected() }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            
            coordinateFinderService?.removeCallback(serviceCallback)
            coordinateFinderService = null
            isServiceBound.set(false)
            
            // Notify callbacks
            notifyCallbacks { it.onServiceDisconnected() }
        }
    }

    /**
     * Callback for receiving updates from CoordinateFinderService
     */
    private val serviceCallback = object : CoordinateFinderService.ServiceCallback {
        override fun onSearchStarted() {
            Log.d(TAG, "Search started notification received")
            notifyCallbacks { it.onSearchStarted() }
        }

        override fun onSearchStopped() {
            Log.d(TAG, "Search stopped notification received")
            notifyCallbacks { it.onSearchStopped() }
        }

        override fun onSearchPaused() {
            Log.d(TAG, "Search paused notification received")
        }

        override fun onSearchResumed() {
            Log.d(TAG, "Search resumed notification received")
        }

        override fun onResultFound(result: SearchResult) {
            Log.d(TAG, "Result found: ${result.getFormattedCoordinates()}")
            notifyCallbacks { it.onResultFound(result) }
        }

        override fun onSearchError(error: String) {
            Log.e(TAG, "Search error: $error")
            notifyCallbacks { it.onSearchError(error) }
        }

        override fun onStatusUpdate(status: CoordinateFinderService.ServiceStatus) {
            Log.d(TAG, "Status update: running=${status.isRunning}, searches=${status.searchCount}")
            notifyCallbacks { it.onStatusUpdate(status) }
        }
    }

    /**
     * Bind to the CoordinateFinderService
     */
    fun bindService(): Boolean {
        if (isServiceBound.get()) {
            Log.d(TAG, "Service already bound")
            return true
        }

        return try {
            val intent = Intent(context, CoordinateFinderService::class.java)
            val success = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            
            if (success) {
                Log.d(TAG, "Service binding initiated")
            } else {
                Log.e(TAG, "Failed to bind to service")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error binding to service", e)
            false
        }
    }

    /**
     * Unbind from the CoordinateFinderService
     */
    fun unbindService() {
        if (!isServiceBound.get()) {
            Log.d(TAG, "Service not bound")
            return
        }

        try {
            coordinateFinderService?.removeCallback(serviceCallback)
            context.unbindService(serviceConnection)
            
            coordinateFinderService = null
            isServiceBound.set(false)
            
            Log.d(TAG, "Service unbound")
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding service", e)
        }
    }

    /**
     * Start the coordinate search
     */
    fun startSearch(): Boolean {
        return try {
            CoordinateFinderService.startService(context)
            Log.d(TAG, "Search start requested")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting search", e)
            false
        }
    }

    /**
     * Stop the coordinate search
     */
    fun stopSearch(): Boolean {
        return try {
            CoordinateFinderService.stopService(context)
            Log.d(TAG, "Search stop requested")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping search", e)
            false
        }
    }

    /**
     * Get current service status
     */
    fun getServiceStatus(): CoordinateFinderService.ServiceStatus? {
        return coordinateFinderService?.getStatus()
    }

    fun getService(): CoordinateFinderService? {
        return coordinateFinderService
    }

    /**
     * Check if service is bound
     */
    fun isServiceBound(): Boolean = isServiceBound.get()

    /**
     * Check if service is running
     */
    fun isServiceRunning(): Boolean = CoordinateFinderService.isRunning(context)

    /**
     * Update service settings
     */
    fun updateServiceSettings() {
        coordinateFinderService?.updateSettings()
        Log.d(TAG, "Service settings update requested")
    }

    /**
     * Add communication callback
     */
    fun addCallback(callback: ServiceCommunicationCallback) {
        callbacks.add(callback)
        Log.d(TAG, "Callback added, total: ${callbacks.size}")
    }

    /**
     * Remove communication callback
     */
    fun removeCallback(callback: ServiceCommunicationCallback) {
        callbacks.remove(callback)
        Log.d(TAG, "Callback removed, total: ${callbacks.size}")
    }

    /**
     * Notify all callbacks
     */
    private fun notifyCallbacks(action: (ServiceCommunicationCallback) -> Unit) {
        callbacks.forEach { callback ->
            try {
                action(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying callback", e)
            }
        }
    }

    /**
     * Get service connection info
     */
    fun getConnectionInfo(): ConnectionInfo {
        return ConnectionInfo(
            isServiceBound = isServiceBound.get(),
            isServiceRunning = isServiceRunning(),
            callbackCount = callbacks.size,
            serviceStatus = getServiceStatus()
        )
    }

    /**
     * Data class for connection information
     */
    data class ConnectionInfo(
        val isServiceBound: Boolean,
        val isServiceRunning: Boolean,
        val callbackCount: Int,
        val serviceStatus: CoordinateFinderService.ServiceStatus?
    )

    /**
     * Clean up resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up ServiceCommunicationManager")
        
        unbindService()
        callbacks.clear()
    }
}