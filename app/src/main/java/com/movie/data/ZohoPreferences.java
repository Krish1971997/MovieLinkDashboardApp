package com.movie.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Java port of ZohoPreferences.kt, extended for this change request.
 *
 * <p>Existing keys (reused exactly as before, no new keys created for them):
 * client_id, client_secret, refresh_token, folder_id.</p>
 *
 * <p>NEW keys added here (these are the values that used to be hard-coded
 * {@code private static final} fields inside UploadFileAPI / MovieScraperAPI):
 * base_url, zoho_accounts_url, workdrive_api_url, workdrive_list_url,
 * workdrive_download_url, scheduler_enabled, scheduler_interval_minutes,
 * scheduler_wifi_only.</p>
 */
public class ZohoPreferences {

    private static final String PREFS_NAME = "zoho_prefs";

    // ---- defaults for the NEW configurable endpoints ----
    public static final String DEFAULT_BASE_URL = "https://moviesda16.com/";
    public static final String DEFAULT_ZOHO_ACCOUNTS_URL = "https://accounts.zoho.com/oauth/v2/token";
    public static final String DEFAULT_WORKDRIVE_API_URL = "https://workdrive.zoho.com/api/v1/upload";
    public static final String DEFAULT_WORKDRIVE_LIST_URL = "https://www.zohoapis.com/workdrive/api/v1/files/";
    public static final String DEFAULT_WORKDRIVE_DOWNLOAD_URL = "https://download-accl.zoho.com/v1/workdrive/download/";

    public static final int DEFAULT_SCHEDULER_INTERVAL_MINUTES = 360;
    public static final int MIN_SCHEDULER_INTERVAL_MINUTES = 15;

    private final SharedPreferences prefs;

