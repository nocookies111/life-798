package com.water.widget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * 应用通知渠道与通知构建入口。
 */
public final class AppNotifications {
    public static final String CHANNEL_WATER_SESSION = "water_session";
    public static final String CHANNEL_WATER_RESULT = "water_result";
    public static final String CHANNEL_TASK_PROGRESS = "task_progress";

    public static final int WATER_SESSION_ID = 2201;
    public static final int WATER_RESULT_ID = 2202;
    public static final int TASK_PROGRESS_ID = 2301;

    private AppNotifications() {}

    public static void ensureChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel session = new NotificationChannel(
                CHANNEL_WATER_SESSION,
                "设备运行状态",
                NotificationManager.IMPORTANCE_LOW
        );
        session.setDescription("显示设备启动及用水记录获取状态");
        session.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        session.setShowBadge(false);
        manager.createNotificationChannel(session);

        NotificationChannel result = new NotificationChannel(
                CHANNEL_WATER_RESULT,
                "接水完成",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        result.setDescription("接水完成后显示本次消费与预计水量");
        result.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(result);

        NotificationChannel tasks = new NotificationChannel(
                CHANNEL_TASK_PROGRESS,
                "积分任务进度",
                NotificationManager.IMPORTANCE_LOW
        );
        tasks.setDescription("运行积分任务时显示当前任务与本次积分");
        tasks.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        tasks.setShowBadge(false);
        manager.createNotificationChannel(tasks);
    }

    public static boolean canPost(Context context, String channelId) {
        ensureChannels(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        NotificationChannel channel = manager.getNotificationChannel(channelId);
        return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    public static Notification waterProgress(Context context, String text) {
        ensureChannels(context);
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_WATER_SESSION)
                .setSmallIcon(R.drawable.ic_water_drop)
                .setContentTitle("饮水设备")
                .setContentText(text)
                .setContentIntent(openApp(context, false, 2201))
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setShowWhen(false)
                .setProgress(0, 0, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }

    public static Notification waterResult(
            Context context,
            String title,
            String text,
            boolean recovery
    ) {
        ensureChannels(context);
        return new Notification.Builder(context, CHANNEL_WATER_RESULT)
                .setSmallIcon(R.drawable.ic_water_drop)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(openApp(context, recovery, 2202))
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .build();
    }

    public static PendingIntent openApp(Context context, boolean recovery, int requestCode) {
        Intent intent = new Intent(context, ConfigActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (recovery) {
            intent.putExtra(ConfigActivity.EXTRA_OPEN_WATER_RECOVERY, true);
        }
        return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
