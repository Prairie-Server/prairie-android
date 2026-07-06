package org.siloserver.silo.android.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get

class PushMessageHandler(
    private val presenter: PushNotificationPresenter,
) {
    suspend fun handle(
        data: Map<String, String>,
        fallbackTitle: String? = null,
        fallbackBody: String? = null,
    ): Boolean {
        val deliveryId = deliveryIdFrom(data) ?: return false
        presenter.present(
            deliveryId = deliveryId,
            fallbackTitle = fallbackTitle ?: data["title"],
            fallbackBody = fallbackBody ?: data["body"],
        )
        return true
    }

    private fun deliveryIdFrom(data: Map<String, String>): String? =
        DELIVERY_ID_KEYS.firstNotNullOfOrNull { key ->
            data[key]?.trim()?.takeIf { it.isNotEmpty() }
        }

    companion object {
        const val DELIVERY_ID_KEY = "delivery_id"
        private val DELIVERY_ID_KEYS = listOf(DELIVERY_ID_KEY, "deliveryId", "deliveryID", "id")
    }
}

class SiloFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val handler = runCatching { get<PushMessageHandler>(PushMessageHandler::class.java) }.getOrNull()
            ?: return
        serviceScope.launch {
            handler.handle(
                data = message.data,
                fallbackTitle = message.notification?.title,
                fallbackBody = message.notification?.body,
            )
        }
    }

    override fun onNewToken(token: String) {
        val registrar = runCatching { get<AndroidPushRegistrar>(AndroidPushRegistrar::class.java) }.getOrNull()
            ?: return
        serviceScope.launch {
            runCatching { registrar.registerToken(token) }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
