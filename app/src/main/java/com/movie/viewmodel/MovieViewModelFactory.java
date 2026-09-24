package com.movie.viewmodel;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.movie.repository.MovieRepository;

/**
 * Java port of MovieViewModelFactory (Kotlin ViewModelProvider.Factory).
 */
public class MovieViewModelFactory implements ViewModelProvider.Factory {

    private final MovieRepository repository;

    public MovieViewModelFactory(MovieRepository repository) {
        this.repository = repository;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(Class<T> modelClass) {
        if (modelClass.isAssignableFrom(MovieViewModel.class)) {
            return (T) new MovieViewModel(repository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
