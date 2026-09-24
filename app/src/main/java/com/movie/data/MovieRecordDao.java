package com.movie.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Java port of MovieRecordDao.kt
 *
 * Kotlin used kotlinx.coroutines.flow.Flow for getAllMovies().
 * Java has no Flow, so the reactive return type is androidx.lifecycle.LiveData
 * (Room supports it natively) and the suspend funs become plain void/long methods
 * that the repository invokes on a background Executor.
 */
@Dao
public interface MovieRecordDao {

    @Query("SELECT * FROM movies ORDER BY id DESC")
    LiveData<List<MovieRecord>> getAllMovies();

    @Query("SELECT * FROM movies ORDER BY id DESC")
    List<MovieRecord> getAllMoviesNow();

    @Query("SELECT COUNT(*) FROM movies")
    int countMovies();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MovieRecord> movies);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(MovieRecord movie);

    @Query("DELETE FROM movies WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM movies")
    void clearAll();
}
