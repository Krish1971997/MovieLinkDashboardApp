package com.movie.scheduler;

import android.content.Context;
import android.util.Log;

import com.movie.data.CategoryEntity;
import com.movie.data.Movie;
import com.movie.data.MovieDatabase;
import com.movie.data.MovieRecord;
import com.movie.data.MovieRecordDao;
import com.movie.data.ZohoPreferences;
import com.movie.parser.ExcelWorkbookWriter;
import com.movie.parser.FileImporter;
import com.movie.scraper.MovieScraperAPI;
import com.movie.scraper.UploadFileAPI;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The scheduler pipeline, in the exact order requested:
 *
 * <pre>
 *  1. Read the WHOLE workbook that lives in WorkDrive.
 *  2. Keep only the rows whose category is TICKED, and delete those ticked categories
 *     from the workbook copy (everything else is carried over untouched).
 *  3. Scrape ONLY the ticked categories.
 *  4. Upload the merged workbook back to WorkDrive.
 *  5. Wipe the local database and re-sync it from the workbook that was just uploaded.
 * </pre>
 *
 * <p>Steps 1-4 are the "excel read -> delete ticked -> scrape ticked -> upload" half; step 5 is the
 * final "delete all existing data and resync" half.</p>
 */
public class SyncOrchestrator {

    private static final String TAG = "SyncOrchestrator";

    private final Context context;
    private final ZohoPreferences prefs;
    private final MovieRecordDao movieDao;

    public SyncOrchestrator(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = new ZohoPreferences(this.context);
        this.movieDao = MovieDatabase.getDatabase(this.context).movieDao();
    }

    public static class CycleResult {
        public int totalRows;
        public int uploadedRows;
        public int scrapedRows;
        public int enabledCategories;
        public boolean cancelled;
        public String summary = "";
    }

