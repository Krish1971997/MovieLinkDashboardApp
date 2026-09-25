package com.movie.repository;

import android.util.Log;

import com.movie.data.MovieRecord;
import com.movie.parser.FileImporter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Java port of ZohoSyncManager.kt
 * <p>
 * Kotlin `object ZohoSyncManager` -> Java final class with static methods.
 * `by lazy` OkHttpClient -> lazy-initialised static holder with double-checked locking.
 */
public final class ZohoSyncManager {

    private static final String TAG = "ZohoSyncManager";

    private static volatile OkHttpClient client;

    private ZohoSyncManager() {
    }

    private static OkHttpClient client() {
        if (client == null) {
            synchronized (ZohoSyncManager.class) {
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return client;
    }

    /**
     * Kotlin data class ZohoFileItem -> simple Java POJO.
     */
    public static class ZohoFileItem {
        public final String id;
        public final String name;
        public final String extension;

        public ZohoFileItem(String id, String name, String extension) {
            this.id = id;
            this.name = name;
            this.extension = extension;
        }
    }

    /**
     * Refreshes the Zoho OAuth v2 access token using the specified accounts server base URL.
     * Throws an Exception if the request fails, wrapping Zoho's error payload.
     */
    public static String refreshAccessToken(
            String accountsServer,
            String clientId,
            String clientSecret,
            String refreshToken
    ) throws Exception {

        if (isBlank(clientId)) throw new Exception("Client ID is blank.");
        if (isBlank(clientSecret)) throw new Exception("Client Secret is blank.");
        if (isBlank(refreshToken)) throw new Exception("Refresh Token is blank.");

        // Standardize accounts server url (remove trailing slash)
        String serverBase = trim(accountsServer);
        if (serverBase.endsWith("/")) {
            serverBase = serverBase.substring(0, serverBase.length() - 1);
        }
        final String url = serverBase + "/oauth/v2/token";

        RequestBody requestBody = new FormBody.Builder()
                .add("client_id", trim(clientId))
                .add("client_secret", trim(clientSecret))
                .add("refresh_token", trim(refreshToken))
                .add("grant_type", "refresh_token")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        try {
            Response response = client().newCall(request).execute();
            try {
                String bodyStr = response.body() != null ? response.body().string() : null;
                Log.d(TAG, "Refresh Token Response code: " + response.code() + ", body: " + bodyStr);

                if (bodyStr == null) {
                    throw new Exception("Auth server returned empty response (HTTP " + response.code() + ").");
                }

                String trimmed = bodyStr.trim();
                if (trimmed.startsWith("<html") || trimmed.startsWith("<!DOCTYPE")) {
                    // Extract title from HTML or keep first 200 characters
                    String titleText = extractHtmlTitle(bodyStr, "HTML error response");
                    String preview = bodyStr.length() > 150 ? bodyStr.substring(0, 150) : bodyStr;
                    throw new Exception("Auth server returned HTML instead of JSON (HTTP " + response.code() + "): " + titleText + " (" + preview + "...)");
                }

                JSONObject json;
                try {
                    json = new JSONObject(bodyStr);
                } catch (Exception pe) {
                    throw new Exception("Invalid response format received from Zoho (HTTP " + response.code() + "). Please check your endpoint or Client configuration.");
                }

                if (!response.isSuccessful()) {
                    String errorMsg = json.optString("error", null);
                    if (errorMsg == null)
                        errorMsg = json.optString("message", "HTTP status " + response.code());
                    throw new Exception("Zoho OAuth Error (" + errorMsg + ") on host: " + serverBase);
                }

                String token = json.optString("access_token", "");
                if (token.isEmpty()) {
                    String errorMsg = json.optString("error", null);
                    if (errorMsg == null) errorMsg = "No access_token field present in JSON.";
                    throw new Exception("Zoho Auth Failure: " + errorMsg);
                }

                return token;
            } finally {
                response.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing Zoho Access Token", e);
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Zoho") || msg.contains("Auth") || msg.contains("HTML"))) {
                throw e;
            }
            throw new Exception("Failed to connect/refresh Zoho token: " + (msg != null ? msg : "network error"), e);
        }
    }

    public static String getZohoDomain(String accountsServer) {
        String clean = trim(accountsServer).toLowerCase(Locale.ROOT);
        if (clean.startsWith("https://")) clean = clean.substring("https://".length());
        if (clean.startsWith("http://")) clean = clean.substring("http://".length());
        int slash = clean.indexOf('/');
        if (slash >= 0) clean = clean.substring(0, slash);

        if (clean.startsWith("accounts.")) {
            String d = clean.substring("accounts.".length());
            return d.isEmpty() ? "zoho.com" : d;
        } else {
            int i = clean.indexOf("zoho.");
            if (i >= 0) {
                return clean.substring(i);
            }
            int j = clean.indexOf("zohocloud.");
            if (j >= 0) {
                return clean.substring(j);
            }
            return "zoho.com";
        }
    }

    /**
     * Fetches files and folders info under [folderId] in Zoho Workdrive.
     */
    public static List<ZohoFileItem> fetchFilesInFolder(
            String apiBaseUrl,
            String folderId,
            String accessToken
    ) throws Exception {

        String serverBase = trim(apiBaseUrl);
        if (serverBase.endsWith("/")) {
            serverBase = serverBase.substring(0, serverBase.length() - 1);
        }
        List<ZohoFileItem> result = new ArrayList<>();
        int offset = 0;
        final int limit = 50;

        while (true) {
            // Build URL matching the working Java list API path exactly:
            // baseUrl + "/files/" + folderId + "/files" + "?" + limit + "&page%5Boffset%5D="+ offset
            String url = serverBase + "/files/" + folderId + "/files?" + limit + "&page%5Boffset%5D=" + offset;
            Log.d(TAG, "fetchFilesInFolder request URL: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Zoho-oauthtoken " + accessToken)
                    .header("Accept", "application/vnd.api+json")
                    .get()
                    .build();

            Response response = client().newCall(request).execute();
            boolean done = false;
            try {
                String bodyStr = response.body() != null ? response.body().string() : null;
                Log.d(TAG, "Fetch Files Response code: " + response.code() + ", body: "
                        + (bodyStr != null ? bodyStr.substring(0, Math.min(1000, bodyStr.length())) : null));

                if (!response.isSuccessful() || bodyStr == null) {
                    String preview = bodyStr != null
                            ? bodyStr.substring(0, Math.min(300, bodyStr.length()))
                            : "empty response body";
                    String previewTrim = preview.trim();
                    if (previewTrim.startsWith("<html") || previewTrim.startsWith("<!DOCTYPE")) {
                        String titleText = extractHtmlTitle(preview, "HTML error page");
                        throw new Exception("Zoho API returned HTML Error (HTTP " + response.code() + "): " + titleText);
                    }
                    throw new Exception("Zoho list files request failed [HTTP " + response.code() + "]: " + preview);
                }

                String bodyTrim = bodyStr.trim();
                if (bodyTrim.startsWith("<html") || bodyTrim.startsWith("<!DOCTYPE")) {
                    String titleText = extractHtmlTitle(bodyStr, "HTML error page");
                    throw new Exception("Zoho API returned HTML Error where JSON was expected (HTTP " + response.code() + "): " + titleText);
                }

                JSONObject json;
                try {
                    json = new JSONObject(bodyStr);
                } catch (Exception pe) {
                    String snippet = bodyStr.substring(0, Math.min(150, bodyStr.length()));
                    throw new Exception("Failed to parse list files API response to JSON (HTTP " + response.code() + "). Content: " + snippet + "...");
                }

                // Check for API errors inside structured JSON
                JSONArray errors = json.optJSONArray("errors");
                if (errors != null && errors.length() > 0) {
                    JSONObject firstErr = errors.optJSONObject(0);
                    String detail = null;
                    if (firstErr != null) {
                        detail = firstErr.optString("detail", "");
                        if (detail.isEmpty())
                            detail = firstErr.optString("title", "Unknown API error");
                    }
                    throw new Exception("Zoho List Files API Error: " + detail);
                }

                JSONArray dataArray = json.optJSONArray("data");
                if (dataArray == null || dataArray.length() == 0) {
                    done = true;
                } else {
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject itemObj = dataArray.optJSONObject(i);
                        if (itemObj == null) continue;
                        String id = itemObj.optString("id", "");
                        JSONObject attributes = itemObj.optJSONObject("attributes");
                        if (attributes == null) continue;

                        boolean isFolder = attributes.optBoolean("is_folder", false);
                        if (isFolder) {
                            Log.d(TAG, "Skipping nested folder: " + attributes.optString("name"));
                            continue;
                        }

                        String name = attributes.optString("name", "");
                        // Key is "extn" in the WorkDrive API attributes dictionary
                        String extension = attributes.optString("extn", "");

                        if (!id.isEmpty()) {
                            result.add(new ZohoFileItem(id, name, extension));
                        }
                    }

                    if (dataArray.length() < limit) {
                        done = true;
                    } else {
                        offset += limit;
                    }
                }
            } finally {
                response.close();
            }

            if (done) {
                return result;
            }
        }
    }

    /**
     * Downloads file content stream as raw bytes from Workdrive.
     */
    public static byte[] downloadFileContent(
            String downloadBaseUrl,
            String fileId,
            String accessToken
    ) throws Exception {

        String serverBase = trim(downloadBaseUrl);
        if (serverBase.endsWith("/")) {
            serverBase = serverBase.substring(0, serverBase.length() - 1);
        }
        String url = serverBase + "/" + fileId;
        Log.d(TAG, "downloadFileContent request URL: " + url);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Zoho-oauthtoken " + accessToken)
                .get()
                .build();

        Response response = client().newCall(request).execute();
        try {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null
                        ? response.body().string()
                        : "No body returned.";
                if (errorBody.length() > 500) errorBody = errorBody.substring(0, 500);
                Log.e(TAG, "Download failed [HTTP " + response.code() + "]: " + errorBody);
                String trimmed = errorBody.trim();
                if (trimmed.startsWith("<html") || trimmed.startsWith("<!DOCTYPE")) {
                    String titleText = extractHtmlTitle(errorBody, "HTML error");
                    throw new Exception("Download server returned HTML instead of spreadsheet bytes (HTTP " + response.code() + "): " + titleText);
                }
                throw new Exception("Failed to download file (HTTP " + response.code() + "): " + errorBody);
            }
            byte[] bytes = response.body() != null ? response.body().bytes() : null;
            if (bytes == null || bytes.length == 0) {
                throw new Exception("Received an empty file stream payload from WorkDrive download endpoint.");
            }
            return bytes;
        } finally {
            response.close();
        }
    }

