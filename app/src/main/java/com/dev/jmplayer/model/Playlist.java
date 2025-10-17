package com.dev.jmplayer.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "playlists")
public class Playlist {
    @PrimaryKey(autoGenerate = true)
    public long playlistId;

    public String name;

    public Playlist(String name) {
        this.name = name;
    }
}