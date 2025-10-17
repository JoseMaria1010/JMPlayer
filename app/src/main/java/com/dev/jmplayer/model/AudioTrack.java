package com.dev.jmplayer.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "audio_tracks")
public class AudioTrack implements Serializable {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "source_url")
    private final String sourceUrl;

    @ColumnInfo(name = "title")
    private String title;

    @ColumnInfo(name = "artist")
    private String artist;

    @ColumnInfo(name = "artwork_url")
    private String artworkUrl;

    public AudioTrack(@NonNull String title, @NonNull String artist, @NonNull String sourceUrl, String artworkUrl) {
        this.title = title;
        this.artist = artist;
        this.sourceUrl = sourceUrl;
        // --- GARANTIR QUE ESTA LINHA EXISTE ---
        this.artworkUrl = artworkUrl;
    }

    // Getters
    public String getTitle() {
        return title;
    }
    public String getArtist() {
        return artist;
    }
    @NonNull
    public String getSourceUrl() {
        return sourceUrl;
    }
    public String getArtworkUrl() {
        return artworkUrl;
    }

    // Setters (necessários para o Room e outras lógicas)
    public void setTitle(String title) {
        this.title = title;
    }
    public void setArtist(String artist) {
        this.artist = artist;
    }
    public void setArtworkUrl(String artworkUrl) {
        this.artworkUrl = artworkUrl;
    }
}