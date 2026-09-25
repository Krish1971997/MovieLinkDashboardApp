package com.movie.data;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * The SUBCATEGORIES string array that used to live inside MovieScraperAPI is moved here and is
 * only used ONCE, as the seed for the Room-backed "categories" table.
 *
 * <p>After first launch the scraper never reads this array again - it only reads the rows the
 * user ticked on the Settings page. Order below is exactly the order supplied by the user so the
 * list in the app matches the original array 1:1.</p>
 */
public final class DefaultCategories {

    private static final String TAG = "DefaultCategories";

    /** Paths taken verbatim from the original SUBCATEGORIES field. */
    public static final String[] SUBCATEGORIES = {
            "/tamil-2026-movies/", "/tamil-2025-movies/", "/tamil-2024-movies/",
            "/tamil-2023-movies/", "/tamil-2022-movies/", "/tamil-2021-movies/", "/tamil-2020-movies/",
            "/tamil-2019-movies/", "/tamil-2018-movies/", "/tamil-2017-movies/", "/tamil-2016-movies/",
            "/tamil-2015-movies/", "/tamil-2012-movies/", "/tamil-hd-movies-download/",
            "/thala-ajith-movies-collection-download/", "/mgr-movies-collection-download/",
            "/madhavan-movies-collection-download/", "/arjun-movies-collection-download/",
            "/jiiva-movies-collection-download/", "/jayam-ravi-movies-collection-download/",
            "/vishal-movies-collection-download/", "/silambarasan-movies-collection-download/",
            "/vijay-sethupathi-movies-collection-download/", "/dhanush-movies-collection-download/",
            "/suriya-movies-collections-download/", "/vijayakanth-movie-collections-download/",
            "/rajinikanth-movie-collections-download/", "/chiyaan-vikram-movie-collections-download/",
            "/kamal-haasan-movie-collections-download/", "/bhagyaraj-movies-collection-download/",
            "/actor-sasikumar-movies-collections/", "/actor-nakul-movies-collections/",
            "/actor-siddharth-movies-collection/", "/actor-cheran-movies-collection/",
            "/actor-vimal-movies-collection/", "/actor-vijay-movies-collection/", "/actor-ramarajan-movies-collection/",
            "/actor-simbu-movies-collection/", "/actor-sathiyaraj-movies-collection/",
            "/actor-appukutty-movies-collection/", "/actor-surya-movies-collection/",
            "/actor-murali-movies-collection/", "/actor-mohan-movies-collection/",
            "/actor-sarathkumar-movies-collection/", "/actor-bhagyaraj-movies-collection/",
            "/actor-mgr-movies-collection/", "/actor-vishal-movies-collection/",
            "/actor-vijayakanth-movies-collection/", "/actor-sivakarthikeyan-movies-collection/",
            "/actor-prashanth-movies-collection/", "/actor-prabhu-movies-collection/",
            "/actor-prabhu-deva-movies-collection/", "/actor-parthiepan-movies-collection/",
            "/actor-kamal-hassan-movies-collection/", "/actor-arjun-movies-collection/",
            "/actor-rajinikanth-movies-collection/", "/actor-madhavan-movies-collection/",
            "/actor-vikram-movie-collections/", "/actor-jeeva-movies-collection/", "/actor-dhaunsh-movies-collection/",
            "/actor-dinesh-movies-collection/", "/actor-vijay-sethupathi-movies-collection/",
            "/actor-arya-movies-collection/", "/actor-jayam-ravi-movies-collection/", "/actor-ajith-movies-collection/",
            "/actor-karthik-movies-collection/", "/actor-rajkiran-movies-collection/",
            "/actor-karthi-movies-collection/", "/actor-sivaji-ganesan-movies-collection/",
            "/actor-kunal-movies-collection/", "/tamil-movies/a/", "/tamil-movies/b/", "/tamil-movies/c/",
            "/tamil-movies/d/", "/tamil-movies/e/", "/tamil-movies/f/", "/tamil-movies/g/", "/tamil-movies/h/",
            "/tamil-movies/i/", "/tamil-movies/j/", "/tamil-movies/k/", "/tamil-movies/l/", "/tamil-movies/m/",
            "/tamil-movies/n/", "/tamil-movies/o/", "/tamil-movies/p/", "/tamil-movies/q/", "/tamil-movies/r/",
            "/tamil-movies/s/", "/tamil-movies/t/", "/tamil-movies/u/", "/tamil-movies/v/", "/tamil-movies/w/",
            "/tamil-movies/x/", "/tamil-movies/y/", "/tamil-movies/z/"
    };

    /**
     * Categories that are ticked on a fresh install.
     *
     * <p>"ellam categories load panna vendam, oru sila categories mattum than load pannanum" -
     * so the year-wise / HD categories are preselected and every actor + A-Z category starts
     * unticked. The user can tick more individually, or press "Select all".</p>
     */
    private static boolean defaultEnabled(String path) {
        if (path == null) return false;
        String p = path.toLowerCase(java.util.Locale.ROOT);
        if (p.startsWith("/tamil-") && p.contains("-movies/")) return true; // year buckets
        return p.equals("/tamil-hd-movies-download/");
    }

    private DefaultCategories() {
    }

    /** Seeds the table on first launch (and when the user asks to restore defaults). */
    public static void seedIfEmpty(Context context) {
        CategoryDao dao = MovieDatabase.getDatabase(context).categoryDao();
        try {
            if (dao.countNow() > 0) return;
            List<CategoryEntity> rows = new ArrayList<>(SUBCATEGORIES.length);
            for (int i = 0; i < SUBCATEGORIES.length; i++) {
                String path = CategoryEntity.normalizePath(SUBCATEGORIES[i]);
                rows.add(new CategoryEntity(0, path, CategoryEntity.displayNameFor(path),
                        defaultEnabled(path), i, System.currentTimeMillis()));
            }
            dao.insertAll(rows);
            Log.i(TAG, "Seeded " + rows.size() + " categories");
        } catch (Exception e) {
            Log.e(TAG, "Category seed failed", e);
        }
    }

    /** Wipes and re-inserts the original array (Settings -> "Restore default list"). */
    public static void resetToDefaults(Context context) {
        CategoryDao dao = MovieDatabase.getDatabase(context).categoryDao();
        try {
            dao.clearAll();
            List<CategoryEntity> rows = new ArrayList<>(SUBCATEGORIES.length);
            for (int i = 0; i < SUBCATEGORIES.length; i++) {
                String path = CategoryEntity.normalizePath(SUBCATEGORIES[i]);
                rows.add(new CategoryEntity(0, path, CategoryEntity.displayNameFor(path),
                        defaultEnabled(path), i, System.currentTimeMillis()));
            }
            dao.insertAll(rows);
        } catch (Exception e) {
            Log.e(TAG, "Category reset failed", e);
        }
    }
}
