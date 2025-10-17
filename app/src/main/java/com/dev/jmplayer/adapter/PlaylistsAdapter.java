package com.dev.jmplayer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.dev.jmplayer.R;
import com.dev.jmplayer.model.Playlist;
import java.util.ArrayList;
import java.util.List;

public class PlaylistsAdapter extends RecyclerView.Adapter<PlaylistsAdapter.PlaylistViewHolder> {

    private List<Playlist> playlists = new ArrayList<>();
    private final OnPlaylistClickListener listener;

    // Interface atualizada para incluir o clique longo
    public interface OnPlaylistClickListener {
        void onPlaylistClicked(Playlist playlist);
        void onPlaylistDeleteClicked(Playlist playlist);
        void onPlaylistLongClicked(Playlist playlist);
    }

    public PlaylistsAdapter(OnPlaylistClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        Playlist currentPlaylist = playlists.get(position);
        holder.bind(currentPlaylist, listener);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    public void setPlaylists(List<Playlist> playlists) {
        this.playlists = playlists;
        notifyDataSetChanged();
    }

    static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private final TextView playlistName;
        private final ImageButton deleteButton;

        public PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            playlistName = itemView.findViewById(R.id.tv_playlist_name);
            deleteButton = itemView.findViewById(R.id.btn_delete_playlist);
        }

        void bind(final Playlist playlist, final OnPlaylistClickListener listener) {
            playlistName.setText(playlist.name);

            // Clique normal para abrir
            itemView.setOnClickListener(v -> listener.onPlaylistClicked(playlist));

            // Clique longo para renomear
            itemView.setOnLongClickListener(v -> {
                listener.onPlaylistLongClicked(playlist);
                return true; // Retorna true para indicar que o evento foi consumido
            });

            // Clique no botão de lixeira para apagar
            deleteButton.setOnClickListener(v -> listener.onPlaylistDeleteClicked(playlist));
        }
    }
}