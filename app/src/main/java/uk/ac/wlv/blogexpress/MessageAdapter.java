package uk.ac.wlv.blogexpress;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RecyclerView adapter showing post cards (cover photo, badges, title,
 * preview, date and a ⋮ menu). Supports multi-selection for group delete/upload:
 * the "Select" button or a long-press starts selection mode.
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    /** Callbacks to the activity. */
    public interface Listener {
        void onMessageClick(Message message);
        void onMessageMenu(Message message, View anchor);
        void onSelectionChanged(int selectedCount);
    }

    private final List<Message> messages = new ArrayList<>();
    private final Set<Long> selectedIds = new HashSet<>();
    private final Listener listener;
    private boolean selectionMode = false;

    public MessageAdapter(Listener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setMessages(List<Message> list) {
        messages.clear();
        messages.addAll(list);
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void enterSelectionMode() {
        selectionMode = true;
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedIds.size());
    }

    @SuppressLint("NotifyDataSetChanged")
    public void selectAll() {
        selectionMode = true;
        for (Message m : messages) selectedIds.add(m.getId());
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedIds.size());
    }

    @SuppressLint("NotifyDataSetChanged")
    public void clearSelection() {
        selectionMode = false;
        selectedIds.clear();
        notifyDataSetChanged();
    }

    public List<Long> getSelectedIds() {
        return new ArrayList<>(selectedIds);
    }

    public List<Message> getSelectedMessages() {
        List<Message> result = new ArrayList<>();
        for (Message m : messages) {
            if (selectedIds.contains(m.getId())) result.add(m);
        }
        return result;
    }

    private void toggleSelection(Message m, int position) {
        if (!selectedIds.remove(m.getId())) selectedIds.add(m.getId());
        notifyItemChanged(position);
        listener.onSelectionChanged(selectedIds.size());
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(v);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message m = messages.get(position);
        holder.txtTitle.setText(m.getTitle());
        holder.txtPreview.setText(m.getContent());
        holder.txtDate.setText(UiUtils.formatDate(m.getUpdatedAt()));

        boolean hasImage = m.hasImage();
        if (hasImage) {
            holder.imageContainer.setVisibility(View.VISIBLE);
            holder.imgCover.setImageBitmap(ImageUtils.decodeSampled(m.getImagePath(), 600));
        } else {
            holder.imgCover.setImageDrawable(null);
            holder.imageContainer.setVisibility(View.GONE);
        }

        // "Uploaded" badge sits on the photo, or under the title if there is no photo
        holder.txtUploadedOverlay.setVisibility(hasImage && m.isUploaded() ? View.VISIBLE : View.GONE);
        holder.txtUploadedBadge.setVisibility(!hasImage && m.isUploaded() ? View.VISIBLE : View.GONE);
        holder.txtDraftBadge.setVisibility(m.isDraft() ? View.VISIBLE : View.GONE);
        boolean showBadgeRow = m.isDraft() || (!hasImage && m.isUploaded());
        holder.badgeRow.setVisibility(showBadgeRow ? View.VISIBLE : View.GONE);

        holder.card.setChecked(selectedIds.contains(m.getId()));
        holder.btnMore.setVisibility(selectionMode ? View.GONE : View.VISIBLE);
        holder.btnMore.setOnClickListener(v -> listener.onMessageMenu(m, v));

        holder.card.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            if (selectionMode) toggleSelection(m, pos);
            else listener.onMessageClick(m);
        });

        holder.card.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return true;
            if (!selectionMode) {
                selectionMode = true;
                selectedIds.add(m.getId());
                notifyDataSetChanged();
                listener.onSelectionChanged(selectedIds.size());
            } else {
                toggleSelection(m, pos);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final View imageContainer, badgeRow;
        final ImageView imgCover;
        final ImageButton btnMore;
        final TextView txtTitle, txtPreview, txtDate;
        final TextView txtUploadedOverlay, txtUploadedBadge, txtDraftBadge;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card);
            imageContainer = itemView.findViewById(R.id.imageContainer);
            badgeRow = itemView.findViewById(R.id.badgeRow);
            imgCover = itemView.findViewById(R.id.imgCover);
            btnMore = itemView.findViewById(R.id.btnMore);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtPreview = itemView.findViewById(R.id.txtPreview);
            txtDate = itemView.findViewById(R.id.txtDate);
            txtUploadedOverlay = itemView.findViewById(R.id.txtUploadedOverlay);
            txtUploadedBadge = itemView.findViewById(R.id.txtUploadedBadge);
            txtDraftBadge = itemView.findViewById(R.id.txtDraftBadge);
        }
    }
}