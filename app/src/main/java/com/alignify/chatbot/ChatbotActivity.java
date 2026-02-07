package com.alignify.chatbot;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alignify.R;
import com.alignify.data.DailyActivity;
import com.alignify.data.UserRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chatbot Activity with text and voice chat support.
 * Supports chat persistence with server-side history.
 */
public class ChatbotActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final String TAG = "ChatbotActivity";
    private static final int REQUEST_RECORD_AUDIO = 1;

    // ==================== State Management ====================
    private enum ChatState {
        LOADING, READY, SENDING, RESETTING, ERROR
    }

    private ChatState currentState = ChatState.LOADING;

    // ==================== UI Components ====================
    private RecyclerView recyclerMessages;
    private LinearLayout layoutWelcome;
    private LinearLayout layoutError;
    private FrameLayout layoutLoading;
    private EditText editMessage;
    private ImageButton btnSend;
    private ImageButton btnVoice;
    private ImageButton btnClear;
    private ImageButton btnNewChat;
    private Button btnRetry;
    private TextView textStatus;
    private TextView textLoading;
    private TextView textError;
    private LinearLayout layoutInput;

    // ==================== Adapters and Services ====================
    private ChatMessageAdapter adapter;
    private ChatApiService apiService;

    // ==================== Voice ====================
    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private boolean ttsEnabled = false;

    // ==================== User Context ====================
    private Map<String, Object> userProfile;
    private DailyActivity todayActivity;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        initUserId();
        initViews();
        initServices();
        loadUserContext();
        setupListeners();
        loadChatHistory();
    }

    private void initUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userId = user.getUid();
        } else {
            userId = "anonymous_" + System.currentTimeMillis();
        }
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerMessages = findViewById(R.id.recycler_messages);
        layoutWelcome = findViewById(R.id.layout_welcome);
        layoutLoading = findViewById(R.id.layout_loading);
        layoutError = findViewById(R.id.layout_error);
        editMessage = findViewById(R.id.edit_message);
        btnSend = findViewById(R.id.btn_send);
        btnVoice = findViewById(R.id.btn_voice);
        btnClear = findViewById(R.id.btn_clear);
        btnNewChat = findViewById(R.id.btn_new_chat);
        btnRetry = findViewById(R.id.btn_retry);
        textStatus = findViewById(R.id.text_status);
        textLoading = findViewById(R.id.text_loading);
        textError = findViewById(R.id.text_error);
        layoutInput = findViewById(R.id.layout_input);

        // Setup RecyclerView
        adapter = new ChatMessageAdapter();
        recyclerMessages.setLayoutManager(new LinearLayoutManager(this));
        recyclerMessages.setAdapter(adapter);

        // Setup suggestion chips
        Chip chip1 = findViewById(R.id.chip_suggest_1);
        Chip chip2 = findViewById(R.id.chip_suggest_2);
        Chip chip3 = findViewById(R.id.chip_suggest_3);

        chip1.setOnClickListener(v -> sendMessage(chip1.getText().toString()));
        chip2.setOnClickListener(v -> sendMessage(chip2.getText().toString()));
        chip3.setOnClickListener(v -> sendMessage(chip3.getText().toString()));
    }

    private void initServices() {
        apiService = new ChatApiService();

        // Initialize Text-to-Speech
        textToSpeech = new TextToSpeech(this, this);

        // Initialize Speech Recognizer
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new VoiceRecognitionListener());
        }
    }

    private void loadUserContext() {
        UserRepository repo = UserRepository.getInstance();

        // Load user profile
        repo.loadUserProfile(new UserRepository.OnProfileLoadedListener() {
            @Override
            public void onProfileLoaded(Map<String, Object> profile) {
                userProfile = profile;
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load profile: " + error);
            }
        });

        // Load today's activity
        repo.getTodayActivity(new UserRepository.OnDailyActivityListener() {
            @Override
            public void onActivityLoaded(DailyActivity activity) {
                todayActivity = activity;
            }
        });
    }

    private void setupListeners() {
        // Send button
        btnSend.setOnClickListener(v -> {
            String message = editMessage.getText().toString().trim();
            if (!message.isEmpty() && currentState == ChatState.READY) {
                sendMessage(message);
            }
        });

        // Voice button
        btnVoice.setOnClickListener(v -> {
            if (currentState != ChatState.READY)
                return;
            if (isListening) {
                stopListening();
            } else {
                startListening();
            }
        });

        // New Chat button
        btnNewChat.setOnClickListener(v -> showNewChatConfirmation());

        // Clear/Delete chat button
        btnClear.setOnClickListener(v -> showDeleteConfirmation());

        // Retry button
        btnRetry.setOnClickListener(v -> loadChatHistory());

        // Text input watcher
        editMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Toggle send/voice button based on input
                btnSend.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                btnVoice.setVisibility(s.length() > 0 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Initially show voice button
        btnSend.setVisibility(View.GONE);
        btnVoice.setVisibility(View.VISIBLE);
    }

    // ==================== State Management ====================

    private void updateUIState(ChatState state) {
        currentState = state;

        runOnUiThread(() -> {
            switch (state) {
                case LOADING:
                    layoutLoading.setVisibility(View.VISIBLE);
                    layoutError.setVisibility(View.GONE);
                    layoutWelcome.setVisibility(View.GONE);
                    recyclerMessages.setVisibility(View.GONE);
                    setInputEnabled(false);
                    textLoading.setText("Loading chat...");
                    break;

                case READY:
                    layoutLoading.setVisibility(View.GONE);
                    layoutError.setVisibility(View.GONE);
                    setInputEnabled(true);
                    textStatus.setText("Your AI Fitness Coach");
                    // Show welcome or messages based on content
                    if (adapter.getItemCount() == 0) {
                        layoutWelcome.setVisibility(View.VISIBLE);
                        recyclerMessages.setVisibility(View.GONE);
                    } else {
                        layoutWelcome.setVisibility(View.GONE);
                        recyclerMessages.setVisibility(View.VISIBLE);
                    }
                    break;

                case SENDING:
                    setInputEnabled(false);
                    textStatus.setText("Thinking...");
                    break;

                case RESETTING:
                    layoutLoading.setVisibility(View.VISIBLE);
                    layoutError.setVisibility(View.GONE);
                    layoutWelcome.setVisibility(View.GONE);
                    recyclerMessages.setVisibility(View.GONE);
                    setInputEnabled(false);
                    textLoading.setText("Starting new chat...");
                    break;

                case ERROR:
                    layoutLoading.setVisibility(View.GONE);
                    layoutError.setVisibility(View.VISIBLE);
                    layoutWelcome.setVisibility(View.GONE);
                    recyclerMessages.setVisibility(View.GONE);
                    setInputEnabled(false);
                    break;
            }
        });
    }

    private void setInputEnabled(boolean enabled) {
        editMessage.setEnabled(enabled);
        btnSend.setEnabled(enabled);
        btnVoice.setEnabled(enabled);
        btnNewChat.setEnabled(enabled);
        btnClear.setEnabled(enabled);
        layoutInput.setAlpha(enabled ? 1.0f : 0.5f);
    }

    // ==================== Chat History ====================

    private static final String PREFS_CHAT_CACHE = "ChatCache";
    private static final String KEY_CACHED_MESSAGES = "cached_messages_";

    private void loadChatHistory() {
        // Try loading from cache first
        List<ChatMessage> cachedMessages = loadFromCache();

        if (cachedMessages != null && !cachedMessages.isEmpty()) {
            // Cache found - display immediately
            Log.d(TAG, "Loaded " + cachedMessages.size() + " messages from cache");
            adapter.clearMessages();
            for (ChatMessage msg : cachedMessages) {
                adapter.addMessage(msg);
            }
            scrollToBottom();
            updateUIState(ChatState.READY);
        } else {
            // Cache empty - fetch from API
            loadChatHistoryFromApi();
        }
    }

    private void loadChatHistoryFromApi() {
        updateUIState(ChatState.LOADING);

        apiService.getChatHistory(userId, 50, new ChatApiService.HistoryCallback() {
            @Override
            public void onSuccess(String sessionId, List<ChatApiService.ChatHistoryMessage> messages) {
                runOnUiThread(() -> {
                    adapter.clearMessages();

                    if (messages != null && !messages.isEmpty()) {
                        for (ChatApiService.ChatHistoryMessage msg : messages) {
                            if (msg.isUser()) {
                                adapter.addMessage(ChatMessage.userMessage(msg.content));
                            } else {
                                adapter.addMessage(ChatMessage.botMessage(msg.content));
                            }
                        }
                        scrollToBottom();
                        // Cache the loaded messages
                        saveToCache(adapter.getMessages());
                    }

                    updateUIState(ChatState.READY);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Failed to load history: " + error);
                    textError.setText("Failed to load chat history\n" + error);
                    updateUIState(ChatState.ERROR);
                });
            }
        });
    }

    private void saveToCache(List<ChatMessage> messages) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String json = gson.toJson(messages);
            getSharedPreferences(PREFS_CHAT_CACHE, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CACHED_MESSAGES + userId, json)
                    .apply();
            Log.d(TAG, "Saved " + messages.size() + " messages to cache");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save cache", e);
        }
    }

    private List<ChatMessage> loadFromCache() {
        try {
            String json = getSharedPreferences(PREFS_CHAT_CACHE, MODE_PRIVATE)
                    .getString(KEY_CACHED_MESSAGES + userId, null);
            if (json != null && !json.isEmpty()) {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<ChatMessage>>() {
                }.getType();
                return gson.fromJson(json, listType);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load cache", e);
        }
        return null;
    }

    private void clearCache() {
        getSharedPreferences(PREFS_CHAT_CACHE, MODE_PRIVATE)
                .edit()
                .remove(KEY_CACHED_MESSAGES + userId)
                .apply();
        Log.d(TAG, "Cache cleared");
    }

    // ==================== Send Message ====================

    private void sendMessage(String message) {
        if (currentState != ChatState.READY)
            return;

        // Update UI immediately
        updateUIState(ChatState.SENDING);
        layoutWelcome.setVisibility(View.GONE);
        recyclerMessages.setVisibility(View.VISIBLE);

        // Add user message
        adapter.addMessage(ChatMessage.userMessage(message));
        scrollToBottom();

        // Clear input
        editMessage.setText("");

        // Show loading indicator
        adapter.addMessage(ChatMessage.loadingMessage());
        scrollToBottom();

        // Build user context
        Map<String, Object> context = ChatApiService.createUserContext(userProfile, todayActivity);

        // Send to API with userId
        apiService.sendMessage(message, userId, context, new ChatApiService.ChatCallback() {
            @Override
            public void onSuccess(String response, String[] sources) {
                runOnUiThread(() -> {
                    adapter.removeLoadingMessage();

                    ChatMessage botMessage = ChatMessage.botMessage(response);
                    botMessage.setSources(sources);
                    adapter.addMessage(botMessage);

                    scrollToBottom();
                    updateUIState(ChatState.READY);

                    // Update cache with new messages
                    saveToCache(adapter.getMessages());

                    // Speak response if TTS enabled
                    if (ttsEnabled && textToSpeech != null) {
                        speakResponse(response);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    adapter.removeLoadingMessage();

                    ChatMessage errorMessage = ChatMessage.botMessage(
                            "Sorry, I couldn't connect to the server. Please try again.\n\nError: " + error);
                    adapter.addMessage(errorMessage);

                    scrollToBottom();
                    updateUIState(ChatState.READY);

                    Toast.makeText(ChatbotActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void scrollToBottom() {
        if (adapter.getItemCount() > 0) {
            recyclerMessages.smoothScrollToPosition(adapter.getItemCount() - 1);
        }
    }

    // ==================== New Chat / Delete ====================

    private void showNewChatConfirmation() {
        if (currentState != ChatState.READY)
            return;

        new AlertDialog.Builder(this)
                .setTitle("New Chat")
                .setMessage("This will clear your current conversation and start fresh. Continue?")
                .setPositiveButton("New Chat", (dialog, which) -> startNewChat())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startNewChat() {
        updateUIState(ChatState.RESETTING);
        adapter.clearMessages();
        clearCache(); // Clear local cache

        apiService.startNewChat(userId, new ChatApiService.ActionCallback() {
            @Override
            public void onSuccess(String sessionId) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatbotActivity.this, "New chat started", Toast.LENGTH_SHORT).show();
                    updateUIState(ChatState.READY);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatbotActivity.this, "Failed to start new chat: " + error, Toast.LENGTH_SHORT)
                            .show();
                    updateUIState(ChatState.READY);
                });
            }
        });
    }

    private void showDeleteConfirmation() {
        if (currentState != ChatState.READY)
            return;

        new AlertDialog.Builder(this)
                .setTitle("Delete Chat History")
                .setMessage("This will permanently delete all your chat history. This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteChatHistory())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteChatHistory() {
        updateUIState(ChatState.RESETTING);
        adapter.clearMessages();
        clearCache(); // Clear local cache

        apiService.deleteChatHistory(userId, new ChatApiService.ActionCallback() {
            @Override
            public void onSuccess(String sessionId) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatbotActivity.this, "Chat history deleted", Toast.LENGTH_SHORT).show();
                    updateUIState(ChatState.READY);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatbotActivity.this, "Failed to delete: " + error, Toast.LENGTH_SHORT).show();
                    updateUIState(ChatState.READY);
                });
            }
        });
    }

    // ==================== Voice Features ====================

    private void startListening() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.RECORD_AUDIO }, REQUEST_RECORD_AUDIO);
            return;
        }

        if (speechRecognizer == null) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show();
            return;
        }

        isListening = true;
        btnVoice.setImageResource(R.drawable.ic_mic);
        btnVoice.setBackgroundResource(R.drawable.bg_circle_accent);
        textStatus.setText("Listening...");

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        speechRecognizer.startListening(intent);
    }

    private void stopListening() {
        isListening = false;
        btnVoice.setBackgroundResource(R.drawable.bg_circle_surface);
        textStatus.setText("Your AI Fitness Coach");

        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    private void speakResponse(String text) {
        if (textToSpeech != null) {
            // Limit speech to first 500 characters for lengthy responses
            String speech = text.length() > 500 ? text.substring(0, 500) + "..." : text;
            textToSpeech.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "response");
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS language not supported");
                ttsEnabled = false;
            }
        } else {
            Log.e(TAG, "TTS initialization failed");
            ttsEnabled = false;
        }
    }

    // ==================== Voice Recognition Listener ====================

    private class VoiceRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
            textStatus.setText("Listening...");
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
            stopListening();
        }

        @Override
        public void onError(int error) {
            stopListening();
            String errorMessage = "Voice recognition error";
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    errorMessage = "Audio recording error";
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    errorMessage = "Didn't catch that. Please try again.";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    errorMessage = "Network error";
                    break;
            }
            Toast.makeText(ChatbotActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String spokenText = matches.get(0);
                sendMessage(spokenText);
            }
            stopListening();
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }

    // ==================== Permissions ====================

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                Toast.makeText(this, "Microphone permission required for voice input",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==================== Lifecycle ====================

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}
