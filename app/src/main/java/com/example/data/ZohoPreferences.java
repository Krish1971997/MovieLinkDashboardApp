package com.example.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Java port of ZohoPreferences.kt.
 * Kotlin's `var` properties become explicit getters/setters over the same SharedPreferences keys.
 */
public class ZohoPreferences {

    private static final String PREFS_NAME = "zoho_prefs";

    private final SharedPreferences prefs;

    public ZohoPreferences(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

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
}
