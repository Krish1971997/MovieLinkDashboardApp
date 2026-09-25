package com.movie.scheduler;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Phase of the periodic sync run.  Encodes what the mini terminal and the
 * full Console page report.
 */
public class SyncStatus {

    public enum Phase {
        IDLE, QUEUED, RUNNING, SUCCESS, ERROR, CANCELLED
    }

    private final Phase phase;
    private final String step;
    private final String detail;
    private final int processed;
    private final int total;
    private final int percent;
    private final long startedAt;
    private final long finishedAt;

    public SyncStatus(Phase phase, String step, String detail,
                      int processed, int total, int percent,
                      long startedAt, long finishedAt) {
        this.phase = phase == null ? Phase.IDLE : phase;
        this.step = step == null ? "Idle" : step;
        this.detail = detail == null ? "" : detail;
        this.processed = processed;
        this.total = total;
        this.percent = percent;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public static SyncStatus idle() {
        return new SyncStatus(Phase.IDLE, "Idle", "", 0, 0, 0, 0, 0);
    }

    private static volatile SyncStatus CURRENT = idle();
    private static final MutableLiveData<SyncStatus> LIVE = new MutableLiveData<>(idle());

    /** Background-thread safe publish (worker phases/progress). */
    public static void publish(SyncStatus status) {
        if (status == null) return;
        CURRENT = status;
        LIVE.postValue(status);
    }

    /** Main-thread publish, used once at cold start to re-show the persisted status. */
    public static void publishLocal(SyncStatus status) {
        if (status == null) return;
        CURRENT = status;
        LIVE.setValue(status);
    }

    public static LiveData<SyncStatus> observe() {
        return LIVE;
    }

    public static SyncStatus running(String step, String detail, int processed, int total) {
        long startedAt = CURRENT.phase == Phase.RUNNING || CURRENT.phase == Phase.QUEUED
                ? CURRENT.startedAt : System.currentTimeMillis();
        int percent = total > 0 ? (int) Math.round(100.0 * processed / total) : 0;
        return new SyncStatus(Phase.RUNNING, step, detail, processed, total, percent, startedAt, 0);
    }

    public static SyncStatus error(String message, int processed, int total, long startedAt) {
        int percent = total > 0 ? (int) Math.round(100.0 * processed / total) : 0;
        return new SyncStatus(Phase.ERROR, "Error", message, processed, total, percent,
                startedAt, System.currentTimeMillis());
    }

    public static SyncStatus cancelled(long startedAt) {
        return new SyncStatus(Phase.CANCELLED, "Cancelled", "", 0, 0, 0,
                startedAt, System.currentTimeMillis());
    }

    public static SyncStatus success(String summary, int uploadedRows, int totalRows, long startedAt) {
        return new SyncStatus(Phase.SUCCESS, "Success", summary, uploadedRows, totalRows, 100,
                startedAt, System.currentTimeMillis());
    }

    public Phase getPhase() {
        return phase;
    }

    public String getStep() {
        return step;
    }

    public String getDetail() {
        return detail;
    }

    public int getProcessed() {
        return processed;
    }

    public int getTotal() {
        return total;
    }

    public int getPercent() {
        return percent;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public boolean isRunning() {
        return phase == Phase.RUNNING || phase == Phase.QUEUED;
    }

    public static SyncStatus current() {
        return CURRENT;
    }
}
