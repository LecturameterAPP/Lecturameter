package com.lecturameter.utils

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * P-035: aviso in-app de actualización con la In-App Updates API de Google Play,
 * modalidad FLEXIBLE (el usuario sigue usando la app mientras la nueva versión se
 * descarga en segundo plano; solo se le pide reiniciar cuando ya está lista).
 *
 * A prueba de fallos por diseño: si algo va mal (app instalada fuera de Play, sin
 * Google Play Services, sin red, dispositivo sin Play Store...) todas las llamadas
 * caen en un catch silencioso y no hacen NADA. La app tiene que funcionar exactamente
 * igual con o sin Play — ningún fallo de esta clase puede molestar al usuario ni
 * crashear la app.
 *
 * Uso (ver MainActivity.kt): se instancia como campo de la Activity, igual que
 * scanIsbnLauncher/cameraPermLauncher, porque registerForActivityResult debe
 * llamarse antes de que la Activity llegue a STARTED.
 *   - checkForUpdate() en onCreate (no bloquea el arranque: todo es async).
 *   - onResume() en onResume, para retomar el aviso si la descarga ya terminó
 *     en una sesión anterior y el usuario no reinició.
 *   - onDestroy() en onDestroy, para des-registrar el listener y evitar fugas.
 */
class InAppUpdate(private val activity: ComponentActivity) {

    /** true cuando la descarga FLEXIBLE ha terminado y toca reiniciar para instalarla. */
    val updateReadyToInstall = mutableStateOf(false)

    private var appUpdateManager: AppUpdateManager? = null
    private var listenerRegistered = false

    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            updateReadyToInstall.value = true
        }
    }

    // Debe registrarse antes de STARTED, igual que el resto de launchers de MainActivity.
    private val updateLauncher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            // El resultado del intent no se usa: el progreso real se sigue con el
            // InstallStateUpdatedListener (que también cubre al usuario cancelando
            // el diálogo de confirmación y reintentando más tarde).
        }

    /** Comprueba si hay actualización disponible y lanza el flujo FLEXIBLE si procede. */
    fun checkForUpdate() {
        try {
            val manager = AppUpdateManagerFactory.create(activity)
            appUpdateManager = manager
            registerListener(manager)
            manager.appUpdateInfo
                .addOnSuccessListener { info -> handleUpdateInfo(manager, info) }
                .addOnFailureListener { /* sin red / sin Play: silencioso, no molestar */ }
        } catch (_: Throwable) {
            // Sin Play Store, sin Google Play Services, dispositivo sideload, etc.
        }
    }

    /** Llamar desde onResume: retoma el aviso si la descarga ya se completó. */
    fun onResume() {
        try {
            val manager = appUpdateManager ?: return
            manager.appUpdateInfo.addOnSuccessListener { info ->
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    updateReadyToInstall.value = true
                }
            }
        } catch (_: Throwable) {
        }
    }

    /** Instala la actualización ya descargada (reinicia la app). Llamar desde la acción del Snackbar. */
    fun completeUpdate() {
        try {
            appUpdateManager?.completeUpdate()
        } catch (_: Throwable) {
        } finally {
            updateReadyToInstall.value = false
        }
    }

    /** Llamar desde onDestroy: evita fugas de memoria del InstallStateUpdatedListener. */
    fun onDestroy() {
        try {
            if (listenerRegistered) {
                appUpdateManager?.unregisterListener(listener)
                listenerRegistered = false
            }
        } catch (_: Throwable) {
        }
    }

    private fun registerListener(manager: AppUpdateManager) {
        if (listenerRegistered) return
        manager.registerListener(listener)
        listenerRegistered = true
    }

    private fun handleUpdateInfo(
        manager: AppUpdateManager,
        info: com.google.android.play.core.appupdate.AppUpdateInfo
    ) {
        try {
            when {
                info.installStatus() == InstallStatus.DOWNLOADED -> {
                    // Descarga completada en una sesión anterior y el usuario no reinició.
                    updateReadyToInstall.value = true
                }
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                    val options = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                    manager.startUpdateFlowForResult(info, updateLauncher, options)
                }
            }
        } catch (_: Throwable) {
            // Fallo al evaluar/lanzar el flujo de actualización: silencioso.
        }
    }
}
