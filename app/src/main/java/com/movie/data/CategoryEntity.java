package com.movie.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.Locale;

/**
 * Mirrors the original Kotlin SUBCATEGORIES list so the dashboard can be
 * configured from the UI instead of being hard-coded.
 */
@Entity(tableName = "categories")
public class CategoryEntity {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    private String path;

    @NonNull
    private String displayName;

    private boolean enabled;

    private int position;

    private long updatedAt;

    @Ignore
    public CategoryEntity(@NonNull String path) {
        this(0, path, displayNameFor(path), true, 0, System.currentTimeMillis());
    }

    @Ignore
    public CategoryEntity(@NonNull String path, boolean enabled) {
        this(0, path, displayNameFor(path), enabled, 0, System.currentTimeMillis());
    }

    public CategoryEntity(int id,
                          @NonNull String path,
                          @NonNull String displayName,
                          boolean enabled,
                          int position,
                          long updatedAt) {
        this.id = id;
        this.path = path == null ? "" : path;
        this.displayName = displayName == null ? displayNameFor(this.path) : displayName;
        this.enabled = enabled;
        this.position = position;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @NonNull
    public String getPath() {
        return path;
    }

    public void setPath(@NonNull String path) {
        this.path = path == null ? "" : path;
        // keep displayName in sync
        this.displayName = displayNameFor(this.path);
    }

    // FIX: Room DB expects getDisplayName() for private String displayName
    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    // Additional Helper (in case other files call entity.getName())
    @NonNull
    public String getName() {
        return displayName;
    }

    public void setDisplayName(@NonNull String displayName) {
        this.displayName = displayName == null ? displayNameFor(this.path) : displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    /**
     * Stable lookup key for matching a workbook row's category text to this entity.
     */
    @NonNull
    public String getCategoryKey() {
        return displayName.toLowerCase(Locale.ROOT);
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Always store as /path/ with leading and trailing slashes.
     */
    public static String normalizePath(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        if (!trimmed.startsWith("/")) trimmed = "/" + trimmed;
        if (!trimmed.endsWith("/")) trimmed = trimmed + "/";
        return trimmed;
    }

    /**
     * Derives a human friendly display name from a /path/ like "/tamil-2026-movies/".
     */
    public static String displayNameFor(String path) {
        if (path == null) return "";
        String p = path.trim();
        if (p.startsWith("/")) p = p.substring(1);
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p.isEmpty() ? "Unnamed" : p;
    }
}