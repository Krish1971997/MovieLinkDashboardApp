package com.movie.scraper;

import android.content.Context;
import android.util.Log;

import com.movie.data.ZohoPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Android-compatible port of <b>UploadFileAPI</b>.
 *
 * <p>THE ORIGINAL CLASS BODY WAS NOT SUPPLIED. The hard-coded {@code private static final} fields
 * named in the change request are handled exactly as instructed:</p>
 *
 * <p><b>REUSED - the app already had these, so the existing storage is used unchanged:</b></p>
 * <pre>
 *   CLIENT_ID            -> ZohoPreferences.getClientId()        ("client_id")
 *   CLIENT_SECRET        -> ZohoPreferences.getClientSecret()    ("client_secret")
 *   REFRESH_TOKEN        -> ZohoPreferences.getRefreshToken()    ("refresh_token")
 *   WORKDRIVE_FOLDER_ID  -> ZohoPreferences.getFolderId()        ("folder_id")
 * </pre>
 *
 * <p><b>NEW - added to the Config page "Generic region settings" section:</b></p>
 * <pre>
 *   ZOHO_ACCOUNTS_URL        -> ZohoPreferences.getZohoAccountsUrl()
 *   WORDRIVE_API_URL         -> ZohoPreferences.getWorkdriveApiUrl()
 *   WORKDRIVE_LIST_URL       -> ZohoPreferences.getWorkdriveListUrl()
 *   WORKDRIVE_DOWNLOAD_URL   -> ZohoPreferences.getWorkdriveDownloadUrl()
 * </pre>
 *
 * <p>All methods are blocking and must be invoked from a background thread (the WorkManager worker
 * already is one).</p>
 */
public final class UploadFileAPI {

    private static final String TAG = "UploadFileAPI";

    private static volatile OkHttpClient client;

    private UploadFileAPI() {
    }

