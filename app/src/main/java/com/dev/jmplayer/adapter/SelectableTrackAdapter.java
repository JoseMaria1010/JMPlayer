package com.dev.jmplayer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.dev.jmplayer.R;
import com.dev.jmplayer.model.AudioTrack;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectableTrackAdapter extends RecyclerView.Adapter<SelectableTrackAdapter.TrackViewHolder> {

    private List<AudioTrack> allTracks = new ArrayList<>();
    private Set<AudioTrack> selectedTracks = new HashSet<>();

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_selectable_track, parent, false);
        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        AudioTrack currentTrack = allTracks.get(position);
        holder.bind(currentTrack, selectedTracks.contains(currentTrack));

        holder.itemView.setOnClickListener(v -> {
            if (selectedTracks.contains(currentTrack)) {
                selectedTracks.remove(currentTrack);
            } else {
                selectedTracks.add(currentTrack);
            }
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return allTracks.size();
    }

    public void setTracks(List<AudioTrack> tracks) {
        this.allTracks = tracks;
        notifyDataSetChanged();
    }

    public ArrayList<AudioTrack> getSelectedTracks() {
        return new ArrayList<>(selectedTracks);
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        private final TextView trackTitle;
        private final TextView trackArtist;
        private final CheckBox checkBox;

        public TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            trackTitle = itemView.findViewById(R.id.tv_track_title);
            trackArtist = itemView.findViewById(R.id.tv_track_artist);
            checkBox = itemView.findViewById(R.id.checkbox_select_track);
        }

        void bind(final AudioTrack track, boolean isSelected) {
            trackTitle.setText(track.getTitle());
            trackArtist.setText(track.getArtist());
            checkBox.setChecked(isSelected);
        }
    }
}