    /**
     * Orchestrates Zoho Sheets Sync cycle completely, returning parsed records on success.
     * Throws explicit descriptive exceptions if failure points are reached.
     */
    public static List<MovieRecord> performSync(
            String accountsServer,
            String apiServer,
            String clientId,
            String clientSecret,
            String refreshToken,
            String folderId,
            String configFileName,
            String configExtension
    ) throws Exception {

        final String targetNameClean = trim(configFileName).toLowerCase(Locale.ROOT);
        final String targetExtClean = trim(configExtension).toLowerCase(Locale.ROOT);

        // 1. Refresh access token
        Log.i(TAG, "Step 1: Refreshing access token...");
        String accessToken = refreshAccessToken(accountsServer, clientId, clientSecret, refreshToken);

        // Resolve Region Endpoint domains dynamically based on accountsServer
        String domain = getZohoDomain(accountsServer);
        String apiBaseUrl = "https://workdrive." + domain + "/api/v1";
        String downloadBaseUrl = "https://download-accl." + domain + "/v1/workdrive/download";

        Log.d(TAG, "Resolved region-specific API endpoint: " + apiBaseUrl);
        Log.d(TAG, "Resolved region-specific Download endpoint: " + downloadBaseUrl);

        // 2. Fetch Files List
        Log.i(TAG, "Step 2: Listing files inside folder " + folderId + "...");
        List<ZohoFileItem> files = fetchFilesInFolder(apiBaseUrl, folderId, accessToken);
        if (files.isEmpty()) {
            throw new Exception("No files found or listed in Zoho Folder structure. Ensure folder ID is correct and permissions allow reading.");
        }

        // 3. Match Configured Filename
        Log.i(TAG, "Step 3: Searching matching workbook for '" + configFileName + "'...");
        ZohoFileItem matchedFile = null;

        for (ZohoFileItem file : files) {
            String fileNameClean = trim(file.name).toLowerCase(Locale.ROOT);
            String baseNameWithoutExt = fileNameClean.contains(".")
                    ? fileNameClean.substring(0, fileNameClean.lastIndexOf('.'))
                    : fileNameClean;

            String baseTargetWithoutExt = targetNameClean.contains(".")
                    ? targetNameClean.substring(0, targetNameClean.lastIndexOf('.'))
                    : targetNameClean;

            // Checks combinations for flexible matching
            boolean isExactMatch = fileNameClean.equals(targetNameClean);
            boolean isBaseNameMatch = baseNameWithoutExt.equals(baseTargetWithoutExt);
            boolean isMatchWithExtAppend = fileNameClean.equals(targetNameClean + "." + targetExtClean);

            if (isExactMatch || isBaseNameMatch || isMatchWithExtAppend) {
                matchedFile = file;
                break;
            }
        }

        if (matchedFile == null) {
            StringBuilder listedNames = new StringBuilder();
            for (int i = 0; i < files.size(); i++) {
                if (i > 0) listedNames.append(", ");
                ZohoFileItem f = files.get(i);
                listedNames.append(f.name).append(" (").append(f.extension).append(")");
            }
            throw new Exception("No file matched configuration '" + configFileName + "' with extension '"
                    + configExtension + "'. Files present in folder: [" + listedNames + "]");
        }

        // 4. Download matched sheets file
        Log.i(TAG, "Step 4: Downloading file bytes for matched file id=" + matchedFile.id + ", name=" + matchedFile.name + "...");
        byte[] fileBytes = downloadFileContent(downloadBaseUrl, matchedFile.id, accessToken);
        if (fileBytes == null) {
            throw new Exception("Failed to retrieve file payload bytes from Zoho server download stream.");
        }

        // 5. Parse downloaded spreadsheet (Excel .xlsx OR fallback CSV)
        Log.i(TAG, "Step 5: Invoking local excel parsing model on stream...");
        ByteArrayInputStream fileInputStream = new ByteArrayInputStream(fileBytes);
        String matchedLower = matchedFile.name.toLowerCase(Locale.ROOT);
        boolean isCsv = matchedLower.endsWith(".csv")
                || matchedLower.endsWith(".txt")
                || "csv".equals(matchedFile.extension.toLowerCase(Locale.ROOT));

        List<MovieRecord> records = isCsv
                ? FileImporter.parseCsv(fileInputStream)
                : FileImporter.parseXlsx(fileInputStream);

        if (records.isEmpty()) {
            throw new Exception("Spreadsheet parse completed, but extracted 0 valid records. Verify the sheet layout, headers, or workbook data.");
        }

        Log.i(TAG, "Successfully synced " + records.size() + " movie records from Zoho Sheets!");
        return records;
    }

    // -------- small private helpers (Kotlin stdlib equivalents) --------

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String extractHtmlTitle(String html, String fallback) {
        try {
            Pattern p = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m = p.matcher(html);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
}