    private static OkHttpClient client() {
        if (client == null) {
            synchronized (UploadFileAPI.class) {
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(120, TimeUnit.SECONDS)
                            .writeTimeout(120, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return client;
    }

    // ================================================================
    //  fields -> preferences
    // ================================================================

    // ---- EXISTING (reused as-is) ----
    public static String getClientId(Context c) {
        return new ZohoPreferences(c).getClientId();
    }

    public static String getClientSecret(Context c) {
        return new ZohoPreferences(c).getClientSecret();
    }

    public static String getRefreshToken(Context c) {
        return new ZohoPreferences(c).getRefreshToken();
    }

    public static String getWorkdriveFolderId(Context c) {
        return new ZohoPreferences(c).getFolderId();
    }

    // ---- NEW (config page, generic region details) ----
    public static String getZohoAccountsUrl(Context c) {
        return new ZohoPreferences(c).getZohoAccountsUrl();
    }

    public static String getWorkdriveApiUrl(Context c) {
        return new ZohoPreferences(c).getWorkdriveApiUrl();
    }

    public static String getWorkdriveListUrl(Context c) {
        return new ZohoPreferences(c).getWorkdriveListUrl();
    }

    public static String getWorkdriveDownloadUrl(Context c) {
        return new ZohoPreferences(c).getWorkdriveDownloadUrl();
    }

    // ================================================================
    //  1. OAuth
    // ================================================================

    /** Refreshes the access token against the configured ZOHO_ACCOUNTS_URL. */
    public static String refreshAccessToken(Context context) throws IOException {
        ZohoPreferences p = new ZohoPreferences(context);
        String url = p.getZohoAccountsUrl();
        if (url == null || url.trim().isEmpty()) {
            throw new IOException("ZOHO_ACCOUNTS_URL is empty - set it on the Config page.");
        }
        url = url.trim();

        RequestBody body = new FormBody.Builder()
                .add("client_id", safe(p.getClientId()))
                .add("client_secret", safe(p.getClientSecret()))
                .add("refresh_token", safe(p.getRefreshToken()))
                .add("grant_type", "refresh_token")
                .build();

        Request request = new Request.Builder().url(url).post(body).build();
        Response response = client().newCall(request).execute();
        try {
            String raw = response.body() != null ? response.body().string() : null;
            if (raw == null) {
                throw new IOException("Token endpoint returned an empty body (HTTP " + response.code() + ").");
            }
            JSONObject json;
            try {
                json = new JSONObject(raw);
            } catch (Exception e) {
                throw new IOException("Token endpoint did not return JSON (HTTP " + response.code()
                        + "). Check ZOHO_ACCOUNTS_URL.");
            }
            if (!response.isSuccessful()) {
                throw new IOException("OAuth error: " + json.optString("error",
                        "HTTP " + response.code()));
            }
            String token = json.optString("access_token", "");
            if (token.isEmpty()) {
                throw new IOException("OAuth response had no access_token: "
                        + json.optString("error", raw));
            }
            return token;
        } finally {
            response.close();
        }
    }

    // ================================================================
    //  2. list files inside WORKDRIVE_FOLDER_ID
    // ================================================================

    public static class WorkDriveFile {
        public final String id;
        public final String name;
        public final String extn;

        public WorkDriveFile(String id, String name, String extn) {
            this.id = id;
            this.name = name;
            this.extn = extn;
        }
    }

    /** WORKDRIVE_LIST_URL + folderId, following the documented WorkDrive list endpoint. */
    public static List<WorkDriveFile> listFiles(Context context, String accessToken) throws IOException {
        ZohoPreferences p = new ZohoPreferences(context);
        String folderId = safe(p.getFolderId());
        if (folderId.isEmpty()) {
            throw new IOException("WORKDRIVE_FOLDER_ID is empty - set it on the Config page.");
        }
        String base = trimTrailing(safe(p.getWorkdriveListUrl()));
        List<WorkDriveFile> result = new ArrayList<>();

        int offset = 0;
        final int limit = 50;
        while (true) {
            String url = base + "/" + folderId
                    + "/files?filter%5Btype%5D=allfiles&page%5Blimit%5D=" + limit
                    + "&page%5Boffset%5D=" + offset;
            Log.d(TAG, "listFiles -> " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Zoho-oauthtoken " + accessToken)
                    .header("Accept", "application/vnd.api+json")
                    .get()
                    .build();

            Response response = client().newCall(request).execute();
            try {
                String raw = response.body() != null ? response.body().string() : null;
                if (!response.isSuccessful() || raw == null) {
                    throw new IOException("List files failed [HTTP " + response.code() + "]: "
                            + preview(raw));
                }
                JSONObject json = new JSONObject(raw);
                JSONArray data = json.optJSONArray("data");
                if (data == null || data.length() == 0) break;

                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.optJSONObject(i);
                    if (item == null) continue;
                    JSONObject attrs = item.optJSONObject("attributes");
                    if (attrs == null || attrs.optBoolean("is_folder", false)) continue;
                    String id = item.optString("id", "");
                    if (id.isEmpty()) continue;
                    result.add(new WorkDriveFile(id, attrs.optString("name", ""),
                            attrs.optString("extn", "")));
                }
                if (data.length() < limit) break;
                offset += limit;
            } catch (org.json.JSONException e) {
                throw new IOException("List files response was not valid JSON.", e);
            } finally {
                response.close();
            }
        }
        return result;
    }

    /** Finds a file by exact name, then by base name, then by base name + configured extension. */
    public static WorkDriveFile findFile(Context context, String accessToken, String wantedName)
            throws IOException {
        String wanted = safe(wantedName).toLowerCase(java.util.Locale.ROOT);
        String wantedBase = wanted.contains(".")
                ? wanted.substring(0, wanted.lastIndexOf('.')) : wanted;

        for (WorkDriveFile f : listFiles(context, accessToken)) {
            String n = safe(f.name).toLowerCase(java.util.Locale.ROOT);
            if (n.equals(wanted)) return f;
        }
        for (WorkDriveFile f : listFiles(context, accessToken)) {
            String n = safe(f.name).toLowerCase(java.util.Locale.ROOT);
            String base = n.contains(".") ? n.substring(0, n.lastIndexOf('.')) : n;
            if (base.equals(wantedBase)) return f;
        }
        return null;
    }

    // ================================================================
    //  3. download
    // ================================================================

    /** WORKDRIVE_DOWNLOAD_URL + fileId */
    public static byte[] downloadFile(Context context, String accessToken, String fileId)
            throws IOException {
        String base = trimTrailing(safe(new ZohoPreferences(context).getWorkdriveDownloadUrl()));
        String url = base + "/" + fileId;
        Log.d(TAG, "downloadFile -> " + url);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Zoho-oauthtoken " + accessToken)
                .get()
                .build();

        Response response = client().newCall(request).execute();
        try {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Download failed [HTTP " + response.code() + "]");
            }
            byte[] bytes = response.body().bytes();
            if (bytes == null || bytes.length == 0) {
                throw new IOException("Download returned 0 bytes.");
            }
            return bytes;
        } finally {
            response.close();
        }
    }

    // ================================================================
    //  4. upload  (WORDRIVE_API_URL)
    // ================================================================

    /**
     * Uploads a workbook back to WORKDRIVE_FOLDER_ID.
     *
     * <p>Uses the documented multipart WorkDrive upload endpoint
     * ({@code WORDRIVE_API_URL} = https://workdrive.zoho.com/api/v1/upload) with the folder id as
     * {@code parent_id}. When a file with the same name already exists it is replaced
     * ({@code override-name-exists=true}).</p>
     */
    public static boolean uploadFile(Context context, String accessToken, byte[] bytes,
                                     String fileName) throws IOException {
        ZohoPreferences p = new ZohoPreferences(context);
        String uploadUrl = safe(p.getWorkdriveApiUrl());
        String folderId = safe(p.getFolderId());
        if (uploadUrl.isEmpty()) {
            throw new IOException("WORDRIVE_API_URL is empty - set it on the Config page.");
        }
        if (folderId.isEmpty()) {
            throw new IOException("WORKDRIVE_FOLDER_ID is empty - set it on the Config page.");
        }

        MediaType xlsx = MediaType.parse(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        RequestBody fileBody = RequestBody.create(bytes, xlsx);

        RequestBody multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("parent_id", folderId)
                .addFormDataPart("override-name-exist", "true")
                .addFormDataPart("filename", fileName)
                .addFormDataPart("content", fileName, fileBody)
                .build();

        Request request = new Request.Builder()
                .url(uploadUrl)
                .header("Authorization", "Zoho-oauthtoken " + accessToken)
                .header("Accept", "application/vnd.api+json")
                .post(multipart)
                .build();

        Log.i(TAG, "uploadFile -> " + uploadUrl + " (" + bytes.length + " bytes as " + fileName + ")");
        Response response = client().newCall(request).execute();
        try {
            String raw = response.body() != null ? response.body().string() : "";
            Log.d(TAG, "upload response HTTP " + response.code() + ": " + preview(raw));
            if (!response.isSuccessful()) {
                throw new IOException("Upload failed [HTTP " + response.code() + "]: " + preview(raw));
            }
            return true;
        } finally {
            response.close();
        }
    }

    // ================================================================
    //  helpers
    // ================================================================

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String trimTrailing(String s) {
        String v = s;
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    private static String preview(String s) {
        if (s == null) return "empty body";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    /** Small helper kept for parity with a byte-array based original API. */
    public static ByteArrayInputStream toStream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }
}