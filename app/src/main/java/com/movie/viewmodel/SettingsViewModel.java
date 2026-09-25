package com.movie.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.movie.data.CategoryDao;
import com.movie.data.CategoryEntity;
import com.movie.data.MovieDatabase;
import com.movie.data.ZohoPreferences;
import com.movie.scheduler.SyncRunLog;
import com.movie.scheduler.SyncStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns scheduler settings + category CRUD.  Mirrors the Kotlin
 * SettingsViewModel.kt that the old code referenced.
 */
public class SettingsViewModel extends AndroidViewModel {

    private final ZohoPreferences prefs;
    private final CategoryDao categoryDao;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<SyncStatus> syncStatus = new MutableLiveData<>(SyncStatus.idle());

    /** hard-coded fallback list that "Restore default" reloads. */
    private static final List<String> DEFAULT_CATEGORIES = Collections.unmodifiableList(Arrays.asList(
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
            "/kamal-haasan-movie-collections-download/", "/bhagyaraj-movie-collections-download/",
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
    ));

    public SettingsViewModel(@NonNull Application app) {
        super(app);
        this.prefs = new ZohoPreferences(app);
        this.categoryDao = MovieDatabase.getDatabase(app).categoryDao();
        // Seed the default category list the first time the app is opened.
        seedDefaultsIfEmpty();
        // Forward persisted status into the LiveData on app start.
        syncStatus.postValue(SyncRunLog.load(app));
    }

    // ---------------- scheduler prefs ----------------

    public boolean isSchedulerEnabled() {
        return prefs.isSchedulerEnabled();
    }

    public void setSchedulerEnabled(boolean enabled) {
        prefs.setSchedulerEnabled(enabled);
    }

    public int getSchedulerIntervalMinutes() {
        return prefs.getSchedulerIntervalMinutes();
    }

    public void setSchedulerIntervalMinutes(int minutes) {
        prefs.setSchedulerIntervalMinutes(minutes);
    }

    public boolean isSchedulerWifiOnly() {
        return prefs.isSchedulerWifiOnly();
    }

    public void setSchedulerWifiOnly(boolean wifiOnly) {
        prefs.setSchedulerWifiOnly(wifiOnly);
    }

    // ---------------- categories ----------------

    public LiveData<List<CategoryEntity>> observeCategories() {
        return categoryDao.observeAll();
    }

    public void addCategory(final String path) {
        final String normalized = CategoryEntity.normalizePath(path);
        if (normalized.length() <= 2) return;
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                categoryDao.insert(new CategoryEntity(normalized, false /* new entry is UNTICKED */));
            }
        });
    }

    public void updateCategory(final CategoryEntity c) {
        c.setUpdatedAt(System.currentTimeMillis());
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                categoryDao.update(c);
            }
        });
    }

    public void deleteCategory(final CategoryEntity c) {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                categoryDao.delete(c);
            }
        });
    }

    public void setCategoryEnabled(final CategoryEntity c, boolean enabled) {
        c.setEnabled(enabled);
        c.setUpdatedAt(System.currentTimeMillis());
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                categoryDao.update(c);
            }
        });
    }

    public void setAllCategoriesEnabled(final boolean enabled) {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                categoryDao.setAllEnabled(enabled);
            }
        });
    }

    public void restoreDefaultCategories() {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                categoryDao.clearAll();
                List<CategoryEntity> list = new ArrayList<>();
                for (int i = 0; i < DEFAULT_CATEGORIES.size(); i++) {
                    String path = CategoryEntity.normalizePath(DEFAULT_CATEGORIES.get(i));
                    list.add(new CategoryEntity(0, path, CategoryEntity.displayNameFor(path),
                            true, i, System.currentTimeMillis()));
                }
                categoryDao.insertAll(list);
            }
        });
    }

    private void seedDefaultsIfEmpty() {
        // run on a worker thread inside Room via the LiveData; we don't block here.
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (categoryDao.count() == 0) {
                    List<CategoryEntity> list = new ArrayList<>();
                    for (int i = 0; i < DEFAULT_CATEGORIES.size(); i++) {
                        String path = CategoryEntity.normalizePath(DEFAULT_CATEGORIES.get(i));
                        list.add(new CategoryEntity(0, path, CategoryEntity.displayNameFor(path),
                                true, i, System.currentTimeMillis()));
                    }
                    categoryDao.insertAll(list);
                }
            }
        }).start();
    }

    // ---------------- sync status ----------------

    public LiveData<SyncStatus> observeSyncStatus() {
        return syncStatus;
    }

    /** Replays the last persisted status when the user re-enters the tab. */
    public void refreshPersistedStatus() {
        SyncStatus s = SyncRunLog.load(getApplication());
        syncStatus.postValue(s);
    }
}
