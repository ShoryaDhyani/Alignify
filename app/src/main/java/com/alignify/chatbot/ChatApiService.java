package com.alignify.chatbot;

import android.util.Log;

import com.alignify.data.DailyActivity;
import com.alignify.data.UserRepository;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Service for communicating with the chatbot backend API.
 * Supports chat persistence with server-side history management.
 */
public class ChatApiService {

    private static final String TAG = "ChatApiService";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Backend URL
    private static final String BASE_URL = "https://alignify.shoryadhyani.me";

    private final OkHttpClient client;
    private final Gson gson;
    private String currentSessionId;

    public ChatApiService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    // ==================== Callback Interfaces ====================

    /**
     * Callback interface for chat responses.
     */
    public interface ChatCallback {
        void onSuccess(String response, String[] sources);

        void onError(String error);
    }

    /**
     * Callback interface for chat history.
     */
    public interface HistoryCallback {
        void onSuccess(String sessionId, List<ChatHistoryMessage> messages);

        void onError(String error);
    }

    /**
     * Callback interface for new chat / delete operations.
     */
    public interface ActionCallback {
        void onSuccess(String sessionId);

        void onError(String error);
    }

    /**
     * Represents a message in chat history.
     */
    public static class ChatHistoryMessage {
        public String id;
        public String role; // "user" or "assistant"
        public String content;
        public String createdAt;

        public boolean isUser() {
            return "user".equals(role);
        }
    }

    // ==================== API Methods ====================

    /**
     * Send a message to the chatbot and get a response.
     * Message is persisted server-side.
     *
     * @param message     User's message
     * @param userId      Firebase user ID for session tracking
     * @param userContext User profile and activity data
     * @param callback    Response callback
     */
    public void sendMessage(String message, String userId, Map<String, Object> userContext, ChatCallback callback) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("message", message);
        requestBody.addProperty("user_id", userId);

        if (userContext != null) {
            requestBody.add("user_context", gson.toJsonTree(userContext));
        }

        RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON);

        Request request = new Request.Builder()
                .url(BASE_URL + "/chat")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Chat request failed", e);
                callback.onError("Connection failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("Server error: " + response.code());
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JsonObject json = gson.fromJson(responseBody, JsonObject.class);

                    String botResponse = json.get("response").getAsString();
                    String[] sources = null;

                    if (json.has("session_id") && !json.get("session_id").isJsonNull()) {
                        currentSessionId = json.get("session_id").getAsString();
                    }

                    if (json.has("sources") && !json.get("sources").isJsonNull()) {
                        sources = gson.fromJson(json.get("sources"), String[].class);
                    }

                    callback.onSuccess(botResponse, sources);

                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse response", e);
                    callback.onError("Failed to parse response");
                }
            }
        });
    }

    /**
     * Fetch chat history for a user.
     * GET /chat-history/{user_id}?limit=50
     *
     * @param userId   Firebase user ID
     * @param limit    Maximum messages to fetch
     * @param callback History callback
     */
    public void getChatHistory(String userId, int limit, HistoryCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/chat-history/" + userId + "?limit=" + limit)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Get history failed", e);
                callback.onError("Connection failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("Server error: " + response.code());
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JsonObject json = gson.fromJson(responseBody, JsonObject.class);

                    String sessionId = null;
                    if (json.has("session_id") && !json.get("session_id").isJsonNull()) {
                        sessionId = json.get("session_id").getAsString();
                        currentSessionId = sessionId;
                    }

                    List<ChatHistoryMessage> messages = new ArrayList<>();
                    if (json.has("messages") && json.get("messages").isJsonArray()) {
                        JsonArray messagesArray = json.getAsJsonArray("messages");
                        for (int i = 0; i < messagesArray.size(); i++) {
                            ChatHistoryMessage msg = gson.fromJson(
                                    messagesArray.get(i), ChatHistoryMessage.class);
                            messages.add(msg);
                        }
                    }

                    callback.onSuccess(sessionId, messages);

                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse history", e);
                    callback.onError("Failed to parse history");
                }
            }
        });
    }

    /**
     * Start a new chat session (deletes previous history).
     * POST /new-chat
     *
     * @param userId   Firebase user ID
     * @param callback Action callback
     */
    public void startNewChat(String userId, ActionCallback callback) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("user_id", userId);
        requestBody.addProperty("confirm", true);

        RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON);

        Request request = new Request.Builder()
                .url(BASE_URL + "/new-chat")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "New chat failed", e);
                callback.onError("Connection failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("Server error: " + response.code());
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JsonObject json = gson.fromJson(responseBody, JsonObject.class);

                    String sessionId = null;
                    if (json.has("session_id") && !json.get("session_id").isJsonNull()) {
                        sessionId = json.get("session_id").getAsString();
                        currentSessionId = sessionId;
                    }

                    callback.onSuccess(sessionId);

                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse new chat response", e);
                    callback.onError("Failed to start new chat");
                }
            }
        });
    }

    /**
     * Delete all chat history for a user.
     * DELETE /chat-history/{user_id}
     *
     * @param userId   Firebase user ID
     * @param callback Action callback
     */
    public void deleteChatHistory(String userId, ActionCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/chat-history/" + userId)
                .delete()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Delete history failed", e);
                callback.onError("Connection failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("Server error: " + response.code());
                    return;
                }

                currentSessionId = null;
                callback.onSuccess(null);
            }
        });
    }

    /**
     * Get current session ID.
     */
    public String getCurrentSessionId() {
        return currentSessionId;
    }

    // ==================== Helper Methods ====================

    /**
     * Build user context from UserRepository data.
     */
    public void buildUserContext(UserRepository.OnProfileLoadedListener profileListener,
            UserRepository.OnDailyActivityListener activityListener) {
        UserRepository repo = UserRepository.getInstance();
        repo.loadUserProfile(profileListener);
        repo.getTodayActivity(activityListener);
    }

    /**
     * Create a user context map from profile and activity data.
     */
    public static Map<String, Object> createUserContext(
            Map<String, Object> profile,
            DailyActivity todayActivity) {
        Map<String, Object> context = new HashMap<>();

        // Add profile data
        if (profile != null) {
            if (profile.containsKey("name"))
                context.put("name", profile.get("name"));
            if (profile.containsKey("age"))
                context.put("age", profile.get("age"));
            if (profile.containsKey("gender"))
                context.put("gender", profile.get("gender"));
            if (profile.containsKey("weight"))
                context.put("weight", profile.get("weight"));
            if (profile.containsKey("height"))
                context.put("height", profile.get("height"));
            if (profile.containsKey("bmi"))
                context.put("bmi", profile.get("bmi"));
            if (profile.containsKey("bmiCategory"))
                context.put("bmiCategory", profile.get("bmiCategory"));
            if (profile.containsKey("activityLevel"))
                context.put("activityLevel", profile.get("activityLevel"));
        }

        // Add today's activity
        if (todayActivity != null) {
            Map<String, Object> activity = new HashMap<>();
            activity.put("steps", todayActivity.getSteps());
            activity.put("calories", todayActivity.getCalories());
            activity.put("activeMinutes", todayActivity.getActiveMinutes());
            activity.put("workoutsCount", todayActivity.getWorkoutsCount());
            activity.put("distance", todayActivity.getDistance());
            context.put("todayActivity", activity);
        }

        return context;
    }

    /**
     * Check if the backend is reachable.
     */
    public void checkHealth(ChatCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/health")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Backend not reachable");
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    callback.onSuccess("healthy", null);
                } else {
                    callback.onError("Backend unhealthy: " + response.code());
                }
            }
        });
    }
}
