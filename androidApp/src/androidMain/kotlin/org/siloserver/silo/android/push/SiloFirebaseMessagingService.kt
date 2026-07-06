package org.siloserver.silo.android.push

class PushMessageHandler(
    private val presenter: PushNotificationPresenter,
) {
    suspend fun handle(data: Map<String, String>): Boolean {
        val deliveryId = data[DELIVERY_ID_KEY]?.takeIf { it.isNotBlank() } ?: return false
        presenter.present(deliveryId)
        return true
    }

    companion object {
        const val DELIVERY_ID_KEY = "delivery_id"
    }
}

/**
 * Firebase is intentionally not linked in this build yet. This wrapper keeps
 * data-only message handling testable and ready for a future
 * FirebaseMessagingService subclass once the dependency/config is present.
 */
class SiloFirebaseMessagingService(
    private val handler: PushMessageHandler,
) {
    suspend fun handleData(data: Map<String, String>): Boolean =
        handler.handle(data)
}
