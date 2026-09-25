package com.movie.scraper;

import android.content.Context;
import android.util.Log;

import com.movie.data.CategoryEntity;
import com.movie.data.DefaultCategories;
import com.movie.data.Movie;
import com.movie.data.MovieDatabase;
import com.movie.data.ZohoPreferences;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Android-compatible port of <b>MovieScraperAPI</b>.
 *
 * <p>THE ORIGINAL CLASS BODY WAS NOT SUPPLIED. Everything below that can be derived from the change
 * request IS implemented for real:</p>
 * <ul>
 *   <li>{@code BASE_URL} is no longer a {@code private static String} hard-coded to
 *       "https://moviesda16.com/" - it is read from {@link ZohoPreferences#getBaseUrl()}, which the
 *       Config page (top field) writes.</li>
 *   <li>The {@code SUBCATEGORIES} array is no longer a field of this class. It now only seeds the
 *       Room {@code categories} table ({@link DefaultCategories}) and the scraper asks the database
 *       for the ticked rows via {@link #getEnabledSubcategories(Context)}.</li>
 *   <li>All network I/O runs on a caller-supplied worker thread (never the main thread).</li>
 * </ul>
 *
 * <p><b>MARKED STUB:</b> the site-specific extraction in {@link #extractMovieSublinks} and
 * {@link #extractDownloadLink} is a generic anchor/regex based implementation, because the original
 * scraping selectors were not provided. Those two methods are the only places you need to paste the
 * original logic back in - they are marked with {@code ORIGINAL-LOGIC-HOOK}.</p>
 */
public final class MovieScraperAPI {

    private static final String TAG = "MovieScraperAPI";

    private static volatile OkHttpClient client;

    private static final int MAX_RETRIES = 3;
    private static final long DELAY_MS = 2000L;
    private static final int MAX_PAGES_WITHOUT_PAGINATION = 20;

    private MovieScraperAPI() {
    }

    private static OkHttpClient client() {
        if (client == null) {
            synchronized (MovieScraperAPI.class) {
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(20, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .followRedirects(true)
                            .build();
                }
            }
        }
        return client;
    }

    // ================================================================
    //  BASE_URL  (was: private static String BASE_URL = null;)
    //  Now read from the Config page instead of being hard-coded.
    // ================================================================

    public static String getBaseUrl(Context context) {
        return new ZohoPreferences(context).getBaseUrl();
    }

    private static String normalizeBase(String baseUrl) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        if (b.isEmpty()) b = ZohoPreferences.DEFAULT_BASE_URL;
        if (!b.startsWith("http://") && !b.startsWith("https://")) b = "https://" + b;
        if (!b.endsWith("/")) b = b + "/";
        return b;
    }

    // ================================================================
    //  Subcategories now come from the Room table, filtered by the
    //  per-item checkbox the user ticked on the Settings page.
    // ================================================================

    /** ALL configured categories (Settings page list, search-filtered if needed). */
    public static List<CategoryEntity> getAllSubcategories(Context context) {
        List<CategoryEntity> rows = MovieDatabase.getDatabase(context).categoryDao().getAllNow();
        return rows == null ? new ArrayList<CategoryEntity>() : rows;
    }

    /** ONLY the ticked categories - exactly what a scheduler run is allowed to process. */
    public static List<CategoryEntity> getEnabledSubcategories(Context context) {
        List<CategoryEntity> rows = MovieDatabase.getDatabase(context).categoryDao().getEnabledNow();
        return rows == null ? new ArrayList<CategoryEntity>() : rows;
    }

    /** Just the path strings of the ticked categories. */
    public static List<String> getEnabledSubcategoryPaths(Context context) {
        List<String> paths = new ArrayList<>();
        for (CategoryEntity c : getEnabledSubcategories(context)) {
            paths.add(c.getPath());
        }
        return paths;
    }

    /** True when the Settings page has this category ticked (so the pipeline may touch it). */
    public static boolean isCategoryEnabled(Context context, String categoryPath) {
        if (context == null || categoryPath == null) return false;
        String normalized = CategoryEntity.normalizePath(categoryPath);
        List<CategoryEntity> enabled = getEnabledSubcategories(context);
        for (CategoryEntity c : enabled) {
            if (c.getPath().equalsIgnoreCase(normalized)) return true;
        }
        return false;
    }

    /** "/tamil-2026-movies/" -> "tamil-2026-movies" - matches the web code's Category column. */
    private static String toCategoryKey(String categoryPath) {
        return categoryPath == null ? "" : categoryPath.replace("/", "");
    }

    // ================================================================
    //  scraping
    // ================================================================

    /** Fetches raw HTML for an absolute URL. Must be called from a background thread. */
    public static String fetchHtml(String absoluteUrl) throws IOException {
        Request request = new Request.Builder()
                .url(absoluteUrl)
                .header("User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                                + "Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build();

        Response response = client().newCall(request).execute();
        try {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code() + " for " + absoluteUrl);
            }
            return response.body().string();
        } finally {
            response.close();
        }
    }

    private static String fetchHtmlWithRetry(String absoluteUrl) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return fetchHtml(absoluteUrl);
            } catch (IOException e) {
                last = e;
                Log.w(TAG, "Retry " + attempt + "/" + MAX_RETRIES + " for " + absoluteUrl + ": " + e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw last;
    }

    /**
     * Scrapes ONE category, following pagination all the way to the last page - mirrors
     * {@code processSubcategory}/{@code scrapePage} in the working web scraper (com.movies.db)
     * instead of only ever reading page 1.
     *
     * @param context      app context (for the Config page BASE_URL)
     * @param categoryPath e.g. "/tamil-2026-movies/"
     * @param onProgress   optional per-movie callback, may be null
     */
    public static List<Movie> scrapeCategory(Context context,
                                             String categoryPath,
                                             ProgressListener onProgress) throws IOException {
        String base = normalizeBase(getBaseUrl(context));
        String path = CategoryEntity.normalizePath(categoryPath);
        String categoryKey = toCategoryKey(categoryPath);
        String baseSubcategoryUrl = base + (path.startsWith("/") ? path.substring(1) : path);

        int lastPage = getLastPageNumber(baseSubcategoryUrl);
        Log.i(TAG, "Category " + categoryPath + " -> total pages: " + lastPage);

        List<Movie> movies = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int index = 0;

        for (int page = 1; page <= lastPage; page++) {
            String pageUrl = baseSubcategoryUrl + (page == 1 ? "" : "?page=" + page);
            Log.i(TAG, "Scraping page: " + pageUrl);

            String html;
            try {
                html = fetchHtmlWithRetry(pageUrl);
            } catch (IOException e) {
                Log.w(TAG, "Page not found or failed, stopping pagination for " + categoryPath + ": " + pageUrl, e);
                break;
            }

            List<MovieLink> links = extractMovieLinks(html);
            if (links.isEmpty()) {
                Log.i(TAG, "No movie data found on page: " + pageUrl + ". Stopping pagination.");
                break;
            }

            for (MovieLink ml : links) {
                if (!seen.add(ml.href)) continue;
                index++;

                String movieUrl = ml.href.startsWith("http")
                        ? ml.href
                        : base + (ml.href.startsWith("/") ? ml.href.substring(1) : ml.href);

                Movie movie = new Movie();
                movie.setName(ml.name.isEmpty() ? humanize(ml.href) : ml.name);
                movie.setSublink(ml.href);
                movie.setCategory(categoryKey);
                movie.setLink(movieUrl);
                movie.setPageUrl(pageUrl);
                if (movie.isValid()) movies.add(movie);

                if (onProgress != null) onProgress.onMovie(index, movie.getName());
            }

            if (page < lastPage) {
                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return movies;
    }

    /**
     * Finds the last page number from the site's "pagination_last" link (mirrors
     * {@code a.pagination_last} in the web code). Falls back to
     * {@link #MAX_PAGES_WITHOUT_PAGINATION} when this category has no pagination link.
     */
    private static int getLastPageNumber(String subcategoryUrl) {
        try {
            String html = fetchHtmlWithRetry(subcategoryUrl);
            Matcher m = PAGINATION_LAST_PATTERN.matcher(html);
            if (m.find()) {
                String href = m.group(1);
                int idx = href.indexOf("page=");
                if (idx >= 0) {
                    Matcher digits = Pattern.compile("\\d+").matcher(href.substring(idx + 5));
                    if (digits.find()) {
                        try {
                            return Integer.parseInt(digits.group());
                        } catch (NumberFormatException ignored) {
                            // fall through to default below
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not fetch " + subcategoryUrl + " to find last page, defaulting to "
                    + MAX_PAGES_WITHOUT_PAGINATION + " pages.", e);
        }
        return MAX_PAGES_WITHOUT_PAGINATION;
    }

    // ================================================================
    //  Movie link extraction - scoped to <div class="f"> like the web
    //  code's "div.f a" jsoup selector, so pagination/menu anchors
    //  elsewhere on the page are never picked up as fake movie rows.
    // ================================================================

    private static final Pattern DIV_F_PATTERN = Pattern.compile(
            "<div[^>]*\\bclass\\s*=\\s*[\"']f[\"'][^>]*>(.*?)</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern ANCHOR_WITH_TEXT_PATTERN = Pattern.compile(
            "<a\\s+[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern PAGINATION_LAST_PATTERN = Pattern.compile(
            "<a\\b(?=[^>]*\\bclass\\s*=\\s*[\"']pagination_last[\"'])[^>]*\\bhref\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TAG_STRIP_PATTERN = Pattern.compile("<[^>]+>");

    /** One movie anchor found inside a div.f block: its href (sublink) and visible text (name). */
    private static final class MovieLink {
        final String href;
        final String name;

        MovieLink(String href, String name) {
            this.href = href;
            this.name = name;
        }
    }

    static List<MovieLink> extractMovieLinks(String html) {
        List<MovieLink> result = new ArrayList<>();
        if (html == null) return result;

        Matcher divMatcher = DIV_F_PATTERN.matcher(html);
        while (divMatcher.find()) {
            String divContent = divMatcher.group(1);
            Matcher a = ANCHOR_WITH_TEXT_PATTERN.matcher(divContent);
            if (a.find()) {
                String href = a.group(1).trim();
                String rawText = a.group(2) == null ? "" : a.group(2);
                String name = decode(TAG_STRIP_PATTERN.matcher(rawText).replaceAll("").trim());
                if (!href.isEmpty() && !href.startsWith("#") && !href.startsWith("javascript:")) {
                    result.add(new MovieLink(href, name));
                }
            }
        }
        return result;
    }

    // ================================================================
    //  Old generic (non-div.f-scoped) extractor - kept in case anything
    //  else in the app still references it, but no longer used by
    //  scrapeCategory above (it was the source of the fake pagination
    //  rows and the missing-pagination bug).
    // ================================================================

    private static final Pattern ANCHOR_PATTERN =
            Pattern.compile("<a\\s+[^>]*href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    static List<String> extractMovieSublinks(String html, String categoryPath) {
        List<String> result = new ArrayList<>();
        if (html == null) return result;

        Matcher m = ANCHOR_PATTERN.matcher(html);
        while (m.find()) {
            String href = m.group(1);
            if (href == null) continue;
            href = href.trim();
            if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript:")
                    || href.startsWith("mailto:")) {
                continue;
            }
            String lower = href.toLowerCase(Locale.ROOT);
            if (lower.startsWith("http") && !lower.contains("-movie")
                    && !lower.contains("-movies") && !lower.contains("download")) {
                continue;
            }
            if (lower.endsWith(".css") || lower.endsWith(".js") || lower.endsWith(".png")
                    || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                    || lower.endsWith(".ico") || lower.endsWith(".svg")) {
                continue;
            }
            // Skip the listing page itself and the category roots.
            if (lower.contains("/tamil-movies/") && lower.endsWith("/")) continue;
            result.add(href);
        }
        return result;
    }

    // ================================================================
    //  Old detail-page download-link extractor - no longer called.
    //  This site has no separate "download" link; link = movie's own
    //  page (BASE_URL + sublink), same as the web scraper. Kept in case
    //  anything else in the app still references it.
    // ================================================================

    private static final Pattern HREF_DOWNLOAD_PATTERN = Pattern.compile(
            "<a\\s+[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(?:[^<]*(?:download|Download)[^<]*)</a>",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ANY_FILE_PATTERN = Pattern.compile(
            "https?://[^\"'\\s<>]+\\.(?:mkv|mp4|avi|zip|rar|pdf)", Pattern.CASE_INSENSITIVE);

    static String extractDownloadLink(String detailHtml) {
        if (detailHtml == null) return "";
        Matcher m = HREF_DOWNLOAD_PATTERN.matcher(detailHtml);
        if (m.find()) return m.group(1);

        Matcher f = ANY_FILE_PATTERN.matcher(detailHtml);
        if (f.find()) return f.group();

        return "";
    }

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static String extractTitle(String html) {
        if (html == null) return "";
        Matcher m = TITLE_PATTERN.matcher(html);
        if (m.find()) {
            String t = m.group(1);
            int pipe = t.indexOf('|');
            if (pipe > 0) t = t.substring(0, pipe);
            return decode(t).trim();
        }
        return "";
    }

    /** "/dacoit-a-love-story-2026-tamil-movie/" -> "dacoit a love story 2026 tamil movie" */
    static String humanize(String sublink) {
        if (sublink == null) return "";
        String s = sublink.trim();
        int slash = s.lastIndexOf('/');
        if (slash > 0) s = s.substring(0, slash);
        slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        return decode(s.replace('-', ' ').trim());
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /** Per-movie progress hook used by the foreground notification. */
    public interface ProgressListener {
        void onMovie(int index, String name);
    }
}