    public CycleResult run(long startedAt, SyncWorker.ProgressSink sink) throws Exception {
        CycleResult result = new CycleResult();

        List<CategoryEntity> enabledCategories =
                MovieDatabase.getDatabase(context).categoryDao().getEnabledNow();
        if (enabledCategories == null) enabledCategories = new ArrayList<>();
        result.enabledCategories = enabledCategories.size();
        sink.onPhase("Ticked categories", enabledCategories.size() + " category(ies) selected");

        Set<String> enabledKeys = new LinkedHashSet<>();
        for (CategoryEntity c : enabledCategories) {
            enabledKeys.add(c.getCategoryKey());
        }

        // ----------------------------------------------------------------
        // STEP 0 - auth + locate the workbook
        // ----------------------------------------------------------------
        sink.onPhase("Authenticating", "Refreshing Zoho OAuth token...");
        String accessToken = UploadFileAPI.refreshAccessToken(context);

        String workbookName = prefs.getUploadFileName();
        sink.onPhase("Locating workbook", "Searching WorkDrive folder for " + workbookName + "...");
        UploadFileAPI.WorkDriveFile remoteFile =
                UploadFileAPI.findFile(context, accessToken, workbookName);

        // ----------------------------------------------------------------
        // STEP 1 - read the WHOLE workbook (or start from the local DB on first run)
        // ----------------------------------------------------------------
        sink.onPhase("Step 1/5 - Reading workbook",
                remoteFile == null ? "No remote workbook yet - seeding from local data."
                        : "Downloading " + remoteFile.name + "...");

        List<MovieRecord> workbookRows = new ArrayList<>();
        if (remoteFile != null) {
            byte[] bytes = UploadFileAPI.downloadFile(context, accessToken, remoteFile.id);
            sink.onPhase("Step 1/5 - Reading workbook",
                    "Parsing " + bytes.length + " bytes...");
            boolean isCsv = remoteFile.name.toLowerCase(Locale.ROOT).endsWith(".csv");
            workbookRows = isCsv
                    ? FileImporter.parseCsv(new ByteArrayInputStream(bytes))
                    : FileImporter.parseXlsx(new ByteArrayInputStream(bytes));
            Log.i(TAG, "Workbook contained " + workbookRows.size() + " rows");
        } else {
            workbookRows = movieDao.getAllMoviesNow();
            if (workbookRows == null) workbookRows = new ArrayList<>();
            Log.i(TAG, "Seeded workbook from local DB: " + workbookRows.size() + " rows");
        }

        // ----------------------------------------------------------------
        // STEP 2 - delete only the TICKED categories from the merged workbook
        //          (unticked categories are carried over untouched)
        // ----------------------------------------------------------------
        List<MovieRecord> keptRows = new ArrayList<>();
        int removedRows = 0;
        for (MovieRecord r : workbookRows) {
            if (r == null) continue;
            if (isTicked(r.getCategory(), enabledKeys)) {
                removedRows++;
                continue;
            }
            keptRows.add(r);
        }
        sink.onPhase("Step 2/5 - Removed ticked categories",
                "Dropped " + removedRows + " row(s), kept " + keptRows.size() + " row(s)");
        sink.onProgress("Step 2/5 - Removed ticked categories", 1, 1,
                removedRows + " row(s) removed");

        // ----------------------------------------------------------------
        // STEP 3 - scrape ONLY the ticked categories
        // ----------------------------------------------------------------
        List<Movie> scraped = new ArrayList<>();
        int categoryIndex = 0;
        for (CategoryEntity category : enabledCategories) {
            if (Thread.currentThread().isInterrupted()) {
                result.cancelled = true;
                return result;
            }
            categoryIndex++;
            final int currentCategory = categoryIndex;
            final String categoryPath = category.getPath();
            final int total = enabledCategories.size();
            sink.onProgress("Step 3/5 - Scraping", currentCategory - 1, total, categoryPath);

            try {
                List<Movie> found = MovieScraperAPI.scrapeCategory(context, categoryPath,
                        new MovieScraperAPI.ProgressListener() {
                            @Override
                            public void onMovie(int index, String name) {
                                sink.onProgress("Step 3/5 - Scraping",
                                        currentCategory - 1, total,
                                        categoryPath + " -> " + name);
                            }
                        });
                scraped.addAll(found);
                sink.onProgress("Step 3/5 - Scraping", currentCategory, total,
                        categoryPath + " -> " + found.size() + " row(s)");
            } catch (SyncWorker.CancelledException ce) {
                result.cancelled = true;
                return result;
            } catch (Exception e) {
                // One broken category must not kill a 30-45 minute run.
                Log.e(TAG, "Category failed: " + categoryPath, e);
                sink.onPhase("Step 3/5 - Scraping",
                        categoryPath + " failed: " + e.getMessage());
            }
        }
        result.scrapedRows = scraped.size();

        // ----------------------------------------------------------------
        // STEP 4 - upload the merged workbook back to WorkDrive
        // ----------------------------------------------------------------
        LinkedHashMap<String, MovieRecord> merged = new LinkedHashMap<>();
        for (MovieRecord r : keptRows) {
            merged.put(keyOf(r), r);
        }
        for (Movie m : scraped) {
            if (m == null || !m.isValid()) continue;
            MovieRecord r = m.toRecord();
            merged.put(keyOf(r), r);
        }
        List<MovieRecord> finalRows = new ArrayList<>(merged.values());
        result.totalRows = finalRows.size();
        result.uploadedRows = finalRows.size();

        sink.onPhase("Step 4/5 - Uploading workbook",
                "Building " + workbookName + " with " + finalRows.size() + " row(s)...");
        byte[] outBytes = ExcelWorkbookWriter.writeMovies(finalRows);
        UploadFileAPI.uploadFile(context, accessToken, outBytes, workbookName);
        sink.onPhase("Step 4/5 - Uploaded", workbookName + " (" + outBytes.length + " bytes)");

        // ----------------------------------------------------------------
        // STEP 5 - wipe local data and re-sync from the uploaded workbook
        // ----------------------------------------------------------------
        sink.onPhase("Step 5/5 - Resyncing",
                "Clearing local database and re-importing " + finalRows.size() + " row(s)...");
        movieDao.clearAll();
        if (!finalRows.isEmpty()) {
            movieDao.insertAll(finalRows);
        }
        sink.onProgress("Step 5/5 - Resynced", finalRows.size(), finalRows.size(),
                "Local index rebuilt");

        result.summary = "Scraped " + result.scrapedRows + " row(s) from "
                + result.enabledCategories + " ticked category(ies); workbook now holds "
                + result.totalRows + " row(s); local index resynced.";
        return result;
    }

    /** True when this row's category is one of the ticked categories. */
    private boolean isTicked(String rowCategory, Set<String> enabledKeys) {
        if (rowCategory == null || rowCategory.trim().isEmpty()) return false;
        String key = CategoryEntity.displayNameFor(
                CategoryEntity.normalizePath(rowCategory)).toLowerCase(Locale.ROOT);
        if (enabledKeys.contains(key)) return true;
        // Tolerate rows stored as /tamil-2026-movies/ or tamil-2026-movies
        String raw = rowCategory.trim().toLowerCase(Locale.ROOT);
        if (raw.startsWith("/")) raw = raw.substring(1);
        if (raw.endsWith("/")) raw = raw.substring(0, raw.length() - 1);
        return enabledKeys.contains(raw);
    }

    /** Dedup key: category + sublink, falling back to name. */
    private String keyOf(MovieRecord r) {
        String category = CategoryEntity.displayNameFor(
                CategoryEntity.normalizePath(r.getCategory())).toLowerCase(Locale.ROOT);
        String sublink = r.getSublink() == null ? "" : r.getSublink().trim().toLowerCase(Locale.ROOT);
        String name = r.getName() == null ? "" : r.getName().trim().toLowerCase(Locale.ROOT);
        return category + "|" + (sublink.isEmpty() ? name : sublink);
    }
}
