package com.dev.jmplayer.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.InputType;
import android.view.Menu;      // Nova importação
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import com.dev.jmplayer.R;
import com.dev.jmplayer.adapter.PlaylistsAdapter;
import com.dev.jmplayer.data.AppDatabase;
import com.dev.jmplayer.model.Playlist;
import androidx.appcompat.widget.Toolbar;

import java.util.concurrent.Executors;

public class PlaylistsActivity extends AppCompatActivity implements PlaylistsAdapter.OnPlaylistClickListener {

    private RecyclerView recyclerView;
    private PlaylistsAdapter adapter;
    private AppDatabase database;
    private ActivityResultLauncher<Intent> tracksLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlists);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Playlist");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            // Se quiser usar o seu ícone personalizado aqui também, adicione a linha abaixo:
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_arrow_back);
        }

        database = AppDatabase.getDatabase(this);

        // Prepara o "lançador" que abrirá a tela de músicas e aguardará um resultado
        tracksLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Passo 3: Se a tela de músicas retornou um resultado OK...
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK) {
                        // ...nós repassamos esse resultado para a tela anterior (SoundDeckActivity)...
                        setResult(RESULT_OK, result.getData());
                        // ...e fechamos esta tela para completar a "reação em cadeia".
                        finish();
                    }
                });

        setupRecyclerView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPlaylistsFromDb();
    }

    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.rv_playlists);
        adapter = new PlaylistsAdapter(this); // A própria Activity é o listener
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadPlaylistsFromDb() {
        Executors.newSingleThreadExecutor().execute(() -> {
            final var playlists = database.playlistDao().getAllPlaylists();
            runOnUiThread(() -> adapter.setPlaylists(playlists));
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.playlists_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_create_playlist) {
            showCreatePlaylistDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPlaylistDeleteClicked(Playlist playlist) {
        new AlertDialog.Builder(this)
                .setTitle("Apagar Playlist")
                .setMessage("Tem a certeza que deseja apagar a playlist '" + playlist.name + "'? Esta ação não pode ser desfeita.")
                .setPositiveButton("Apagar", (dialog, which) -> {
                    // Executa a remoção em background
                    Executors.newSingleThreadExecutor().execute(() -> {
                        database.playlistDao().deletePlaylist(playlist);
                        // Após apagar, recarrega a lista na thread principal
                        runOnUiThread(this::loadPlaylistsFromDb);
                    });
                    Toast.makeText(this, "Playlist '" + playlist.name + "' apagada", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public void onPlaylistLongClicked(Playlist playlist) {
        showRenamePlaylistDialog(playlist);
    }

    // --- METODO PARA O DIÁLOGO DE RENOMEAÇÃO ---
    private void showRenamePlaylistDialog(final Playlist playlist) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Renomear Playlist");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(playlist.name); // Preenche com o nome atual
        builder.setView(input);

        builder.setPositiveButton("Salvar", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(playlist.name)) {
                // Atualiza o nome do objeto playlist
                playlist.name = newName;

                // Executa a atualização na base de dados em background
                Executors.newSingleThreadExecutor().execute(() -> {
                    database.playlistDao().updatePlaylist(playlist);
                    // Recarrega a lista na thread principal
                    runOnUiThread(this::loadPlaylistsFromDb);
                });
                Toast.makeText(this, "Playlist renomeada para '" + newName + "'", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showCreatePlaylistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Criar Nova Playlist");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Nome da Playlist");
        builder.setView(input);

        builder.setPositiveButton("Criar", (dialog, which) -> {
            String playlistName = input.getText().toString().trim();
            if (!playlistName.isEmpty()) {
                Executors.newSingleThreadExecutor().execute(() -> {
                    database.playlistDao().insertPlaylist(new Playlist(playlistName));
                    // Após inserir, recarrega a lista na thread principal
                    runOnUiThread(this::loadPlaylistsFromDb);
                });
                Toast.makeText(this, "Playlist '" + playlistName + "' criada", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    @Override
    public void onPlaylistClicked(Playlist playlist) {
        Intent intent = new Intent(this, PlaylistTracksActivity.class);
        intent.putExtra(PlaylistTracksActivity.EXTRA_PLAYLIST_ID, playlist.playlistId);
        intent.putExtra(PlaylistTracksActivity.EXTRA_PLAYLIST_NAME, playlist.name);
        tracksLauncher.launch(intent);
        // --- NOVA LINHA PARA ANIMAÇÃO DE ENTRADA ---
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // --- SOBRESCREVER O onBACKPRESSED PARA ADICIONAR ANIMAÇÃO DE SAÍDA ---

    @SuppressLint("GestureBackNavigation")
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}