package com.water.widget;

import android.app.NotificationManager;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.water.widget.ui.UsageHistoryStore;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 启动饮水设备并监测本次消费流水。
 *
 * 设备需要通过实体按钮完成实际出水；服务端生成新的成功消费流水后，
 * 发送本次消费与预计水量通知。
 */
public class WaterService extends Service {
    public static final String EXTRA_DID = "extra_did";
    public static final String EXTRA_WIDGET_ID = "extra_widget_id";
    private static final String EXTRA_APP_TOKEN = "extra_app_token";
    private static final String EXTRA_SCORE_TOKEN = "extra_score_token";
    private static final String EXTRA_ACCOUNT_KEY = "extra_account_key";

    private static final long BASELINE_TIMEOUT_MILLIS = 4_000L;
    private static final long FIRST_POLL_DELAY_MILLIS = 8_000L;
    private static final long POLL_INTERVAL_MILLIS = 10_000L;
    private static final long MAX_MONITOR_MILLIS = 10 * 60_000L;
    private static final String EXTRA_RESERVATION_ID = "extra_reservation_id";
    private static final AtomicLong NEXT_RESERVATION_ID = new AtomicLong();
    private static final AtomicLong ACTIVE_RESERVATION_ID = new AtomicLong();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Session session;
    private long ownedReservationId;

    public enum StartResult {
        STARTED,
        ALREADY_RUNNING,
        FAILED
    }

