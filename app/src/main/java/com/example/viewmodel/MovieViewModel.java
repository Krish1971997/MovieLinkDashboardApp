package com.example.viewmodel;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.example.data.MovieDatabase;
import com.example.data.MovieRecord;
import com.example.data.MovieRecordDao;
import com.example.data.ZohoPreferences;
import com.example.parser.FileImporter;
import com.example.repository.MovieRepository;
import com.example.repository.ZohoSyncManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Java port of MovieViewModel.kt
 *
 * Kotlin mapping:
 *  - StateFlow<T>            -> MutableLiveData<T> / LiveData<T>
 *  - combine(...).stateIn    -> MediatorLiveData with three sources
 *  - viewModelScope.launch   -> single-thread background ExecutorService
 *  - Dispatchers.IO          -> the executor
 *  - withContext(Dispatchers.IO) { ... } -> executor task with post-back to main via LiveData
 */
public class MovieViewModel extends ViewModel {

    private static final String TAG = "MovieViewModel";

    private final MovieRepository repository;
    private final MovieRecordDao dao;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MutableLiveData<String> selectedCategory = new MutableLiveData<>(null);
    private final MutableLiveData<ImportingState> importingState = new MutableLiveData<>(ImportingState.idle());

    private final LiveData<List<MovieRecord>> allMovies;
    private final LiveData<List<String>> categories;
    private final MediatorLiveData<List<MovieRecord>> filteredMovies = new MediatorLiveData<>();

    public MovieViewModel(MovieRepository repository) {
        this.repository = repository;
        this.dao = repository.getMovieDao();
        this.allMovies = repository.getAllMovies();

        // val categories = allMovies.map { ... }.stateIn(...)
        // Built with MediatorLiveData instead of Transformations.map, because the
        // lifecycle 2.8.4 Java overload expects a Kotlin Function1.
        final MediatorLiveData<List<String>> categoriesLive = new MediatorLiveData<>();
        categoriesLive.addSource(allMovies, new Observer<List<MovieRecord>>() {
            @Override
            public void onChanged(List<MovieRecord> movies) {
                Set<String> distinct = new LinkedHashSet<>();
                if (movies != null) {
                    for (MovieRecord m : movies) {
                        if (m.getCategory() != null && !m.getCategory().isEmpty()) {
                            distinct.add(m.getCategory());
                        }
                    }
                }
                List<String> sorted = new ArrayList<>(distinct);
                Collections.sort(sorted);
                categoriesLive.setValue(sorted);
            }
        });
        categoriesLive.setValue(new ArrayList<String>());
        this.categories = categoriesLive;

        // combine(allMovies, searchQuery, selectedCategory) { ... }
        filteredMovies.addSource(allMovies, new Observer<List<MovieRecord>>() {
            @Override
            public void onChanged(List<MovieRecord> movies) {
                recomputeFilters();
            }
        });
        filteredMovies.addSource(searchQuery, new Observer<String>() {
            @Override
            public void onChanged(String q) {
                recomputeFilters();
            }
        });
        filteredMovies.addSource(selectedCategory, new Observer<String>() {
            @Override
            public void onChanged(String c) {
                recomputeFilters();
            }
        });
        filteredMovies.setValue(new ArrayList<MovieRecord>());

        preloadSampleMoviesIfEmpty();
    }

    private void recomputeFilters() {
        List<MovieRecord> movies = allMovies.getValue();
        String query = searchQuery.getValue() == null ? "" : searchQuery.getValue();
        String category = selectedCategory.getValue();

        List<MovieRecord> result = new ArrayList<>();
        if (movies != null) {
            for (MovieRecord movie : movies) {
                boolean matchesCategory = category == null
                        || movie.getCategory().equalsIgnoreCase(category);
                boolean matchesSearch = query.isEmpty()
                        || movie.getName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
                        || movie.getCategory().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
                if (matchesCategory && matchesSearch) {
                    result.add(movie);
                }
            }
        }
        filteredMovies.setValue(result);
    }

