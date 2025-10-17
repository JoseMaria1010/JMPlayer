package com.dev.jmplayer.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;

// Define uma chave primária composta pelo ID da playlist e o URL da música
@Entity(tableName = "playlist_track_join", primaryKeys = {"playlistId", "sourceUrl"})
public class PlaylistTrackCrossRef {
    public long playlistId;

    @NonNull
    public String sourceUrl;

    public PlaylistTrackCrossRef(long playlistId, @NonNull String sourceUrl) {
        this.playlistId = playlistId;
        this.sourceUrl = sourceUrl;
    }
}