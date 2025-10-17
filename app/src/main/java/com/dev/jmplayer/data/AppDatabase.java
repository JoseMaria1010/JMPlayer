package com.dev.jmplayer.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.dev.jmplayer.model.AudioTrack;
import com.dev.jmplayer.model.Playlist;
import com.dev.jmplayer.model.PlaylistTrackCrossRef;

@Database(entities = {AudioTrack.class, Playlist.class, PlaylistTrackCrossRef.class}, version = 2)
public abstract class AppDatabase extends RoomDatabase {

    public abstract PlaylistDao playlistDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "jmplayer_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}