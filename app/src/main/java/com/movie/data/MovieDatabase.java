package com.movie.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Java port of MovieDatabase.kt (Room database, singleton holder via double-checked lock).
 *
 * v2: added the "categories" table so the previously hard-coded SUBCATEGORIES array
 * can be created / edited / deleted / filtered from the Settings page.
 */
@Database(entities = {MovieRecord.class, CategoryEntity.class}, version = 2, exportSchema = false)
public abstract class MovieDatabase extends RoomDatabase {

    public abstract MovieRecordDao movieDao();

    public abstract CategoryDao categoryDao();

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