    public ZohoPreferences(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ================================================================
    //  EXISTING fields (unchanged keys) - populated from the app
    // ================================================================

    public String getClientId() {
        return prefs.getString("client_id", "");
    }

    public void setClientId(String value) {
        prefs.edit().putString("client_id", value == null ? "" : value).apply();
    }

    public String getClientSecret() {
        return prefs.getString("client_secret", "");
    }

    public void setClientSecret(String value) {
        prefs.edit().putString("client_secret", value == null ? "" : value).apply();
    }

    public String getRefreshToken() {
        return prefs.getString("refresh_token", "");
    }

    public void setRefreshToken(String value) {
        prefs.edit().putString("refresh_token", value == null ? "" : value).apply();
    }

    public String getFolderId() {
        return prefs.getString("folder_id", "");
    }

    public void setFolderId(String value) {
        prefs.edit().putString("folder_id", value == null ? "" : value).apply();
    }

    public String getFileName() {
        return prefs.getString("file_name", "movies");
    }

    public void setFileName(String value) {
        prefs.edit().putString("file_name", value == null ? "" : value).apply();
    }

    public String getDefaultExtension() {
        return prefs.getString("default_extension", "xlsx");
    }

    public void setDefaultExtension(String value) {
        prefs.edit().putString("default_extension", value == null ? "" : value).apply();
    }

    public String getAccountsServer() {
        return prefs.getString("accounts_server", "https://accounts.zoho.com");
    }

    public void setAccountsServer(String value) {
        prefs.edit().putString("accounts_server", value == null ? "" : value).apply();
    }

    public String getApiServer() {
        return prefs.getString("api_server", "https://workdrive.zohoapis.com");
    }

    public void setApiServer(String value) {
        prefs.edit().putString("api_server", value == null ? "" : value).apply();
    }

    public boolean isClearOldDataBeforeUpload() {
        return prefs.getBoolean("clear_old_data", true);
    }

    public void setClearOldDataBeforeUpload(boolean value) {
        prefs.edit().putBoolean("clear_old_data", value).apply();
    }

    public boolean isAppLockEnabled() {
        return prefs.getBoolean("is_app_lock_enabled", false);
    }

    public void setAppLockEnabled(boolean value) {
        prefs.edit().putBoolean("is_app_lock_enabled", value).apply();
    }

    public String getAppLockPasscode() {
        return prefs.getString("app_lock_passcode", "1234");
    }

    public void setAppLockPasscode(String value) {
        prefs.edit().putString("app_lock_passcode", value == null ? "" : value).apply();
    }

    // ================================================================
    //  NEW: MovieScraperAPI.BASE_URL  (shown at the TOP of the config page)
    //      was: private static String BASE_URL = null; // "https://moviesda16.com/"
    // ================================================================

    public String getBaseUrl() {
        return prefs.getString("base_url", DEFAULT_BASE_URL);
    }

    public void setBaseUrl(String value) {
        prefs.edit().putString("base_url", (value == null || value.trim().isEmpty()) ? DEFAULT_BASE_URL : value.trim()).apply();
    }

    // ================================================================
    //  NEW: the 4 UploadFileAPI URLs
    //  (added inside the "Generic region settings" section of the config page)
    // ================================================================

    public String getZohoAccountsUrl() {
        return prefs.getString("zoho_accounts_url", DEFAULT_ZOHO_ACCOUNTS_URL);
    }

    public void setZohoAccountsUrl(String value) {
        prefs.edit().putString("zoho_accounts_url", emptyTo(value, DEFAULT_ZOHO_ACCOUNTS_URL)).apply();
    }

    public String getWorkdriveApiUrl() {
        return prefs.getString("workdrive_api_url", DEFAULT_WORKDRIVE_API_URL);
    }

    public void setWorkdriveApiUrl(String value) {
        prefs.edit().putString("workdrive_api_url", emptyTo(value, DEFAULT_WORKDRIVE_API_URL)).apply();
    }

    public String getWorkdriveListUrl() {
        return prefs.getString("workdrive_list_url", DEFAULT_WORKDRIVE_LIST_URL);
    }

    public void setWorkdriveListUrl(String value) {
        prefs.edit().putString("workdrive_list_url", emptyTo(value, DEFAULT_WORKDRIVE_LIST_URL)).apply();
    }

    public String getWorkdriveDownloadUrl() {
        return prefs.getString("workdrive_download_url", DEFAULT_WORKDRIVE_DOWNLOAD_URL);
    }

    public void setWorkdriveDownloadUrl(String value) {
        prefs.edit().putString("workdrive_download_url", emptyTo(value, DEFAULT_WORKDRIVE_DOWNLOAD_URL)).apply();
    }

    // ================================================================
    //  NEW: scheduler state
    // ================================================================

    /**
     * Master ON/OFF switch for the automatic (periodic) scheduler.
     */
    public boolean isSchedulerEnabled() {
        return prefs.getBoolean("scheduler_enabled", false);
    }

    public void setSchedulerEnabled(boolean value) {
        prefs.edit().putBoolean("scheduler_enabled", value).apply();
    }

    public int getSchedulerIntervalMinutes() {
        int v = prefs.getInt("scheduler_interval_minutes", DEFAULT_SCHEDULER_INTERVAL_MINUTES);
        return v < MIN_SCHEDULER_INTERVAL_MINUTES ? MIN_SCHEDULER_INTERVAL_MINUTES : v;
    }

    public void setSchedulerIntervalMinutes(int value) {
        prefs.edit().putInt("scheduler_interval_minutes", Math.max(MIN_SCHEDULER_INTERVAL_MINUTES, value)).apply();
    }

    public boolean isSchedulerWifiOnly() {
        return prefs.getBoolean("scheduler_wifi_only", true);
    }

    public void setSchedulerWifiOnly(boolean value) {
        prefs.edit().putBoolean("scheduler_wifi_only", value).apply();
    }

    /**
     * Convenience: the exact workbook name that will be uploaded back to WorkDrive.
     */
    public String getUploadFileName() {
        String base = getFileName();
        if (base == null || base.trim().isEmpty()) base = "movies";
        String ext = getDefaultExtension();
        if (ext == null || ext.trim().isEmpty()) ext = "xlsx";
        base = base.trim();
        if (base.toLowerCase(java.util.Locale.ROOT).endsWith("." + ext.toLowerCase(java.util.Locale.ROOT))) {
            return base;
        }
        return base + "." + ext;
    }

    private static String emptyTo(String value, String fallback) {
        return (value == null || value.trim().isEmpty()) ? fallback : value.trim();
    }
}
