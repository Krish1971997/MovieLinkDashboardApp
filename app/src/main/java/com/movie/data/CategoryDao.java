package com.movie.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY position ASC")
    LiveData<List<CategoryEntity>> observeAll();

    @Query("SELECT * FROM categories ORDER BY position ASC")
    List<CategoryEntity> getAllNow();

    @Query("SELECT COUNT(*) FROM categories")
    int count();

    /** Blocking count - background thread only. */
    @Query("SELECT COUNT(*) FROM categories")
    int countNow();

    @Query("SELECT * FROM categories WHERE displayName LIKE '%' || :query || '%' "
            + "OR path LIKE '%' || :query || '%' ORDER BY displayName COLLATE NOCASE ASC")
    LiveData<List<CategoryEntity>> observeSearch(String query);

    @Query("SELECT * FROM categories ORDER BY position ASC LIMIT 1")
    CategoryEntity firstNow();

    @Query("SELECT * FROM categories WHERE enabled = 1 ORDER BY position ASC")
    List<CategoryEntity> getEnabledNow();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(CategoryEntity category);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CategoryEntity> categories);

    @Update
    void update(CategoryEntity category);

    @Delete
    void delete(CategoryEntity category);

    @Query("UPDATE categories SET enabled = :enabled WHERE id = :id")
    void setEnabledForId(int id, boolean enabled);

    @Query("UPDATE categories SET enabled = :enabled")
    void setAllEnabled(boolean enabled);

    @Query("UPDATE categories SET enabled = :enabled")
    void setEnabledForAll(boolean enabled);

    @Query("DELETE FROM categories")
    void clearAll();
}
