package com.alignify.chatbot;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alignify.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for chat messages.
 */
public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_BOT = 2;
    private static final int VIEW_TYPE_LOADING = 3;

    private final List<ChatMessage> messages;
    private final SimpleDateFormat timeFormat;

    public ChatMessageAdapter() {
        this.messages = new ArrayList<>();
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messages.get(position);
        if (message.isLoading()) {
            return VIEW_TYPE_LOADING;
        }
        return message.isFromUser() ? VIEW_TYPE_USER : VIEW_TYPE_BOT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        switch (viewType) {
            case VIEW_TYPE_USER:
                return new UserMessageViewHolder(
                        inflater.inflate(R.layout.item_chat_message_user, parent, false));
            case VIEW_TYPE_LOADING:
                return new LoadingViewHolder(
                        inflater.inflate(R.layout.item_chat_loading, parent, false));
            case VIEW_TYPE_BOT:
            default:
                return new BotMessageViewHolder(
                        inflater.inflate(R.layout.item_chat_message_bot, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);

        if (holder instanceof UserMessageViewHolder) {
            ((UserMessageViewHolder) holder).bind(message);
        } else if (holder instanceof BotMessageViewHolder) {
            ((BotMessageViewHolder) holder).bind(message);
        }
        // Loading view doesn't need binding
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // ==================== Public Methods ====================

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void removeLoadingMessage() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).isLoading()) {
                messages.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    public void updateLastBotMessage(String content, String[] sources) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (!msg.isFromUser() && !msg.isLoading()) {
                msg.setContent(content);
                msg.setSources(sources);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void clearMessages() {
        messages.clear();
        notifyDataSetChanged();
    }

    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    // ==================== ViewHolders ====================

    class UserMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;
        private final TextView timeText;

        UserMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.text_message);
            timeText = itemView.findViewById(R.id.text_time);
        }

        void bind(ChatMessage message) {
            messageText.setText(message.getContent());
            timeText.setText(timeFormat.format(new Date(message.getTimestamp())));
        }
    }

    class BotMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;
        private final TextView timeText;
        private final TextView sourcesText;

        BotMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.text_message);
            timeText = itemView.findViewById(R.id.text_time);
            sourcesText = itemView.findViewById(R.id.text_sources);
        }

        void bind(ChatMessage message) {
            messageText.setText(message.getContent());
            timeText.setText(timeFormat.format(new Date(message.getTimestamp())));

            // Show sources if available
            String[] sources = message.getSources();
            if (sources != null && sources.length > 0 && sourcesText != null) {
                sourcesText.setVisibility(View.VISIBLE);
                sourcesText.setText("📚 " + String.join(", ", sources));
            } else if (sourcesText != null) {
                sourcesText.setVisibility(View.GONE);
            }
        }
    }

    class LoadingViewHolder extends RecyclerView.ViewHolder {
        LoadingViewHolder(View itemView) {
            super(itemView);
            // Loading animation is handled in XML
        }
    }
}
