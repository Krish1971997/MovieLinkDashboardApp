package com.movie.scheduler;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.movie.data.ZohoPreferences;

import java.util.concurrent.TimeUnit;

/**
 * Stand-in for WorkManager-driven periodic scheduler.  Real production code
 * would enqueue a OneTimeWorkRequest / PeriodicWorkRequest.  Here we just
 * record intent in the run-log, so the Console page has something to show.
 */
public final class SyncScheduler {

    /** Unique work name for both the manual run and the periodic schedule. */
    private static final String UNIQUE_WORK_NAME = "movie_link_sync_work";

    private SyncScheduler() {}

    private static Constraints buildConstraints(Context ctx) {
        boolean wifiOnly = new ZohoPreferences(ctx).isSchedulerWifiOnly();
        return new Constraints.Builder()
                .setRequiredNetworkType(wifiOnly ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                .build();
    }

    public static void enable(Context ctx) {
        ZohoPreferences prefs = new ZohoPreferences(ctx);
        int minutes = Math.max(15, prefs.getSchedulerIntervalMinutes());
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SyncWorker.class, minutes, TimeUnit.MINUTES)
                .setConstraints(buildConstraints(ctx))
                .addTag(SyncWorker.TAG)
                .build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
        SyncRunLog.appendLine(ctx, "Automatic scheduler ENABLED (every " + minutes + " min)");
    }

    public static void disable(Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_WORK_NAME);
        SyncRunLog.appendLine(ctx, "Automatic scheduler DISABLED");
    }

    public static void rescheduleIfEnabled(Context ctx) {
        ZohoPreferences prefs = new ZohoPreferences(ctx);
        if (prefs.isSchedulerEnabled()) {
            enable(ctx);
            SyncRunLog.appendLine(ctx,
                    "Reschedule requested: every " + prefs.getSchedulerIntervalMinutes() + " min, "
                            + (prefs.isSchedulerWifiOnly() ? "Wi-Fi only" : "any network"));
        }
    }

    public static boolean isEnabled(Context ctx) {
        return new ZohoPreferences(ctx).isSchedulerEnabled();
    }

    /** Manual "Run Now" entry point. Actually enqueues SyncWorker via WorkManager. */
    public static void runNow(Context ctx) {
        ZohoPreferences prefs = new ZohoPreferences(ctx);
        SyncStatus s = new SyncStatus(
                SyncStatus.Phase.QUEUED,
                "Queued",
                "Manual run requested via Settings page",
                0, 0, 0,
                System.currentTimeMillis(), 0);
        SyncRunLog.persistStatus(ctx, s);
        SyncRunLog.incrementRunCount(ctx);
        SyncRunLog.appendLine(ctx, "===== Run #" + SyncRunLog.getRunCount(ctx) + " queued =====");
        SyncRunLog.appendLine(ctx, "Base URL       : " + prefs.getBaseUrl());
        SyncRunLog.appendLine(ctx, "File target    : " + prefs.getUploadFileName());
        SyncRunLog.appendLine(ctx, "Folder ID      : " + prefs.getFolderId());
        SyncRunLog.appendLine(ctx, "Network policy : " + (prefs.isSchedulerWifiOnly() ? "Wi-Fi only" : "any"));

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(buildConstraints(ctx))
                .addTag(SyncWorker.TAG)
                .build();
        WorkManager.getInstance(ctx).enqueueUniqueWork(
                UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    /** Cancels whatever SyncWorker run is currently enqueued or executing. */
    public static void cancelRunning(Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_WORK_NAME);
        WorkManager.getInstance(ctx).cancelAllWorkByTag(SyncWorker.TAG);
        SyncRunLog.appendLine(ctx, "Manual cancel requested");
        SyncRunLog.persistStatus(ctx, new SyncStatus(
                SyncStatus.Phase.CANCELLED,
                "Cancelled",
                "User cancelled the run",
                0, 0, 0,
                0, System.currentTimeMillis()));
    }
}
