package com.movie.scheduler;

import android.content.Context;
import android.content.SharedPreferences;

import com.movie.util.UiUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Persists the last SyncStatus and a rolling log buffer so both the
 * dashboard mini terminal and the full Console page can render the same
 * output.
 */
public final class SyncRunLog {

    private static final String PREFS = "sync_run_log";
    private static final String KEY_LOG = "log_lines";
    private static final String KEY_COUNT = "run_count";
    private static final String KEY_LAST_PHASE = "last_phase";
    private static final String KEY_LAST_STEP = "last_step";
    private static final String KEY_LAST_DETAIL = "last_detail";
    private static final String KEY_LAST_PROCESSED = "last_processed";
    private static final String KEY_LAST_TOTAL = "last_total";
    private static final String KEY_LAST_STARTED = "last_started";
    private static final String KEY_LAST_FINISHED = "last_finished";

    /** ring buffer of recent log lines (at most 400 entries). */
    private static final int MAX_LINES = 400;

    private SyncRunLog() {}

    public static synchronized void appendLine(Context ctx, String line) {
        if (ctx == null || line == null) return;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> existing = readLines(sp);
        long ts = System.currentTimeMillis();
        String stamp = "[" + formatTime(ts) + "] ";
        existing.add(stamp + line);
        if (existing.size() > MAX_LINES) {
            existing = new ArrayList<>(existing.subList(existing.size() - MAX_LINES, existing.size()));
        }
        sp.edit().putString(KEY_LOG, joinLines(existing)).apply();
    }

    public static synchronized String snapshot(Context ctx) {
        if (ctx == null) return "";
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> lines = readLines(sp);
        return joinLines(lines);
    }

    public static synchronized List<String> lines(Context ctx) {
        if (ctx == null) return new ArrayList<>();
        return readLines(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    public static synchronized int getRunCount(Context ctx) {
        if (ctx == null) return 0;
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_COUNT, 0);
    }

    public static synchronized void incrementRunCount(Context ctx) {
        if (ctx == null) return;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putInt(KEY_COUNT, sp.getInt(KEY_COUNT, 0) + 1).apply();
    }

    public static synchronized void persistStatus(Context ctx, SyncStatus s) {
        if (ctx == null || s == null) return;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_LAST_PHASE, s.getPhase().name())
                .putString(KEY_LAST_STEP, s.getStep())
                .putString(KEY_LAST_DETAIL, s.getDetail())
                .putInt(KEY_LAST_PROCESSED, s.getProcessed())
                .putInt(KEY_LAST_TOTAL, s.getTotal())
                .putLong(KEY_LAST_STARTED, s.getStartedAt())
                .putLong(KEY_LAST_FINISHED, s.getFinishedAt())
                .apply();
    }

    public static synchronized SyncStatus load(Context ctx) {
        if (ctx == null) return SyncStatus.idle();
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SyncStatus.Phase phase;
        try {
            phase = SyncStatus.Phase.valueOf(sp.getString(KEY_LAST_PHASE, SyncStatus.Phase.IDLE.name()));
        } catch (Exception e) {
            phase = SyncStatus.Phase.IDLE;
        }
        int processed = sp.getInt(KEY_LAST_PROCESSED, 0);
        int total = sp.getInt(KEY_LAST_TOTAL, 0);
        int percent = total > 0 ? (int) Math.round(100.0 * processed / total) : 0;
        return new SyncStatus(
                phase,
                sp.getString(KEY_LAST_STEP, "Idle"),
                sp.getString(KEY_LAST_DETAIL, ""),
                processed,
                total,
                percent,
                sp.getLong(KEY_LAST_STARTED, 0),
                sp.getLong(KEY_LAST_FINISHED, 0)
        );
    }

    public static String formatTime(long ts) {
        if (ts <= 0) return "--";
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(ts));
    }

    public static String formatDateTime(long ts) {
        if (ts <= 0) return "--";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(ts));
    }

    public static void copyToClipboard(Context ctx) {
        UiUtils.copyToClipboard(ctx, snapshot(ctx), "Scheduler Log");
    }

    private static List<String> readLines(SharedPreferences sp) {
        String raw = sp.getString(KEY_LOG, "");
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String l : raw.split("\n")) {
            if (l != null && !l.isEmpty()) out.add(l);
        }
        return out;
    }

    private static String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append('\n');
        return sb.toString();
    }

    public static synchronized void clearLog(Context ctx) {
        if (ctx == null) return;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().remove(KEY_LOG).apply();
    }
}
