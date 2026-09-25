package com.movie.scheduler;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.movie.R;
import com.movie.data.CategoryEntity;
import com.movie.data.MovieDatabase;

import java.util.List;

/**
 * The single WorkManager job that executes one full sync cycle.
 *
 * <p>Runs as a foreground service of type {@code dataSync} because one cycle takes 30-45 minutes;
 * a normal worker is stopped at the 10 minute mark. The notification doubles as live progress for
 * the user, and every phase change is mirrored into {@link SyncRunLog} so the result is still
 * visible after the app has been closed.</p>
 */
public class SyncWorker extends Worker {

    private static final String LOG_TAG = "SyncWorker";

    /** Tag used for {@code cancelAllWorkByTag} and {@code getWorkInfosByTag}. */
    public static final String TAG = "movie_link_sync";

    private static final String CHANNEL_ID = "movie_link_sync_channel";
    private static final int NOTIFICATION_ID = 4711;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        final Context context = getApplicationContext();
        final long startedAt = System.currentTimeMillis();

        Log.i(LOG_TAG, "Sync cycle starting");
        createChannel(context);
        publishForeground(0, "Movie Link Sync", "Preparing the sync cycle...");
        SyncStatus.publish(SyncStatus.running("Starting", "Preparing the sync cycle...", 0, 0));

        try {
            // Nothing to do if the user has not ticked a single category.
            List<CategoryEntity> enabled =
                    MovieDatabase.getDatabase(context).categoryDao().getEnabledNow();
            if (enabled == null || enabled.isEmpty()) {
                finish(context, SyncStatus.error(
                        "No category is ticked on the Settings page. Tick at least one category "
                                + "before running the scheduler.", 0, 0, startedAt));
                return Result.failure();
            }

            final ProgressSink sink = new ProgressSink() {
                private long lastNotify = 0L;

                @Override
                public void onPhase(String step, String detail) {
                    Log.i(LOG_TAG, step + " - " + detail);
                    SyncStatus.publish(SyncStatus.running(step, detail, 0, 0));
                    pushNotification(0, step, detail);
                }

                @Override
                public void onProgress(String step, int processed, int total, String detail) {
                    SyncStatus.publish(SyncStatus.running(step, detail, processed, total));
                    long now = System.currentTimeMillis();
                    if (now - lastNotify > 2000L || (total > 0 && processed >= total)) {
                        lastNotify = now;
                        int percent = total <= 0 ? 0 : (int) ((processed * 100L) / total);
                        pushNotification(percent, step, processed + "/" + total + " - " + detail);
                    }
                    if (isStopped()) {
                        throw new CancelledException();
                    }
                }
            };

            SyncOrchestrator.CycleResult result =
                    new SyncOrchestrator(context).run(startedAt, sink);

            if (result.cancelled) {
                finish(context, SyncStatus.cancelled(startedAt));
                return Result.retry();
            }

            finish(context, SyncStatus.success(result.summary, result.uploadedRows,
                    result.totalRows, startedAt));
            return Result.success();

        } catch (CancelledException ce) {
            Log.w(LOG_TAG, "Sync cycle cancelled by the system");
            finish(context, SyncStatus.cancelled(startedAt));
            return Result.retry();

        } catch (Exception e) {
            Log.e(LOG_TAG, "Sync cycle failed", e);
            String msg = e.getMessage();
            finish(context, SyncStatus.error(
                    msg == null || msg.trim().isEmpty() ? e.toString() : msg, 0, 0, startedAt));
            // A single retry, then give up - a 30-45 minute job must not loop forever.
            if (getRunAttemptCount() < 1) {
                return Result.retry();
            }
            return Result.failure();
        }
    }

    private void finish(Context context, SyncStatus status) {
        SyncStatus.publish(status);
        SyncRunLog.persistStatus(context, status);
        String detail = status.getDetail() == null || status.getDetail().isEmpty()
                ? "" : (": " + status.getDetail());
        SyncRunLog.appendLine(context,
                "===== " + status.getPhase() + " - " + status.getStep() + detail + " =====");
        cancelNotification(context);
        Log.i(LOG_TAG, "Sync cycle finished: " + status);
    }

    // ================================================================
    //  foreground service plumbing
    // ================================================================

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm == null) return;
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Movie Link Sync", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Progress of the movie link scrape and WorkDrive sync run");
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        }
    }

    private NotificationCompat.Builder builder(Context context, String title, String text, int percent) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title == null || title.isEmpty() ? "Movie Link Sync" : title)
                .setContentText(text == null ? "" : text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if (percent > 0) {
            b.setProgress(100, percent, false);
        } else {
            b.setProgress(0, 0, true);
        }
        return b;
    }

    private void publishForeground(int percent, String title, String text) {
        try {
            ForegroundInfo info;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info = new ForegroundInfo(NOTIFICATION_ID,
                        builder(getApplicationContext(), title, text, percent).build(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                info = new ForegroundInfo(NOTIFICATION_ID,
                        builder(getApplicationContext(), title, text, percent).build());
            }
            setForegroundAsync(info);
        } catch (Exception e) {
            Log.e(LOG_TAG, "setForeground failed", e);
        }
    }

    private void pushNotification(int percent, String title, String text) {
        try {
            NotificationManager nm = (NotificationManager)
                    getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            nm.notify(NOTIFICATION_ID,
                    builder(getApplicationContext(), title, text, percent).build());
        } catch (Exception e) {
            Log.e(LOG_TAG, "notify failed", e);
        }
    }

    private void cancelNotification(Context context) {
        try {
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIFICATION_ID);
        } catch (Exception ignored) {
        }
    }

    /** Thrown internally when WorkManager asks the worker to stop mid-run. */
    static class CancelledException extends RuntimeException {
        CancelledException() {
            super("Sync cancelled");
        }
    }

    /** Progress callbacks handed to the orchestrator. */
    public interface ProgressSink {
        void onPhase(String step, String detail);

        void onProgress(String step, int processed, int total, String detail);
    }
}
