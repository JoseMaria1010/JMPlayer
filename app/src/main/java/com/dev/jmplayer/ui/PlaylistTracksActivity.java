package com.dev.jmplayer.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.dev.jmplayer.R;
import com.dev.jmplayer.adapter.PlaylistTracksAdapter;
import com.dev.jmplayer.data.AppDatabase;
import com.dev.jmplayer.model.AudioTrack;
import com.dev.jmplayer.model.PlaylistTrackCrossRef;

import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.concurrent.Executors;

public class PlaylistTracksActivity extends AppCompatActivity implements PlaylistTracksAdapter.OnTrackClickListener {

    public static final String EXTRA_PLAYLIST_ID = "EXTRA_PLAYLIST_ID";
    public static final String EXTRA_PLAYLIST_NAME = "EXTRA_PLAYLIST_NAME";

    private RecyclerView recyclerView;
    private PlaylistTracksAdapter adapter;
    private AppDatabase database;
    private long playlistId;
    private ActivityResultLauncher<Intent> addMusicLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_tracks);

        playlistId = getIntent().getLongExtra(EXTRA_PLAYLIST_ID, -1);
        String playlistName = getIntent().getStringExtra(EXTRA_PLAYLIST_NAME);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(playlistName); // Usa o nome da playlist como título
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            // Se quiser usar o seu ícone personalizado aqui também, adicione a linha abaixo:
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_arrow_back);
        }

        addMusicLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Se a tela de adição retornou "OK", significa que músicas foram adicionadas
                    if (result.getResultCode() == RESULT_OK) {
                        // Simplesmente recarregue a lista para mostrar as novas músicas
                        loadTracksForPlaylist(playlistId);
                    }
                });

        if (playlistId == -1) {
            finish();
            return;
        }

        database = AppDatabase.getDatabase(this);
        recyclerView = findViewById(R.id.rv_playlist_tracks);
        adapter = new PlaylistTracksAdapter(this);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadTracksForPlaylist(playlistId);
    }

    public void onTrackClick(int position, ArrayList<AudioTrack> playlist) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("playlist", playlist);
        resultIntent.putExtra("start_index", position);
        setResult(RESULT_OK, resultIntent);
        finish(); // Devolve o resultado e fecha
    }

    private void loadTracksForPlaylist(long playlistId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            final var tracks = database.playlistDao().getTracksForPlaylist(this.playlistId);
            runOnUiThread(() -> {
                adapter.setTracks(tracks);
            });
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.playlist_tracks_menu, menu);
        return true;
    }



    @Override
    public void onRemoveTrackClick(AudioTrack track) {
        new AlertDialog.Builder(this)
                .setTitle("Remover Música")
                .setMessage("Tem a certeza que deseja remover '" + track.getTitle() + "' desta playlist?")
                .setPositiveButton("Remover", (dialog, which) -> {
                    // Executa a remoção em background
                    Executors.newSingleThreadExecutor().execute(() -> {
                        PlaylistTrackCrossRef crossRef = new PlaylistTrackCrossRef(playlistId, track.getSourceUrl());
                        database.playlistDao().removeTrackFromPlaylist(crossRef);
                        // Após remover, recarrega a lista na thread principal
                        runOnUiThread(() -> loadTracksForPlaylist(playlistId));
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_add_music) {
            Intent intent = new Intent(this, AddMusicToPlaylistActivity.class);
            intent.putExtra(AddMusicToPlaylistActivity.EXTRA_PLAYLIST_ID, playlistId);
            addMusicLauncher.launch(intent);
            // --- NOVA LINHA PARA ANIMAÇÃO DE ENTRADA ---
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // --- SOBRESCREVER O onBACKPRESSED PARA ADICIONAR ANIMAÇÃO DE SAÍDA ---
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }
}