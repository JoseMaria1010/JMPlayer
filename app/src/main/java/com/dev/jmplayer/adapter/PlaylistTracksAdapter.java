package com.dev.jmplayer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton; // Nova importação
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.dev.jmplayer.R;
import com.dev.jmplayer.model.AudioTrack;
import java.util.ArrayList;
import java.util.List;

public class PlaylistTracksAdapter extends RecyclerView.Adapter<PlaylistTracksAdapter.TrackViewHolder> {

    private List<AudioTrack> tracks = new ArrayList<>();
    private final OnTrackClickListener listener;

    // Interface atualizada para incluir a ação de remover
    public interface OnTrackClickListener {
        void onTrackClick(int position, ArrayList<AudioTrack> playlist);
        void onRemoveTrackClick(AudioTrack track);
    }

    public PlaylistTracksAdapter(OnTrackClickListener listener) {
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
        holder.bind(currentTrack, listener);
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    public void setTracks(List<AudioTrack> tracks) {
        this.tracks = tracks;
        notifyDataSetChanged();
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        private final TextView trackTitle;
        private final TextView trackArtist;
        private final ImageButton removeButton;
        private final ImageView trackArtwork;

        public TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            trackTitle = itemView.findViewById(R.id.tv_track_title);
            trackArtist = itemView.findViewById(R.id.tv_track_artist);
            removeButton = itemView.findViewById(R.id.btn_remove_track);
            trackArtwork = itemView.findViewById(R.id.iv_track_artwork);
        }

        void bind(final AudioTrack track, final OnTrackClickListener listener) {
            trackTitle.setText(track.getTitle());
            trackArtist.setText(track.getArtist());
            removeButton.setVisibility(View.VISIBLE);

            // --- USAR GLIDE PARA CARREGAR A CAPA ---
            if (trackArtwork != null) {
                Glide.with(itemView.getContext())
                        .load(track.getArtworkUrl())
                        .placeholder(R.drawable.ic_music_note_placeholder)
                        .error(R.drawable.ic_music_note_placeholder)
                        .into(trackArtwork);
            }
            // ----------------------------------------

            itemView.setOnClickListener(v -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onTrackClick(getAdapterPosition(), (ArrayList<AudioTrack>) ((PlaylistTracksAdapter) getBindingAdapter()).tracks);
                }
            });

            removeButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRemoveTrackClick(track);
                }
            });
        }
    }


}