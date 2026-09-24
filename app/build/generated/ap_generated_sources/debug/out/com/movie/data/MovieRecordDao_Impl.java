package com.movie.data;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MovieRecordDao_Impl implements MovieRecordDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MovieRecord> __insertionAdapterOfMovieRecord;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  public MovieRecordDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMovieRecord = new EntityInsertionAdapter<MovieRecord>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `movies` (`id`,`name`,`sublink`,`category`,`link`,`pageUrl`,`importedAt`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final MovieRecord entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getName() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getName());
        }
        if (entity.getSublink() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getSublink());
        }
        if (entity.getCategory() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getCategory());
        }
        if (entity.getLink() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getLink());
        }
        if (entity.getPageUrl() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getPageUrl());
        }
        statement.bindLong(7, entity.getImportedAt());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM movies WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM movies";
        return _query;
      }
    };
  }

  @Override
  public void insertAll(final List<MovieRecord> movies) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfMovieRecord.insert(movies);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public long insert(final MovieRecord movie) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfMovieRecord.insertAndReturnId(movie);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteById(final int id) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
    int _argIndex = 1;
    _stmt.bindLong(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteById.release(_stmt);
    }
  }

  @Override
  public void clearAll() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfClearAll.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfClearAll.release(_stmt);
    }
  }

  @Override
  public LiveData<List<MovieRecord>> getAllMovies() {
    final String _sql = "SELECT * FROM movies ORDER BY id DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"movies"}, false, new Callable<List<MovieRecord>>() {
      @Override
      @Nullable
      public List<MovieRecord> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfSublink = CursorUtil.getColumnIndexOrThrow(_cursor, "sublink");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfLink = CursorUtil.getColumnIndexOrThrow(_cursor, "link");
          final int _cursorIndexOfPageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "pageUrl");
          final int _cursorIndexOfImportedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "importedAt");
          final List<MovieRecord> _result = new ArrayList<MovieRecord>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MovieRecord _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final String _tmpSublink;
            if (_cursor.isNull(_cursorIndexOfSublink)) {
              _tmpSublink = null;
            } else {
              _tmpSublink = _cursor.getString(_cursorIndexOfSublink);
            }
            final String _tmpCategory;
            if (_cursor.isNull(_cursorIndexOfCategory)) {
              _tmpCategory = null;
            } else {
              _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            }
            final String _tmpLink;
            if (_cursor.isNull(_cursorIndexOfLink)) {
              _tmpLink = null;
            } else {
              _tmpLink = _cursor.getString(_cursorIndexOfLink);
            }
            final String _tmpPageUrl;
            if (_cursor.isNull(_cursorIndexOfPageUrl)) {
              _tmpPageUrl = null;
            } else {
              _tmpPageUrl = _cursor.getString(_cursorIndexOfPageUrl);
            }
            final long _tmpImportedAt;
            _tmpImportedAt = _cursor.getLong(_cursorIndexOfImportedAt);
            _item = new MovieRecord(_tmpId,_tmpName,_tmpSublink,_tmpCategory,_tmpLink,_tmpPageUrl,_tmpImportedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public List<MovieRecord> getAllMoviesNow() {
    final String _sql = "SELECT * FROM movies ORDER BY id DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
      final int _cursorIndexOfSublink = CursorUtil.getColumnIndexOrThrow(_cursor, "sublink");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfLink = CursorUtil.getColumnIndexOrThrow(_cursor, "link");
      final int _cursorIndexOfPageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "pageUrl");
      final int _cursorIndexOfImportedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "importedAt");
      final List<MovieRecord> _result = new ArrayList<MovieRecord>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final MovieRecord _item;
        final int _tmpId;
        _tmpId = _cursor.getInt(_cursorIndexOfId);
        final String _tmpName;
        if (_cursor.isNull(_cursorIndexOfName)) {
          _tmpName = null;
        } else {
          _tmpName = _cursor.getString(_cursorIndexOfName);
        }
        final String _tmpSublink;
        if (_cursor.isNull(_cursorIndexOfSublink)) {
          _tmpSublink = null;
        } else {
          _tmpSublink = _cursor.getString(_cursorIndexOfSublink);
        }
        final String _tmpCategory;
        if (_cursor.isNull(_cursorIndexOfCategory)) {
          _tmpCategory = null;
        } else {
          _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
        }
        final String _tmpLink;
        if (_cursor.isNull(_cursorIndexOfLink)) {
          _tmpLink = null;
        } else {
          _tmpLink = _cursor.getString(_cursorIndexOfLink);
        }
        final String _tmpPageUrl;
        if (_cursor.isNull(_cursorIndexOfPageUrl)) {
          _tmpPageUrl = null;
        } else {
          _tmpPageUrl = _cursor.getString(_cursorIndexOfPageUrl);
        }
        final long _tmpImportedAt;
        _tmpImportedAt = _cursor.getLong(_cursorIndexOfImportedAt);
        _item = new MovieRecord(_tmpId,_tmpName,_tmpSublink,_tmpCategory,_tmpLink,_tmpPageUrl,_tmpImportedAt);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int countMovies() {
    final String _sql = "SELECT COUNT(*) FROM movies";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