    /**
     * 同一进程同一时间只允许一个接水监测会话，避免连续点击后把 A 设备的流水归给 B。
     */
    public static StartResult start(Context context, String did, int widgetId) {
        if (did == null || did.trim().isEmpty()) return StartResult.FAILED;
        Account account = AccountStore.getCurrent(context);
        if (account == null || !account.hasAppToken()) return StartResult.FAILED;
        long reservationId = NEXT_RESERVATION_ID.incrementAndGet();
        if (!ACTIVE_RESERVATION_ID.compareAndSet(0L, reservationId)) {
            return StartResult.ALREADY_RUNNING;
        }
        String accountKey = account.phone != null && !account.phone.isEmpty()
                ? account.phone
                : account.uid;
        String scoreToken = account.hasToken() ? account.token : account.appToken;
        Intent intent = new Intent(context, WaterService.class)
                .putExtra(EXTRA_DID, did)
                .putExtra(EXTRA_WIDGET_ID, widgetId)
                .putExtra(EXTRA_APP_TOKEN, account.appToken)
                .putExtra(EXTRA_SCORE_TOKEN, scoreToken)
                .putExtra(EXTRA_ACCOUNT_KEY, accountKey == null ? "" : accountKey)
                .putExtra(EXTRA_RESERVATION_ID, reservationId);
        try {
            androidx.core.content.ContextCompat.startForegroundService(context, intent);
            return StartResult.STARTED;
        } catch (RuntimeException error) {
            releaseReservation(reservationId);
            return StartResult.FAILED;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            releaseReservation(ownedReservationId);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        long reservationId = intent.getLongExtra(EXTRA_RESERVATION_ID, 0L);
        if (
                reservationId <= 0L ||
                ACTIVE_RESERVATION_ID.get() != reservationId
        ) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        ownedReservationId = reservationId;
        if (session != null) {
            return START_NOT_STICKY;
        }

        final String did = intent.getStringExtra(EXTRA_DID);
        final int widgetId = intent.getIntExtra(
                EXTRA_WIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
        );
        final String appToken = intent.getStringExtra(EXTRA_APP_TOKEN);
        final String scoreToken = intent.getStringExtra(EXTRA_SCORE_TOKEN);
        final String accountKey = intent.getStringExtra(EXTRA_ACCOUNT_KEY);
        AppNotifications.ensureChannels(this);
        startAsForeground("正在准备设备…");

        if (
                did == null ||
                did.trim().isEmpty() ||
                appToken == null ||
                appToken.isEmpty()
        ) {
            finishImmediate(
                    "设备启动失败",
                    "请检查设备控制登录信息与当前设备",
                    true,
                    startId
            );
            return START_NOT_STICKY;
        }

        Session started = new Session(
                startId,
                widgetId,
                reservationId,
                did,
                appToken,
                scoreToken,
                accountKey == null ? "" : accountKey
        );
        session = started;
        captureBaselines(started);
        return START_NOT_STICKY;
    }

    /**
     * 在真正启动设备前获取现有账单与积分流水 ID。即使旧订单稍后更新状态，
     * 也不会被误认为本次接水；网络较慢时最多等待四秒，不长期阻塞设备使用。
     */
    private void captureBaselines(Session current) {
        current.pendingBaselines = 0;
        if (!current.billToken.isEmpty()) current.pendingBaselines++;
        if (!current.scoreToken.isEmpty()) current.pendingBaselines++;

        if (current.pendingBaselines == 0) {
            beginDeviceStart(current);
            return;
        }

        mainHandler.postDelayed(
                () -> beginDeviceStart(current),
                BASELINE_TIMEOUT_MILLIS
        );

        if (!current.billToken.isEmpty()) {
            IlifeApi.billListWithToken(current.billToken, (json, err) ->
                    onMain(() -> {
                        if (!isActive(current) || current.baselineFrozen) return;
                        current.billBaseline = WaterBillParser.INSTANCE.recordKeys(json);
                        baselineFinished(current);
                    })
            );
        }
        if (!current.scoreToken.isEmpty()) {
            IlifeApi.scoreLstWithToken(current.scoreToken, (json, err) ->
                    onMain(() -> {
                        if (!isActive(current) || current.baselineFrozen) return;
                        current.scoreBaseline = WaterConsumptionParser.INSTANCE.recordKeys(json);
                        baselineFinished(current);
                    })
            );
        }
    }

    private void baselineFinished(Session current) {
        current.pendingBaselines = Math.max(0, current.pendingBaselines - 1);
        if (current.pendingBaselines == 0) beginDeviceStart(current);
    }

    private void beginDeviceStart(Session current) {
        if (!isActive(current) || current.baselineFrozen) return;
        current.baselineFrozen = true;
        WaterApi.startWithToken(current.billToken, current.did, status ->
                onMain(() -> {
                    if (!isActive(current)) return;
                    updateWidget(current.widgetId, status);
                    if (!isSuccessStatus(status)) {
                        finishWithResult(
                                current,
                                "设备启动失败",
                                status == null ? "请返回应用检查设备与登录信息" : status,
                                true
                        );
                        return;
                    }

                    // 服务端流水通常只精确到秒；启动前基线负责排除同秒旧记录。
                    current.monitoringStartedAt =
                            System.currentTimeMillis() / 1_000L * 1_000L;
                    notifyWaterProgress("设备已启动，等待接水完成…");
                    mainHandler.postDelayed(
                            () -> pollConsumption(current),
                            FIRST_POLL_DELAY_MILLIS
                    );
                })
        );
    }

    private void pollConsumption(Session current) {
        if (!isActive(current)) return;
        if (System.currentTimeMillis() - current.monitoringStartedAt >= MAX_MONITOR_MILLIS) {
            finishWithResult(
                    current,
                    "接水会话已结束",
                    "暂未获取到本次消费记录，可稍后在消费统计中查看",
                    false
            );
            return;
        }
        if (!current.billToken.isEmpty()) {
            IlifeApi.billListWithToken(current.billToken, (billJson, billErr) ->
                    onMain(() -> {
                        if (!isActive(current)) return;
                        WaterConsumption bill = WaterBillParser.INSTANCE.latestSince(
                                billJson,
                                current.monitoringStartedAt,
                                current.did,
                                current.billBaseline
                        );
                        if (bill != null) {
                            finishConsumption(current, bill);
                        } else {
                            pollScoreConsumption(current);
                        }
                    })
            );
        } else {
            pollScoreConsumption(current);
        }
    }

    private void pollScoreConsumption(Session current) {
        if (!isActive(current)) return;
        if (current.scoreToken.isEmpty()) {
            scheduleNextPoll(current);
            return;
        }
        IlifeApi.scoreLstWithToken(current.scoreToken, (json, err) ->
                onMain(() -> {
                    if (!isActive(current)) return;
                    WaterConsumption consumption =
                            WaterConsumptionParser.INSTANCE.latestSince(
                                    json,
                                    current.monitoringStartedAt,
                                    current.did,
                                    current.scoreBaseline
                            );
                    if (consumption == null) {
                        scheduleNextPoll(current);
                        return;
                    }
                    if (!current.accountKey.isEmpty()) {
                        UsageHistoryStore.INSTANCE.mergeAndRead(
                                this,
                                current.accountKey,
                                json
                        );
                    }
                    finishConsumption(current, consumption);
                })
        );
    }

    private void scheduleNextPoll(Session current) {
        if (!isActive(current)) return;
        mainHandler.postDelayed(
                () -> pollConsumption(current),
                POLL_INTERVAL_MILLIS
        );
    }

    private void finishConsumption(Session current, WaterConsumption consumption) {
        finishWithResult(
                current,
                "接水完成",
                "本次消费 " + consumption.getMoneyText()
                        + " · 水量约 " + consumption.getEstimatedWaterText() + "（估算）",
                false
        );
    }

    private void startAsForeground(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    AppNotifications.WATER_SESSION_ID,
                    AppNotifications.waterProgress(this, text),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        } else {
            startForeground(
                    AppNotifications.WATER_SESSION_ID,
                    AppNotifications.waterProgress(this, text)
            );
        }
    }

