# Lecturameter ProGuard rules
# Generado v2.4 — activar R8 con minifyEnabled true

# ── Data classes serializadas con Gson ───────────────────────────────────────
# Gson usa reflexión; R8 eliminaría campos si no los protegemos.
-keepclassmembers class com.lecturameter.** {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Todas las data classes del paquete principal
-keep class com.lecturameter.DateEvent { *; }
-keep class com.lecturameter.BookEdition { *; }
-keep class com.lecturameter.Book { *; }
-keep class com.lecturameter.ReadingSession { *; }
-keep class com.lecturameter.YearWrapped { *; }
-keep class com.lecturameter.BookStats { *; }
-keep class com.lecturameter.OpenLibraryResult { *; }
-keep class com.lecturameter.BookMetadata { *; }
-keep class com.lecturameter.IsbnFullMetadata { *; }
-keep class com.lecturameter.EditionResult { *; }
-keep class com.lecturameter.CycleStats { *; }
-keep class com.lecturameter.FullBackup { *; }
-keep class com.lecturameter.FullBackup$* { *; }
-keep class com.lecturameter.TutorialPage { *; }
# v2.4 rework: retos serializados con Gson (prefs "challenges")
-keep class com.lecturameter.Challenge { *; }
-keep class com.lecturameter.ChallengeType { *; }
-keepclassmembers enum com.lecturameter.ChallengeType { *; }

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
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
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
