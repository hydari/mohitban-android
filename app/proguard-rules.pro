# R8 shrinks the libraries; the app's own code is kept unchanged (names included) so
# enum lookups by name, Room, WorkManager and backups behave exactly as in a debug build.
-keep class ir.mohitban.app.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# Room / WorkManager entry points (also covered by their consumer rules)
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
-dontwarn kotlin.reflect.jvm.internal.**
