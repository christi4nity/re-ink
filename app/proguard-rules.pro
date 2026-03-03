# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.reink.**$$serializer { *; }
-keepclassmembers class com.reink.** {
    *** Companion;
}
-keepclasseswithmembers class com.reink.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# OkHttp
-dontwarn okhttp3.internal.platform.**

# Readability4J
-dontwarn org.jsoup.**
-keep class net.dankito.readability4j.** { *; }

# RSS-Parser
-keep class com.prof18.rssparser.** { *; }

# WorkManager + Hilt
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker

# ZXing (QR code generation)
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Jakarta Mail (IMAP)
-keep class jakarta.mail.** { *; }
-keep class com.sun.mail.** { *; }
-dontwarn jakarta.mail.**
-dontwarn com.sun.mail.**

# Google Tink (via security-crypto)
-dontwarn com.google.errorprone.annotations.**

# SLF4J (transitive from Readability4J)
-dontwarn org.slf4j.impl.StaticLoggerBinder