    /** init { viewModelScope.launch(Dispatchers.IO) { ... preload sample movies ... } } */
    private void preloadSampleMoviesIfEmpty() {
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (dao.countMovies() == 0) {
                        dao.insertAll(sampleMovies());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Sample preload failed", e);
                }
            }
        });
    }

    private List<MovieRecord> sampleMovies() {
        List<MovieRecord> list = new ArrayList<>();
        list.add(new MovieRecord(0, "Dacoit A Love Story (2026)",
                "/dacoit-a-love-story-2026-tamil-movie/", "tamil-2026-movies",
                "https://moviesda30.com/dacoit-a-love-story-2026-tamil-movie/",
                "https://moviesda30.com/tamil-2026-movies/"));
        list.add(new MovieRecord(0, "Vaazha II Biopic of a Billion Bros (2026)",
                "/vaazha-ii-biopic-of-a-billion-bros-2026-tamil-movie/", "tamil-2026-movies",
                "https://moviesda30.com/vaazha-ii-biopic-of-a-billion-bros-2026-tamil-movie/",
                "https://moviesda30.com/tamil-2026-movies/"));
        list.add(new MovieRecord(0, "Coolie (2026)",
                "/coolie-2026-tamil-movie/", "tamil-2026-movies",
                "https://moviesda30.com/coolie-2026-tamil-movie/",
                "https://moviesda30.com/tamil-2026-movies/"));
        list.add(new MovieRecord(0, "Thalapathy 69 (2026)",
                "/thalapathy-69-2026-tamil-movie/", "tamil-2026-movies",
                "https://moviesda30.com/thalapathy-69-2026-tamil-movie/",
                "https://moviesda30.com/tamil-2026-movies/"));
        list.add(new MovieRecord(0, "Amaran (2024)",
                "/amaran-2024-tamil-movie/", "tamil-2024-movies",
                "https://moviesda30.com/amaran-tamil-movie/",
                "https://moviesda30.com/tamil-2024-movies/"));
        list.add(new MovieRecord(0, "Vettaiyan (2024)",
                "/vettaiyan-2024-tamil-movie/", "tamil-2024-movies",
                "https://moviesda30.com/vettaiyan-tamil-movie/",
                "https://moviesda30.com/tamil-2024-movies/"));
        list.add(new MovieRecord(0, "The Greatest Of All Time (2024)",
                "/the-greatest-of-all-time-tamil-movie/", "tamil-2024-movies",
                "https://moviesda30.com/the-greatest-of-all-time-tamil-movie/",
                "https://moviesda30.com/tamil-2024-movies/"));
        return list;
    }

    // ---------------- accessors ----------------

    public LiveData<String> getSearchQuery() {
        return searchQuery;
    }

    public LiveData<String> getSelectedCategory() {
        return selectedCategory;
    }

    public LiveData<ImportingState> getImportingState() {
        return importingState;
    }

    public LiveData<List<MovieRecord>> getAllMovies() {
        return allMovies;
    }

    public LiveData<List<String>> getCategories() {
        return categories;
    }

    public LiveData<List<MovieRecord>> getFilteredMovies() {
        return filteredMovies;
    }

    public void updateSearchQuery(String query) {
        searchQuery.setValue(query == null ? "" : query);
    }

    public void selectCategory(String category) {
        selectedCategory.setValue(category);
    }

    public void resetImportState() {
        importingState.setValue(ImportingState.idle());
    }

    // ---------------- file import ----------------

    public void importFile(final Context context, final Uri uri) {
        importingState.setValue(ImportingState.loading());
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final List<MovieRecord> records = FileImporter.importUri(context, uri);
                if (records != null && !records.isEmpty()) {
                    final ZohoPreferences prefs = new ZohoPreferences(context);
                    if (prefs.isClearOldDataBeforeUpload()) {
                        dao.clearAll();
                    }
                    dao.insertAll(records);
                    postImportingState(ImportingState.success(records.size()));
                } else {
                    postImportingState(ImportingState.error(
                            "No valid movie records found. Ensure columns match B, D, E, and F."));
                }
            }
        });
    }

    private void postImportingState(final ImportingState state) {
        importingState.postValue(state);
    }

    // ---------------- Zoho sync ----------------

    public void syncFromZoho(final Context context) {
        importingState.setValue(ImportingState.loading());
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final ZohoPreferences prefs = new ZohoPreferences(context);
                    List<MovieRecord> newRecords = ZohoSyncManager.performSync(
                            prefs.getAccountsServer(),
                            prefs.getApiServer(),
                            prefs.getClientId(),
                            prefs.getClientSecret(),
                            prefs.getRefreshToken(),
                            prefs.getFolderId(),
                            prefs.getFileName(),
                            prefs.getDefaultExtension()
                    );

                    if (newRecords != null && !newRecords.isEmpty()) {
                        if (prefs.isClearOldDataBeforeUpload()) {
                            dao.clearAll();
                        }
                        dao.insertAll(newRecords);
                        postImportingState(ImportingState.success(newRecords.size()));
                    } else {
                        postImportingState(ImportingState.error(
                                "Worksheet parsed, but returned 0 valid records."));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Zoho Sheets sync failed", e);
                    String msg = e.getMessage();
                    postImportingState(ImportingState.error(
                            msg != null ? msg : "An unknown Zoho sync error occurred."));
                }
            }
        });
    }

    // ---------------- CRUD ----------------

    public void addManualMovie(String name, String sublink, String category,
                               String link, String pageUrl, final Runnable onComplete) {
        final MovieRecord record = new MovieRecord(0, name, sublink, category, link, pageUrl);
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                dao.insert(record);
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    public void deleteMovie(final int id) {
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                dao.deleteById(id);
            }
        });
    }

    public void clearAllData() {
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                dao.clearAll();
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ioExecutor.shutdown();
    }
}
