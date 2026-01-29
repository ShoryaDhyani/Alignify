package com.alignify.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.alignify.DashboardActivity;
import com.alignify.R;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Firebase Cloud Messaging service for handling push notifications.
 */
public class AlignifyMessagingService extends FirebaseMessagingService {

    private static final String TAG = "AlignifyFCM";
    private static final String CHANNEL_ID = "alignify_notifications";
    private static final String CHANNEL_NAME = "Alignify Notifications";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "FCM Token: " + token);
        // TODO: Send token to server if needed for targeted notifications
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        Log.d(TAG, "Message received from: " + message.getFrom());

        // Handle data payload
        if (message.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + message.getData());
            handleDataMessage(message);
        }

        // Handle notification payload (when app is in foreground)
        if (message.getNotification() != null) {
            String title = message.getNotification().getTitle();
            String body = message.getNotification().getBody();
            showNotification(title, body);
        }
    }

    private void handleDataMessage(RemoteMessage message) {
        String type = message.getData().get("type");
        String title = message.getData().get("title");
        String body = message.getData().get("body");

        if (type != null) {
            switch (type) {
                case "workout_reminder":
                    showWorkoutReminderNotification(title, body);
                    break;
                case "streak_reminder":
                    showStreakReminderNotification(title, body);
                    break;
                case "achievement":
                    showAchievementNotification(title, body);
                    break;
                default:
                    showNotification(title, body);
                    break;
            }
        } else if (title != null && body != null) {
            showNotification(title, body);
        }
    }

    private void showWorkoutReminderNotification(String title, String body) {
        String notificationTitle = title != null ? title : "Time to workout! 💪";
        String notificationBody = body != null ? body : "Keep your streak going with a quick exercise session.";
        showNotification(notificationTitle, notificationBody);
    }

    private void showStreakReminderNotification(String title, String body) {
        String notificationTitle = title != null ? title : "Don't break your streak! 🔥";
        String notificationBody = body != null ? body
                : "You're on a roll! Complete a workout today to keep your streak.";
        showNotification(notificationTitle, notificationBody);
    }

    private void showAchievementNotification(String title, String body) {
        String notificationTitle = title != null ? title : "Achievement Unlocked! 🏆";
        String notificationBody = body != null ? body : "You've earned a new badge. Check it out!";
        showNotification(notificationTitle, notificationBody);
    }

    private void showNotification(String title, String body) {
        createNotificationChannel();

        Intent intent = new Intent(this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title != null ? title : "Alignify")
                .setContentText(body != null ? body : "")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Workout reminders and achievements");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
