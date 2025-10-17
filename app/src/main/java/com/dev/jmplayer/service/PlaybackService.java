package com.dev.jmplayer.service;

import android.content.Intent;
import android.os.Bundle;

// --- NOVAS IMPORTAÇÕES ---
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C; // Importante para as constantes (Usage, ContentType)
// -------------------------

import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

public class PlaybackService extends MediaSessionService {

    private MediaSession mediaSession;
    private ExoPlayer player;

    @Override
    public void onCreate() {
        super.onCreate();


        // 1. Criar os Atributos de Áudio
        // Isto diz ao sistema Android que tipo de som estamos a tocar.
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA) // Define o propósito como "reprodução de média"
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC) // Define o conteúdo como "música"
                .build();

        // 2. Construir o ExoPlayer com os atributos
        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true) // O 'true' liga a gestão automática de foco!
                .setHandleAudioBecomingNoisy(true) // Pausa se os fones forem desconectados
                .build();

        // O resto do seu código (que já tínhamos)
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setShuffleModeEnabled(false);

        mediaSession = new MediaSession.Builder(this, player).build();
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}