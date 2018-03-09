package de.ub0r.android.basscast.model;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;

@Database(entities = {Stream.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {

    public static class Builder {
        private static AppDatabase sInstance;

        public static AppDatabase getInstance(final Context context) {
            if (sInstance == null) {
                sInstance = Room.databaseBuilder(context, AppDatabase.class, "basscast")
                        .build();
            }
            return sInstance;
        }
    }

    public abstract StreamDao streamDao();
}