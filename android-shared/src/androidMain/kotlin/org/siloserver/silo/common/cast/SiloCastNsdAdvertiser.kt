package org.siloserver.silo.common.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import org.siloserver.silo.cast.SiloCastProtocol
import org.siloserver.silo.common.pairing.PairingDeviceId

class SiloCastNsdAdvertiser(
    context: Context,
    private val nameProvider: () -> String = {
        Build.MODEL?.trim()?.ifBlank { null } ?: "Android TV"
    },
    private val deviceIdProvider: () -> String = {
        PairingDeviceId.stable(context)
    },
) {
    private val appContext = context.applicationContext
    private val nsdManager: NsdManager =
        appContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var registrationListener: NsdManager.RegistrationListener? = null

    @Synchronized
    fun start(port: Int) {
        stop()
        val name = nameProvider()
        val deviceId = deviceIdProvider()
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = name
            serviceType = SiloCastProtocol.serviceType
            this.port = port
            setAttribute("v", SiloCastProtocol.version.toString())
            setAttribute("name", name)
            setAttribute("deviceId", deviceId)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "SiloCast registered on ${info.port}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "SiloCast registration failed: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "SiloCast unregistered")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "SiloCast unregistration failed: $errorCode")
            }
        }
        registrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    @Synchronized
    fun stop() {
        registrationListener?.let { listener ->
            runCatching { nsdManager.unregisterService(listener) }
        }
        registrationListener = null
    }

    private companion object {
        const val TAG = "SiloCastNsdAdvertiser"
    }
}
