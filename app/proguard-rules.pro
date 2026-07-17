# ── B-031 (RESUELTO 16-07-2026): R8 rompia Compose en release ─────────────────
# Sintoma: el Bingo mataba el proceso y los Retos no seleccionaban SOLO en release
# (corrupcion del SlotTable: AIOOBE en SlotTableKt.key / Stack.pop en Recomposer).
# Bisecado en emulador (matriz completa en MD6): hacen falta EXACTAMENTE dos piezas,
#   1. esta regla: RENOMBRAR las clases de la app dispara el bug (las libs pueden
#      ofuscarse enteras; keepnames mantiene shrinking, solo conserva nombres), y
#   2. android.enableR8.fullMode=false en gradle.properties.
# Con ambas: minify+shrink+optimize+ofuscacion de libs = estable (0 FATAL, bingo y
# retos verificados en release real, emulador API 34). NO reactivar fullMode ni quitar
# esta regla sin repetir la prueba de release instalado: en debug NUNCA se reproduce.
-keepnames class com.lecturameter.** { *; }

# Lecturameter ProGuard rules
# Generado v2.4 — activar R8 con minifyEnabled true

# ── Data classes serializadas con Gson ───────────────────────────────────────
# Gson usa reflexión; R8 eliminaría campos si no los protegemos.
-keepclassmembers class com.lecturameter.** {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Fase 1.1: los modelos serializados viven ahora en com.lecturameter.model.
# CRÍTICO para release: sin esto R8 ofusca los campos y Gson escribe/lee JSON
# con nombres ofuscados → backups y prefs ilegibles.
-keep class com.lecturameter.model.** { *; }
-keepclassmembers enum com.lecturameter.model.** { *; }
# Data classes que siguen en el paquete raíz (SearchRepository/BackupRepository)
-keep class com.lecturameter.OpenLibraryResult { *; }
-keep class com.lecturameter.BookMetadata { *; }
-keep class com.lecturameter.IsbnFullMetadata { *; }
-keep class com.lecturameter.EditionResult { *; }
-keep class com.lecturameter.CycleStats { *; }
-keep class com.lecturameter.FullBackup { *; }
-keep class com.lecturameter.FullBackup$* { *; }
-keep class com.lecturameter.TutorialPage { *; }
# v2.4 rework: retos serializados con Gson (prefs "challenges") — ahora en model/ (regla de arriba)

# ── Google APIs / Drive ───────────────────────────────────────────────────────
-keep class com.google.api.** { *; }
-keep class com.google.apis.** { *; }
-dontwarn com.google.api.**
-dontwarn com.google.apis.**

# ── Google Play Services Auth ─────────────────────────────────────────────────
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

# ── WorkManager ───────────────────────────────────────────────────────────────
-keep class androidx.work.** { *; }
-keepclassmembers class * extends androidx.work.Worker { *; }
-keepclassmembers class * extends androidx.work.CoroutineWorker { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── ML Kit (barcode scanner) ──────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── CameraX ───────────────────────────────────────────────────────────────────
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ── Coroutines ────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── Coil ──────────────────────────────────────────────────────────────────────
-dontwarn coil.**

# ── Gson internals ────────────────────────────────────────────────────────────
# Crash v2.5 Play Store (crash lm en play store.md): R8 quitaba las firmas
# genéricas de los TypeToken anónimos → IllegalStateException al arrancar.
# Signature ya estaba; se añaden las subclases anónimas de TypeToken (regla
# necesaria con R8 full mode de AGP 8.x) e InnerClasses/EnclosingMethod.
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── Compose / Reflection ─────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# ── FileProvider ──────────────────────────────────────────────────────────────
-keep class androidx.core.content.FileProvider

# ── Widget (AppWidgetProvider) ────────────────────────────────────────────────
-keep public class com.lecturameter.widget.** extends android.appwidget.AppWidgetProvider

# ── Glance ────────────────────────────────────────────────────────────────────
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# ── P-035: Play In-App Updates (app-update-ktx) ───────────────────────────────
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**
