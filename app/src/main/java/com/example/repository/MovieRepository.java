package com.example.repository;

import androidx.lifecycle.LiveData;

import com.example.data.MovieRecord;
import com.example.data.MovieRecordDao;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Java port of MovieRepository.kt
 *
 * Kotlin `suspend fun`s are replaced by normal methods executed on the supplied
 * background ExecutorService (supplied by the ViewModel) - the Java equivalent
 * of "suspend + Dispatchers.IO".
 */
public class MovieRepository {

    private final MovieRecordDao movieDao;

    public MovieRepository(MovieRecordDao movieDao) {
        this.movieDao = movieDao;
    }

    public LiveData<List<MovieRecord>> getAllMovies() {
        return movieDao.getAllMovies();
    }

    public MovieRecordDao getMovieDao() {
        return movieDao;
    }

    public void insertAll(ExecutorService executor, final List<MovieRecord> movies) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                movieDao.insertAll(movies);
            }
        });
    }

    public void insert(ExecutorService executor, final MovieRecord movie) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                movieDao.insert(movie);
            }
        });
    }

    public void deleteById(ExecutorService executor, final int id) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                movieDao.deleteById(id);
            }
        });
    }

    public void clearAll(ExecutorService executor) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                movieDao.clearAll();
            }
        });
    }
}
