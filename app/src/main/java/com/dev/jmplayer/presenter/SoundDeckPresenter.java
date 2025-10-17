package com.dev.jmplayer.presenter;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import java.util.Locale;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata; // Importação necessária
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

import com.dev.jmplayer.contract.SoundDeckContract;
import com.dev.jmplayer.data.AppDatabase;
import com.dev.jmplayer.helper.ACRCloudHelper;
import com.dev.jmplayer.model.AudioTrack;
import com.dev.jmplayer.model.Playlist;
import com.dev.jmplayer.model.PlaylistTrackCrossRef;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SoundDeckPresenter implements SoundDeckContract.Presenter, ACRCloudHelper.ACRCloudListener {

    private static final String TAG = "SoundDeckPresenter";
    private SoundDeckContract.View view;
    private MediaController mediaController;
    private final Context context;
    private final AppDatabase database;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final Handler uiUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable progressUpdateTask;

    private List<AudioTrack> fullMusicLibrary = new ArrayList<>();
    private boolean isInPlaylistMode = false;
    private List<AudioTrack> currentDisplayList = new ArrayList<>();
    private List<AudioTrack> trackQueue = new ArrayList<>();

    private ACRCloudHelper acrCloudHelper;

    public SoundDeckPresenter(Context context) {
        this.context = context.getApplicationContext();
        database = AppDatabase.getDatabase(context);

        String host = "identify-eu-west-1.acrcloud.com"; // Ex: "identify-eu-west-1.acrcloud.com"
        String accessKey = "31d940b7d80ef62fdd59eb3756c73708";
        String accessSecret = "WWrD5ngL8sl0aqLbasylNBG0fwm15q8rnUgI83oI";

        acrCloudHelper = new ACRCloudHelper(context, host, accessKey, accessSecret, this);
    }

    public void setMediaController(MediaController controller) {
        this.mediaController = controller;
        if (controller != null) {
            setupControllerListener();
            // NÃO SINCRONIZE AQUI. Deixe a lógica de inicialização decidir.
        }
    }

    @Override
    public void bindView(SoundDeckContract.View view) {
        this.view = view;
        //setupProgressUpdater();

    }

    @Override
    public void unbindView() {
        this.view = null;
        cancelProgressUpdates();
        if (acrCloudHelper != null) {
            acrCloudHelper.cancel();
        }
    }

    @Override
    public void resyncUI() {
        Log.d(TAG, "resyncUI: Tentando ressincronizar a UI...");

        // 1. Vai para a background thread APENAS para carregar a biblioteca (para a busca)
        databaseExecutor.execute(() -> {
            if (fullMusicLibrary.isEmpty()) {
                fullMusicLibrary = fetchLocalMusicLibrary();
            }

            // 2. Quando terminar, volta para a Main Thread para fazer o resto
            new Handler(Looper.getMainLooper()).post(() -> {
                // 3. Agora estamos na Main Thread. É SEGURO aceder ao MediaController.
                if (mediaController == null || view == null) {
                    Log.w(TAG, "resyncUI: MediaController ou View ficou nulo durante a sincronização.");
                    return; // Não podemos fazer nada
                }

                Log.d(TAG, "resyncUI: MediaController conectado. Recriando lista...");
                List<AudioTrack> currentQueueInPlayer = new ArrayList<>();
                int mediaItemCount = mediaController.getMediaItemCount(); // Esta linha não vai mais dar crash

                for (int i = 0; i < mediaItemCount; i++) {
                    MediaItem item = mediaController.getMediaItemAt(i);
                    // Tenta encontrar o AudioTrack completo na nossa biblioteca
                    AudioTrack foundTrack = findTrackByMediaId(fullMusicLibrary, item.mediaId);
                    if (foundTrack != null) {
                        currentQueueInPlayer.add(foundTrack);
                    } else if (item.mediaMetadata != null && item.mediaMetadata.title != null) {
                        // Se não encontrar (pode ser uma música antiga/removida), usa os metadados do player
                        currentQueueInPlayer.add(new AudioTrack(
                                item.mediaMetadata.title.toString(),
                                item.mediaMetadata.artist != null ? item.mediaMetadata.artist.toString() : "Artista Desconhecido",
                                item.mediaId,
                                item.mediaMetadata.artworkUri != null ? item.mediaMetadata.artworkUri.toString() : null
                        ));
                    }
                }

                // 4. Define o estado do Presenter
                if (mediaItemCount > 0 && mediaItemCount == fullMusicLibrary.size()) {
                    this.isInPlaylistMode = false;
                    this.currentDisplayList = new ArrayList<>(fullMusicLibrary);
                } else {
                    this.isInPlaylistMode = true;
                    this.currentDisplayList = new ArrayList<>(currentQueueInPlayer);
                }

                // 5. Define a fila local para que 'syncUIToPlayerState' encontre os metadados
                setTrackQueueForMetadata(currentDisplayList);

                // 6. Atualiza o RecyclerView
                if (view != null) {
                    view.displayTracks(currentDisplayList);
                }

                // 7. Sincroniza a UI (título, botões, seekbar) com a música atual
                syncUIToPlayerState();
            });
        });
    }

    private void setupControllerListener() {
        mediaController.addListener(new Player.Listener() {

            public void onMediaItemChanged(MediaItem mediaItem, int reason) {
                // Isto já estava correto
                syncUIToPlayerState();
            }
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                // Isto já estava correto
                syncUIToPlayerState();
            }

            // --- INÍCIO DA CORREÇÃO ---
            // Adicione estes dois métodos
            @Override
            public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
                // Quando o modo shuffle muda, sincroniza a UI imediatamente
                syncUIToPlayerState();
            }

            @Override
            public void onRepeatModeChanged(int repeatMode) {
                // Quando o modo repeat muda, sincroniza a UI imediatamente
                syncUIToPlayerState();
            }
            // --- FIM DA CORREÇÃO ---
        });
    }

    // --- NOVO METODO AUXILIAR PARA FORMATAR O TEMPO (Problema 1) ---
    private String formatTime(long millis) {
        if (millis < 0) millis = 0;
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    // --- CORREÇÃO NO ATUALIZADOR DE PROGRESSO (Problema 1) ---
    private void beginProgressUpdates() {
        cancelProgressUpdates();
        progressUpdateTask = new Runnable() {
            @Override
            public void run() {
                if (mediaController != null && mediaController.isPlaying() && view != null) {
                    long duration = mediaController.getDuration();
                    long position = mediaController.getCurrentPosition();

                    view.updateProgressDisplay((int) position, (int) duration);

                    // --- LINHA ADICIONADA ---
                    // Atualiza os TextViews de tempo
                    view.showTimeProgress(formatTime(position), formatTime(duration));

                    uiUpdateHandler.postDelayed(this, 1000);
                }
            }
        };
        uiUpdateHandler.post(progressUpdateTask);
    }

    // --- CORREÇÃO NO SYNC DA UI (Problema 1) ---
    public void syncUIToPlayerState() {
        if (mediaController == null || view == null) return;

        view.setPlaybackButtonState(mediaController.isPlaying());
        view.updateShuffleButtonState(mediaController.getShuffleModeEnabled());
        view.updateRepeatButtonState(mediaController.getRepeatMode());

        // --- CORREÇÃO AQUI (para tempo inicial/pausado) ---
        long duration = mediaController.getDuration();
        long position = mediaController.getCurrentPosition();

        // Evita mostrar 00:00 / 00:00 se a duração for 0
        if (duration > 0) {
            view.updateProgressDisplay((int) position, (int) duration);
            view.showTimeProgress(formatTime(position), formatTime(duration));
        } else {
            view.updateProgressDisplay(0, 0);
            view.showTimeProgress("00:00", "00:00");
        }
        // --- FIM DA CORREÇÃO ---

        MediaItem currentItem = mediaController.getCurrentMediaItem();
        if (currentItem != null && currentItem.mediaId != null) {
            AudioTrack currentTrack = findTrackByMediaId(this.trackQueue, currentItem.mediaId);
            if (currentTrack != null) {
                view.renderTrackDetails(currentTrack);
            } else if (currentItem.mediaMetadata != null && currentItem.mediaMetadata.title != null) {
                view.renderTrackDetails(new AudioTrack(
                        currentItem.mediaMetadata.title.toString(),
                        currentItem.mediaMetadata.artist != null ? currentItem.mediaMetadata.artist.toString() : "Artista Desconhecido",
                        currentItem.mediaId,
                        currentItem.mediaMetadata.artworkUri != null ? currentItem.mediaMetadata.artworkUri.toString() : null
                ));
            }
        } else if (!this.trackQueue.isEmpty()) {
            view.renderTrackDetails(this.trackQueue.get(0));
        }

        if (mediaController.isPlaying()) {
            beginProgressUpdates();
        } else {
            cancelProgressUpdates(); // Apenas para o loop, o tempo já foi atualizado
        }
    }

    @Override
    public void loadTrackQueue(List<AudioTrack> tracks) {

    }


    public void loadFullLibrary() {
        databaseExecutor.execute(() -> {
            if (fullMusicLibrary.isEmpty()) {
                fullMusicLibrary = fetchLocalMusicLibrary();
            }
            this.isInPlaylistMode = false;
            this.currentDisplayList = new ArrayList<>(fullMusicLibrary);

            new Handler(Looper.getMainLooper()).post(() -> {
                // Carrega a fila no player (isto redefine a fila)
                loadTrackQueueInternal(currentDisplayList, 0, false);
                if (view != null) {
                    view.displayTracks(currentDisplayList);
                }
                syncUIToPlayerState(); // Sincroniza com a música 0
            });
        });
    }


    // Metodo auxiliar para encontrar uma música na biblioteca
    private AudioTrack findTrackByMediaId(List<AudioTrack> trackList, String mediaId) {
        if (mediaId == null) return null;
        for (AudioTrack track : trackList) {
            if (mediaId.equals(track.getSourceUrl())) {
                return track;
            }
        }
        return null;
    }


    public void loadTrackQueue(List<AudioTrack> tracks, int startIndex, boolean playImmediately, boolean isPlaylist) {
        this.isInPlaylistMode = isPlaylist;
        this.currentDisplayList = new ArrayList<>(tracks);

        if (view != null) {
            view.displayTracks(currentDisplayList);
        }

        loadTrackQueueInternal(tracks, startIndex, playImmediately);

        // A sincronização agora é chamada aqui para garantir
        syncUIToPlayerState();
    }

    private void loadTrackQueueInternal(List<AudioTrack> tracks, int startIndex, boolean playImmediately) {
        setTrackQueueForMetadata(tracks);

        if (mediaController != null && tracks != null && !tracks.isEmpty()) {
            List<MediaItem> mediaItems = new ArrayList<>();
            for (AudioTrack track : tracks) {
                MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();
                metadataBuilder.setTitle(track.getTitle());
                metadataBuilder.setArtist(track.getArtist());
                if (track.getArtworkUrl() != null) {
                    metadataBuilder.setArtworkUri(Uri.parse(track.getArtworkUrl()));
                }
                MediaItem item = new MediaItem.Builder()
                        .setUri(track.getSourceUrl())
                        .setMediaId(track.getSourceUrl())
                        .setMediaMetadata(metadataBuilder.build())
                        .build();
                mediaItems.add(item);
            }
            mediaController.setMediaItems(mediaItems, startIndex, 0);
            mediaController.prepare();
            if (playImmediately) {
                mediaController.play();
            }
        }
    }

    public void setTrackQueueForMetadata(List<AudioTrack> tracks) {
        if (tracks != null) {
            this.trackQueue = new ArrayList<>(tracks);
        }
    }

    private void cancelProgressUpdates() {
        if (progressUpdateTask != null) {
            uiUpdateHandler.removeCallbacks(progressUpdateTask);
        }
    }

    private List<AudioTrack> fetchLocalMusicLibrary() {
        List<AudioTrack> localTracks = new ArrayList<>();
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID
        };
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";
        try (Cursor cursor = context.getContentResolver().query(collection, projection, selection, null, sortOrder)) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String title = cursor.getString(titleColumn);
                    String artist = cursor.getString(artistColumn);
                    long albumId = cursor.getLong(albumIdColumn);
                    if (title == null || title.isEmpty()) {
                        title = "Faixa Desconhecida";
                    }
                    if (artist == null || artist.equals("<unknown>")) {
                        artist = "Artista Desconhecido";
                    }
                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    Uri artworkUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);
                    localTracks.add(new AudioTrack(title, artist, contentUri.toString(), artworkUri.toString()));
                }
            }
        }
        return localTracks;
    }

    public boolean isInPlaylistMode() {
        return isInPlaylistMode;
    }

    // --- Métodos de Controlo do Player ---
    @Override public void togglePlayback() {
        if (mediaController == null) return;
        if (mediaController.isPlaying()) {
            mediaController.pause();
        } else {
            mediaController.play();
        }
    }
    @Override public void skipToNextTrack() { if (mediaController != null) mediaController.seekToNextMediaItem(); }
    @Override public void returnToPreviousTrack() { if (mediaController != null) mediaController.seekToPreviousMediaItem(); }
    @Override public void seekToTimestamp(int position) { if (mediaController != null) mediaController.seekTo(position); }
    @Override public void toggleShuffleMode() { if (mediaController != null) mediaController.setShuffleModeEnabled(!mediaController.getShuffleModeEnabled()); }

    @Override
    public void toggleRepeatMode() {
        if (mediaController == null) return;
        int currentMode = mediaController.getRepeatMode();
        if (currentMode == Player.REPEAT_MODE_OFF) {
            mediaController.setRepeatMode(Player.REPEAT_MODE_ALL);
        } else if (currentMode == Player.REPEAT_MODE_ALL) {
            mediaController.setRepeatMode(Player.REPEAT_MODE_ONE);
        } else {
            mediaController.setRepeatMode(Player.REPEAT_MODE_OFF);
        }
    }

    // --- Lógica de Playlist ---
    @Override
    public void onOptionsClicked() {
        databaseExecutor.execute(() -> {
            List<Playlist> playlists = database.playlistDao().getAllPlaylists();
            new Handler(Looper.getMainLooper()).post(() -> {
                if (view != null) {
                    view.showPlaylistsDialog(playlists);
                }
            });
        });
    }

    public void createPlaylist(String name) {
        databaseExecutor.execute(() -> {
            database.playlistDao().insertPlaylist(new Playlist(name));
        });
    }

    public void addCurrentTrackToPlaylist(Playlist playlist) {
        if (mediaController == null) return;
        MediaItem currentItem = mediaController.getCurrentMediaItem();
        if (currentItem == null || currentItem.mediaId == null) return;

        AudioTrack trackToAdd = findTrackByMediaId(this.trackQueue, currentItem.mediaId);

        if (trackToAdd != null) {
            databaseExecutor.execute(() -> {
                database.playlistDao().insertTrack(trackToAdd);
                PlaylistTrackCrossRef crossRef = new PlaylistTrackCrossRef(playlist.playlistId, trackToAdd.getSourceUrl());
                database.playlistDao().addTrackToPlaylist(crossRef);
            });
        }
    }

    // --- Métodos de Reconhecimento ---
    @Override
    public void startMusicRecognition() {
        if (view != null) {
            view.checkAudioPermission();
        }
    }

    @Override
    public void onAudioPermissionGranted() {
        if (view != null) {
            view.showRecognitionStarted();
        }
        if (acrCloudHelper != null) {
            acrCloudHelper.startRecognition();
        }
    }

    public void cancelMusicRecognition() {
        if (acrCloudHelper != null) {
            acrCloudHelper.cancel();
        }
        Log.d(TAG, "Reconhecimento cancelado pelo utilizador.");
    }

    // --- Callbacks do ACRCloud ---
    @Override
    public void onResult(String title, String artist) {
        if (view != null) {
            view.dismissRecognitionDialog();
            view.showRecognitionResult(title, artist);
        }
    }

    @Override
    public void onError(String message) {
        if (view != null) {
            view.dismissRecognitionDialog();
            view.showRecognitionError(message);
        }
    }

    @Override
    public void search(String query) {
        List<AudioTrack> sourceList = isInPlaylistMode ? currentDisplayList : fullMusicLibrary;
        if (query.isEmpty()) {
            if (view != null) view.displayTracks(sourceList);
            return;
        }
        ArrayList<AudioTrack> filteredList = new ArrayList<>();
        String lowerCaseQuery = query.toLowerCase();
        for (AudioTrack track : sourceList) {
            if (track.getTitle().toLowerCase().contains(lowerCaseQuery) ||
                    track.getArtist().toLowerCase().contains(lowerCaseQuery)) {
                filteredList.add(track);
            }
        }
        if (view != null) view.displayTracks(filteredList);
    }
}