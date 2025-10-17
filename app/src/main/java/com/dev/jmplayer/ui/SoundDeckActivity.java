package com.dev.jmplayer.ui;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.dev.jmplayer.R;
import com.dev.jmplayer.adapter.TrackAdapter;
import com.dev.jmplayer.contract.SoundDeckContract;
import com.dev.jmplayer.model.AudioTrack;
import com.dev.jmplayer.model.Playlist;
import com.dev.jmplayer.presenter.SoundDeckPresenter;
import com.dev.jmplayer.service.PlaybackService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SoundDeckActivity extends AppCompatActivity implements SoundDeckContract.View, TrackAdapter.OnTrackClickListener {

    private SoundDeckContract.Presenter presenter;
    private ListenableFuture<MediaController> controllerFuture;

    // Launchers
    private ActivityResultLauncher<Intent> playlistsLauncher;
    private ActivityResultLauncher<String> requestStoragePermissionLauncher;
    private ActivityResultLauncher<String> requestAudioPermissionLauncher;

    // UI
    private ImageView artworkImageView;
    private TextView titleTextView, artistTextView, currentTimeTextView, totalTimeTextView;
    private ImageButton togglePlaybackButton, nextButton, previousButton, shuffleButton, repeatButton;
    private SeekBar progressSeekBar;
    private ObjectAnimator artworkAnimator;
    private RecyclerView trackRecyclerView;
    private TrackAdapter trackAdapter;
    private ProgressDialog recognitionDialog;

    // State
    private static class PendingPlaylist {
        ArrayList<AudioTrack> playlist;
        int startIndex;
    }
    private PendingPlaylist pendingPlaylistToPlay = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sound_deck);

        setupLaunchers();

        presenter = new SoundDeckPresenter(getApplicationContext());
        presenter.bindView(this);

        initializeUI();
        setupToolbar();
        setupListeners();
        setupArtworkAnimation();
        setupRecyclerView();

        // A chamada 'checkPermissionsAndLoad()' FOI REMOVIDA DAQUI.
        // Vamos chamá-la apenas DEPOIS que o MediaController estiver pronto.
    }

    private void setupLaunchers() {
        playlistsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        Serializable extra = data.getSerializableExtra("playlist");
                        pendingPlaylistToPlay = new PendingPlaylist();
                        pendingPlaylistToPlay.playlist = (extra instanceof ArrayList) ? (ArrayList<AudioTrack>) extra : null;
                        pendingPlaylistToPlay.startIndex = data.getIntExtra("start_index", 0);
                    }
                });

        // --- LAUNCHER DE PERMISSÃO DE ARMAZENAMENTO CORRIGIDO ---
        requestStoragePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                // A permissão foi dada, agora podemos carregar os dados
                loadDataBasedOnPlayerState();
            } else {
                Toast.makeText(this, "Permissão de armazenamento necessária.", Toast.LENGTH_LONG).show();
            }
        });

        requestAudioPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                presenter.onAudioPermissionGranted();
            } else {
                showRecognitionError("Permissão do microfone negada.");
            }
        });
    }


    private void checkPermissionsAndLoad() {
        String permission = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            if (presenter instanceof SoundDeckPresenter) {
                ((SoundDeckPresenter) presenter).loadFullLibrary();
            }
        } else {
            requestStoragePermissionLauncher.launch(permission);
        }
    }

    private void checkStoragePermissionAndProceed() {
        String permission = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            // Permissão já existe, carrega os dados
            loadDataBasedOnPlayerState();
        } else {
            // Pede permissão, o callback do launcher tratará do resto
            requestStoragePermissionLauncher.launch(permission);
        }
    }

    private void loadDataBasedOnPlayerState() {
        if (controllerFuture == null || !controllerFuture.isDone()) {
            return; // Controller não está pronto, algo está errado
        }

        try {
            MediaController controller = controllerFuture.get();
            if (controller.getMediaItemCount() == 0) {
                // CASO 1: Arranque fresco. O Player está vazio.
                // Carrega a biblioteca completa (o que redefine o player)
                if (presenter instanceof SoundDeckPresenter) {
                    ((SoundDeckPresenter) presenter).loadFullLibrary();
                }
            } else {
                // CASO 2: A reabrir a app. O Player já tem músicas.
                // Apenas sincroniza a UI sem redefinir o player.
                if (presenter instanceof SoundDeckPresenter) {
                    ((SoundDeckPresenter) presenter).resyncUI();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        initializeMediaController();
    }


    @Override
    protected void onStop() {
        super.onStop();
        releaseMediaController();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        presenter.unbindView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.menu_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { return false; }
            @Override public boolean onQueryTextChange(String newText) {
                if (presenter instanceof SoundDeckPresenter) {
                    ((SoundDeckPresenter) presenter).search(newText);
                }
                return true;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.menu_recognize_music) {
            presenter.startMusicRecognition();
            return true;
        }
        else if (itemId == R.id.menu_add_to_playlist) {
            presenter.onOptionsClicked();
            return true;
        } else if (itemId == R.id.menu_view_playlists) {
            Intent intent = new Intent(SoundDeckActivity.this, PlaylistsActivity.class);
            playlistsLauncher.launch(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return true;
        } else if (itemId == R.id.menu_view_all_music) {
            if (presenter instanceof SoundDeckPresenter) {
                ((SoundDeckPresenter) presenter).loadFullLibrary();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView() {
        trackRecyclerView = findViewById(R.id.rv_track_list);
        trackAdapter = new TrackAdapter(this);
        trackRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        trackRecyclerView.setAdapter(trackAdapter);
    }

    @Override
    public void onTrackClick(AudioTrack track, int position) {
        if (presenter instanceof SoundDeckPresenter) {
            List<AudioTrack> currentList = (trackAdapter != null) ? trackAdapter.getTracks() : new ArrayList<>();
            if (!currentList.isEmpty()) {
                ((SoundDeckPresenter) presenter).loadTrackQueue(
                        new ArrayList<>(currentList),
                        position,
                        true,
                        ((SoundDeckPresenter) presenter).isInPlaylistMode() // Mantém o estado atual
                );
            }
        }
    }

    @Override
    public void displayTracks(List<AudioTrack> tracks) {
        //Toast.makeText(this, "A exibir " + tracks.size() + " músicas", Toast.LENGTH_SHORT).show();
        if (trackAdapter != null) {
            trackAdapter.setTracks(tracks);
        }
    }

    private void initializeMediaController() {
        if (controllerFuture != null && !controllerFuture.isDone()) {
            return;
        }
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();

        controllerFuture.addListener(() -> {
            try {
                MediaController controller = controllerFuture.get();
                if (presenter instanceof SoundDeckPresenter) {
                    ((SoundDeckPresenter) presenter).setMediaController(controller);
                }

                // CASO A: Vindo de uma playlist
                if (pendingPlaylistToPlay != null && pendingPlaylistToPlay.playlist != null) {
                    ((SoundDeckPresenter) presenter).loadTrackQueue(
                            pendingPlaylistToPlay.playlist,
                            pendingPlaylistToPlay.startIndex,
                            true,
                            true // isPlaylist
                    );
                    pendingPlaylistToPlay = null;
                } else {
                    // CASO B: Arranque normal ou reabertura da app
                    // Agora que o controller está pronto, verificamos as permissões
                    // e depois decidimos o que carregar
                    checkStoragePermissionAndProceed();
                }

                // NOTA: 'syncUIToPlayerState()' é agora chamado DENTRO
                // de 'loadFullLibrary', 'resyncUI', e 'loadTrackQueue' no Presenter
                // para garantir que só é chamado APÓS os dados estarem prontos.

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void releaseMediaController() {
        MediaController.releaseFuture(controllerFuture);
    }

    @Override
    public void showPlaylistsDialog(List<Playlist> playlists) {
        final CharSequence[] playlistNames = new CharSequence[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) {
            playlistNames[i] = playlists.get(i).name;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Adicionar à playlist");
        if (playlists.isEmpty()) {
            builder.setMessage("Nenhuma playlist encontrada. Crie uma na tela 'Minhas Playlists'.");
        } else {
            builder.setItems(playlistNames, (dialog, which) -> {
                Playlist selectedPlaylist = playlists.get(which);
                if (presenter instanceof SoundDeckPresenter) {
                    ((SoundDeckPresenter) presenter).addCurrentTrackToPlaylist(selectedPlaylist);
                }
                Toast.makeText(this, "Música adicionada a " + selectedPlaylist.name, Toast.LENGTH_SHORT).show();
            });
        }
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void setupArtworkAnimation() {
        artworkAnimator = ObjectAnimator.ofFloat(artworkImageView, "rotation", 0f, 360f);
        artworkAnimator.setDuration(20000);
        artworkAnimator.setRepeatCount(ValueAnimator.INFINITE);
        artworkAnimator.setRepeatMode(ValueAnimator.RESTART);
        artworkAnimator.setInterpolator(new LinearInterpolator());
    }

    @Override
    public void animateArtwork(boolean shouldAnimate) {
        if (shouldAnimate) {
            if (artworkAnimator.isPaused()) {
                artworkAnimator.resume();
            } else if (!artworkAnimator.isRunning()) {
                artworkAnimator.start();
            }
        } else {
            if (artworkAnimator.isRunning()) {
                artworkAnimator.pause();
            }
        }
    }

    private void initializeUI() {
        artworkImageView = findViewById(R.id.iv_artwork);
        titleTextView = findViewById(R.id.tv_title);
        artistTextView = findViewById(R.id.tv_artist);
        currentTimeTextView = findViewById(R.id.tv_current_time);
        totalTimeTextView = findViewById(R.id.tv_total_time);
        togglePlaybackButton = findViewById(R.id.btn_toggle_playback);
        nextButton = findViewById(R.id.btn_next);
        previousButton = findViewById(R.id.btn_previous);
        shuffleButton = findViewById(R.id.btn_shuffle);
        repeatButton = findViewById(R.id.btn_repeat);
        progressSeekBar = findViewById(R.id.sb_progress);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("JMPlayer");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_my_custom_back_arrow);
        }
    }

    private void setupListeners() {
        togglePlaybackButton.setOnClickListener(v -> presenter.togglePlayback());
        nextButton.setOnClickListener(v -> presenter.skipToNextTrack());
        previousButton.setOnClickListener(v -> presenter.returnToPreviousTrack());
        shuffleButton.setOnClickListener(v -> presenter.toggleShuffleMode());
        repeatButton.setOnClickListener(v -> presenter.toggleRepeatMode());
        progressSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                presenter.seekToTimestamp(seekBar.getProgress());
            }
        });
    }

    @Override
    public void renderTrackDetails(AudioTrack track) {
        titleTextView.setText(track.getTitle());
        artistTextView.setText(track.getArtist());
        Glide.with(this)
                .load(track.getArtworkUrl())
                .placeholder(R.drawable.ic_music_note_placeholder)
                .error(R.drawable.ic_music_note_placeholder) // Adiciona um fallback de erro
                .into(artworkImageView);
    }

    @Override
    public void setPlaybackButtonState(boolean isPlaying) {
        togglePlaybackButton.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    @Override
    public void updateProgressDisplay(int currentPosition, int maxDuration) {
        progressSeekBar.setMax(maxDuration);
        progressSeekBar.setProgress(currentPosition);
    }

    @Override
    public void showTimeProgress(String currentTime, String totalTime) {
        currentTimeTextView.setText(currentTime);
        totalTimeTextView.setText(totalTime);
    }

    @Override
    public void updateShuffleButtonState(boolean isActive) {
        shuffleButton.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                isActive ? R.color.colorAccent : android.R.color.white)));
    }

    @Override
    public void updateRepeatButtonState(int repeatMode) {
        switch (repeatMode) {
            case Player.REPEAT_MODE_ONE:
                repeatButton.setImageResource(R.drawable.ic_repeat_one);
                repeatButton.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorAccent)));
                break;
            case Player.REPEAT_MODE_ALL:
                repeatButton.setImageResource(R.drawable.ic_repeat);
                repeatButton.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorAccent)));
                break;
            default: // Player.REPEAT_MODE_OFF
                repeatButton.setImageResource(R.drawable.ic_repeat);
                repeatButton.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.white)));
                break;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // --- MÉTODOS DE RECONHECIMENTO DE MÚSICA ---

    @Override
    public void checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            presenter.onAudioPermissionGranted();
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    @Override
    public void showRecognitionStarted() {
        if (recognitionDialog == null) {
            recognitionDialog = new ProgressDialog(this);
            recognitionDialog.setMessage("A ouvir...");
            recognitionDialog.setCancelable(true);
            recognitionDialog.setOnCancelListener(dialog -> {
                if (presenter instanceof SoundDeckPresenter) {
                    ((SoundDeckPresenter) presenter).cancelMusicRecognition();
                }
            });
        }
        recognitionDialog.show();
    }

    @Override
    public void dismissRecognitionDialog() {
        if (recognitionDialog != null && recognitionDialog.isShowing()) {
            recognitionDialog.dismiss();
        }
    }

    @Override
    public void showRecognitionResult(String title, String artist) {
        new AlertDialog.Builder(this)
                .setTitle("Música Encontrada")
                .setMessage(title + "\npor " + artist)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void showRecognitionError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}