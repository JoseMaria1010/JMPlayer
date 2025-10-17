package com.dev.jmplayer.contract;

import com.dev.jmplayer.model.AudioTrack;
import com.dev.jmplayer.model.Playlist;

import java.util.List;

public interface SoundDeckContract {

    interface View {
        void renderTrackDetails(AudioTrack track);
        void setPlaybackButtonState(boolean isPlaying);
        void updateProgressDisplay(int currentPosition, int maxDuration);
        void showTimeProgress(String currentTime, String totalTime);
        void showPlaylistsDialog(List<Playlist> playlists);
        void updateRepeatButtonState(int repeatMode);
        void updateShuffleButtonState(boolean shuffleModeEnabled);
        void animateArtwork(boolean shouldAnimate);
        void displayTracks(List<AudioTrack> tracks);
        void checkAudioPermission(); // Pede à View para verificar a permissão do microfone
        void showRecognitionStarted(); // Mostra um diálogo/feedback de "A ouvir..."
        void dismissRecognitionDialog(); // Esconde o diálogo de "A ouvir..."
        void showRecognitionResult(String title, String artist); // Mostra o resultado
        void showRecognitionError(String message);
    }

    interface Presenter {
        void bindView(View view);
        void unbindView();
        void loadTrackQueue(List<AudioTrack> tracks); // Metodo para carregar a lista de músicas
        void togglePlayback();
        void skipToNextTrack();
        void returnToPreviousTrack();
        void seekToTimestamp(int position);
        void onOptionsClicked();
        void addCurrentTrackToPlaylist(Playlist selectedPlaylist);
        void createPlaylist(String playlistName);
        void toggleRepeatMode();
        void toggleShuffleMode();
        void search(String query);
        void startMusicRecognition();
        void onAudioPermissionGranted();
        void resyncUI();
    }
}