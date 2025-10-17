package com.dev.jmplayer.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.dev.jmplayer.model.AudioTrack;
import com.dev.jmplayer.model.Playlist;
import com.dev.jmplayer.model.PlaylistTrackCrossRef;

import java.util.List;

@Dao
public interface PlaylistDao {

    // Insere uma nova playlist. Se já existir, não faz nada.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertPlaylist(Playlist playlist);

    // Adiciona uma música a uma playlist (insere na tabela de junção).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void addTrackToPlaylist(PlaylistTrackCrossRef crossRef);

    // Insere uma música na tabela principal de músicas.
    // Isso é útil para que o banco de dados conheça todas as músicas que já foram adicionadas a alguma playlist.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertTrack(AudioTrack track);

    // Busca todas as playlists
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    List<Playlist> getAllPlaylists();

    // Busca todas as músicas de uma playlist específica
    @Transaction
    @Query("SELECT * FROM audio_tracks " +
            "INNER JOIN playlist_track_join ON audio_tracks.source_url = playlist_track_join.sourceUrl " +
            "WHERE playlist_track_join.playlistId = :playlistId")
    List<AudioTrack> getTracksForPlaylist(long playlistId);

    @Delete
    void removeTrackFromPlaylist(PlaylistTrackCrossRef crossRef);
    @Delete
    void deletePlaylist(Playlist playlist);
    @Update
    void updatePlaylist(Playlist playlist);
}