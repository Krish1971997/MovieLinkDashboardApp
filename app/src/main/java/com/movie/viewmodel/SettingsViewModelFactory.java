package com.movie.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class SettingsViewModelFactory implements ViewModelProvider.Factory {

    private final Application app;

    public SettingsViewModelFactory(Application app) {
        this.app = app;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(SettingsViewModel.class)) {
            return (T) new SettingsViewModel(app);
        }
        throw new IllegalArgumentException("Unknown SettingsViewModel class: " + modelClass);
    }
}
