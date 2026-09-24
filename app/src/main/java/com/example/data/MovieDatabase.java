package com.example.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Java port of MovieDatabase.kt (Room database, singleton holder via double-checked lock).
 */
@Database(entities = {MovieRecord.class}, version = 1, exportSchema = false)
public abstract class MovieDatabase extends RoomDatabase {

    public abstract MovieRecordDao movieDao();

    private static volatile MovieDatabase INSTANCE;

    public static MovieDatabase getDatabase(Context context) {
        if (INSTANCE == null) {
            synchronized (MovieDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    MovieDatabase.class,
                                    "movie_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
