package com.dev.jmplayer.ui;

import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dev.jmplayer.R;
import com.dev.jmplayer.adapter.SelectableTrackAdapter;
import com.dev.jmplayer.data.AppDatabase;
import com.dev.jmplayer.model.AudioTrack;
import com.dev.jmplayer.model.PlaylistTrackCrossRef;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddMusicToPlaylistActivity extends AppCompatActivity {

    public static final String EXTRA_PLAYLIST_ID = "com.dev.jmplayer.EXTRA_PLAYLIST_ID";

    private RecyclerView recyclerView;
    private SelectableTrackAdapter adapter;
    private AppDatabase database;
    private long playlistId;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_music_to_playlist);

        playlistId = getIntent().getLongExtra(EXTRA_PLAYLIST_ID, -1);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Colocar Musicas");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        database = AppDatabase.getDatabase(this);
        setupRecyclerView();
        loadAllTracks();
    }

    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.rv_selectable_tracks);
        adapter = new SelectableTrackAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadAllTracks() {
        executor.execute(() -> {
            List<AudioTrack> localTracks = fetchLocalMusicLibrary();
            runOnUiThread(() -> adapter.setTracks(localTracks));
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.add_music_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_save_selection) {
            saveSelectedTracks();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveSelectedTracks() {
        ArrayList<AudioTrack> selectedTracks = adapter.getSelectedTracks();
        if (selectedTracks.isEmpty()) {
            Toast.makeText(this, "Nenhuma música selecionada", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            for (AudioTrack track : selectedTracks) {
                // Garante que a música existe na tabela de músicas antes de criar a referência
                database.playlistDao().insertTrack(track);
                PlaylistTrackCrossRef crossRef = new PlaylistTrackCrossRef(playlistId, track.getSourceUrl());
                database.playlistDao().addTrackToPlaylist(crossRef);
            }
            runOnUiThread(() -> {
                Toast.makeText(this, selectedTracks.size() + " música(s) adicionada(s)", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK); // Informa a activity anterior que algo mudou
                finish(); // Fecha esta activity e volta para a lista de músicas da playlist
            });
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private List<AudioTrack> fetchLocalMusicLibrary() {
        List<AudioTrack> localTracks = new ArrayList<>();

        // 1. Adicionar ALBUM_ID à projeção
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID // <-- NOVO
        };

        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        try (Cursor cursor = getContentResolver().query(collection, projection, selection, null, sortOrder)) {
            if (cursor != null) {
                // 2. Obter as colunas
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID); // <-- NOVO

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String title = cursor.getString(titleColumn);
                    String artist = cursor.getString(artistColumn);
                    long albumId = cursor.getLong(albumIdColumn); // <-- NOVO

                    if (title == null || title.isEmpty()) {
                        title = "Faixa Desconhecida";
                    }
                    if (artist == null || artist.equals("<unknown>")) {
                        artist = "Artista Desconhecido";
                    }

                    // 3. Construir as URIs
                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    Uri artworkUri = ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                    );

                    // 4. Passar a artworkUri (como String) para o construtor
                    localTracks.add(new AudioTrack(title, artist, contentUri.toString(), artworkUri.toString()));
                }
            }
        }
        return localTracks;
    }
}