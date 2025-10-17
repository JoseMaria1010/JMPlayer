package com.dev.jmplayer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.dev.jmplayer.R;
import com.dev.jmplayer.model.AudioTrack;
import java.util.ArrayList;
import java.util.List;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {

    private List<AudioTrack> tracks = new ArrayList<>();
    private final OnTrackClickListener listener;

    public interface OnTrackClickListener {
        void onTrackClick(AudioTrack track, int position);
    }

    public TrackAdapter(OnTrackClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track, parent, false);
        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        AudioTrack currentTrack = tracks.get(position);
        holder.bind(currentTrack, listener, position);
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    public List<AudioTrack> getTracks() {
        return tracks;
    }

    public void setTracks(List<AudioTrack> tracks) {
        this.tracks = tracks;
        notifyDataSetChanged();
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        private final TextView trackTitle;
        private final TextView trackArtist;
        private final ImageView trackArtwork;

        public TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            trackTitle = itemView.findViewById(R.id.tv_track_title);
            trackArtist = itemView.findViewById(R.id.tv_track_artist);
            trackArtwork = itemView.findViewById(R.id.iv_track_artwork);
        }

        void bind(final AudioTrack track, final OnTrackClickListener listener, final int position) {
            trackTitle.setText(track.getTitle());
            trackArtist.setText(track.getArtist());

            // --- USAR GLIDE PARA CARREGAR A CAPA ---
            if (trackArtwork != null) {
                Glide.with(itemView.getContext())
                        .load(track.getArtworkUrl())
                        .placeholder(R.drawable.ic_music_note_placeholder) // O placeholder que já temos
                        .error(R.drawable.ic_music_note_placeholder) // Em caso de falha
                        .into(trackArtwork);
            }
            // ----------------------------------------

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTrackClick(track, position);
                }
            });
        }
    }
}