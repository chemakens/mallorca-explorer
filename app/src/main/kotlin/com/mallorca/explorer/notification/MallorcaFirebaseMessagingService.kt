package com.mallorca.explorer.notification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mallorca.explorer.core.data.auth.UserCloudDataSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MallorcaFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var userCloudDataSource: UserCloudDataSource

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Se llama cuando el token FCM cambia (primera vez o tras rotar).
     * Guardamos el token en Firestore para que la Cloud Function pueda usarlo.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("FCM nuevo token: ${token.take(20)}...")
        val uid = firebaseAuth.currentUser?.uid ?: return
        serviceScope.launch {
            userCloudDataSource.saveFcmToken(uid, token)
        }
    }

    /**
     * Mensajes recibidos con la app en primer plano (foreground).
     * FCM ya muestra la notificación automáticamente en background.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Timber.d("FCM mensaje recibido: ${remoteMessage.notification?.title}")
        // FCM ya gestiona las notificaciones en background automáticamente.
        // En foreground podríamos mostrarla manualmente si fuera necesario,
        // pero para eventos del día siguiente el usuario normalmente no tendrá la app abierta.
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
