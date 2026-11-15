# WorkManager instantiates the worker reflectively from the persisted class name, so R8 must keep
# the two-argument constructor even in a fully minified release build.
-keep class dev.veneranative.updater.UpdateDownloadWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
