package com.movie.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.movie.data.CategoryDao;
import com.movie.data.CategoryEntity;
import com.movie.data.DefaultCategories;
import com.movie.data.MovieDatabase;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Data access for the Settings page category list. Mirrors the style of {@link MovieRepository}:
 * Room DAO plus a background executor, because none of this may run on the main thread.
 */
public class CategoryRepository {

    private final CategoryDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Context appContext;

    public CategoryRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.dao = MovieDatabase.getDatabase(appContext).categoryDao();
    }

    public CategoryDao getDao() {
        return dao;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public LiveData<List<CategoryEntity>> observeAll() {
        return dao.observeAll();
    }

    /** Search filter used by the Settings page text box (empty query = everything). */
    public LiveData<List<CategoryEntity>> observeFiltered(final String query) {
        if (query == null || query.trim().isEmpty()) {
            return dao.observeAll();
        }
        return dao.observeSearch(query.trim());
    }

    public void seedIfEmpty() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                DefaultCategories.seedIfEmpty(appContext);
            }
        });
    }

    public void restoreDefaults() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                DefaultCategories.resetToDefaults(appContext);
            }
        });
    }

    public void addCategory(final String path) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                String normalized = CategoryEntity.normalizePath(path);
                if (normalized.isEmpty()) return;
                CategoryEntity last = dao.firstNow();
                int position = 0;
                List<CategoryEntity> all = dao.getAllNow();
                if (all != null && !all.isEmpty()) {
                    for (CategoryEntity c : all) {
                        if (c.getPosition() >= position) position = c.getPosition() + 1;
                    }
                }
                dao.insert(new CategoryEntity(0, normalized,
                        CategoryEntity.displayNameFor(normalized), false, position,
                        System.currentTimeMillis()));
                if (last == null) {
                    // table was empty -> nothing else to do
                }
            }
        });
    }

    public void updateCategory(final CategoryEntity entity) {
        if (entity == null) return;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                entity.setUpdatedAt(System.currentTimeMillis());
                dao.update(entity);
            }
        });
    }

    public void deleteCategory(final CategoryEntity entity) {
        if (entity == null) return;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                dao.delete(entity);
            }
        });
    }

    public void setEnabled(final CategoryEntity entity, final boolean enabled) {
        if (entity == null) return;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                dao.setEnabledForId(entity.getId(), enabled);
            }
        });
    }

    public void setEnabledForAll(final boolean enabled) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                dao.setEnabledForAll(enabled);
            }
        });
    }

    public void clearAll() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                dao.clearAll();
            }
        });
    }

    /** Blocking count - only call from a worker/background thread. */
    public int countEnabledBlocking() {
        List<CategoryEntity> enabled = dao.getEnabledNow();
        return enabled == null ? 0 : enabled.size();
    }
}