    private void notifyWaterProgress(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        try {
            manager.notify(
                    AppNotifications.WATER_SESSION_ID,
                    AppNotifications.waterProgress(this, text)
            );
        } catch (SecurityException ignored) {
            // 用户关闭通知时，前台服务仍可继续；最终结果会回退为 Toast。
        }
    }

    private void finishWithResult(
            Session current,
            String title,
            String text,
            boolean recovery
    ) {
        if (!isActive(current)) return;
        session = null;
        mainHandler.removeCallbacksAndMessages(null);
        releaseReservation(current.reservationId);
        stopForeground(STOP_FOREGROUND_REMOVE);
        showResult(title, text, recovery);
        stopSelf(current.startId);
    }

    private void finishImmediate(
            String title,
            String text,
            boolean recovery,
            int startId
    ) {
        session = null;
        mainHandler.removeCallbacksAndMessages(null);
        releaseReservation(ownedReservationId);
        stopForeground(STOP_FOREGROUND_REMOVE);
        showResult(title, text, recovery);
        stopSelf(startId);
    }

    private void showResult(String title, String text, boolean recovery) {
        boolean posted = false;
        if (AppNotifications.canPost(this, AppNotifications.CHANNEL_WATER_RESULT)) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                try {
                    manager.notify(
                            AppNotifications.WATER_RESULT_ID,
                            AppNotifications.waterResult(this, title, text, recovery)
                    );
                    posted = true;
                } catch (SecurityException ignored) {
                    // Android 13+ 用户拒绝通知权限时使用 Toast 回退。
                }
            }
        }
        if (!posted) Toast.makeText(this, title + "：" + text, Toast.LENGTH_LONG).show();
    }

    private void updateWidget(int widgetId, String status) {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return;
        RemoteViews views = WaterWidgetProvider.buildViews(this, widgetId, status);
        AppWidgetManager.getInstance(this).updateAppWidget(widgetId, views);
    }

    private boolean isActive(Session expected) {
        return session == expected &&
                ACTIVE_RESERVATION_ID.get() == expected.reservationId;
    }

    private static void releaseReservation(long reservationId) {
        if (reservationId > 0L) {
            ACTIVE_RESERVATION_ID.compareAndSet(reservationId, 0L);
        }
    }

    private void onMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mainHandler.post(action);
        }
    }

    private static boolean isSuccessStatus(String status) {
        return status != null && status.contains("成功") && !status.contains("失败");
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        Session current = session;
        if (current != null) {
            finishWithResult(
                    current,
                    "接水会话已结束",
                    "系统已结束后台监测，可稍后在消费统计中查看",
                    false
            );
        } else {
            releaseReservation(ownedReservationId);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf(startId);
        }
    }

    @Override
    public void onDestroy() {
        session = null;
        mainHandler.removeCallbacksAndMessages(null);
        releaseReservation(ownedReservationId);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final class Session {
        final int startId;
        final int widgetId;
        final long reservationId;
        final String did;
        final String billToken;
        final String scoreToken;
        final String accountKey;
        Set<String> billBaseline = Collections.emptySet();
        Set<String> scoreBaseline = Collections.emptySet();
        int pendingBaselines;
        boolean baselineFrozen;
        long monitoringStartedAt;

        Session(
                int startId,
                int widgetId,
                long reservationId,
                String did,
                String billToken,
                String scoreToken,
                String accountKey
        ) {
            this.startId = startId;
            this.widgetId = widgetId;
            this.reservationId = reservationId;
            this.did = did;
            this.billToken = billToken == null ? "" : billToken;
            this.scoreToken = scoreToken == null ? "" : scoreToken;
            this.accountKey = accountKey;
        }
    }
}
