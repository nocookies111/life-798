package com.water.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * 桌面小部件 Provider。
 * 不使用 android:configure（HyperOS 不兼容），直接添加。
 * 未配置时整个小部件点击打开配置页；已配置时通过单一按钮启动当前设备。
 */
public class WaterWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_START = "com.water.widget.ACTION_START";
    private static final String LEGACY_ACTION_HOT = "com.water.widget.ACTION_HOT";
    private static final String LEGACY_ACTION_COLD = "com.water.widget.ACTION_COLD";
    public static final String EXTRA_DID = "extra_did";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context, id, null));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_START.equals(action)
                || LEGACY_ACTION_HOT.equals(action)
                || LEGACY_ACTION_COLD.equals(action)) {
            String did = intent.getStringExtra(EXTRA_DID);
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);

            WaterService.StartResult result = WaterService.start(context, did, widgetId);
            String status;
            if (result == WaterService.StartResult.STARTED) {
                status = "设备启动中…";
            } else if (result == WaterService.StartResult.ALREADY_RUNNING) {
                status = "已有接水会话正在监测";
            } else {
                status = "启动失败，请稍后重试";
            }
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                AppWidgetManager.getInstance(context).updateAppWidget(
                        widgetId,
                        buildViews(context, widgetId, status)
                );
            }
        }
    }

    static RemoteViews buildViews(Context context, int widgetId, String status) {
        boolean configured = WaterApi.isConfigured(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_water);

        if (!configured) {
            // 未配置：整个小部件点击打开主页
            Intent cfg = new Intent(context, ConfigActivity.class);
            PendingIntent pi = PendingIntent.getActivity(context, 100 + widgetId, cfg,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(android.R.id.background, pi);
            views.setTextViewText(R.id.widget_status, "未配置 · 点击设置");
        } else {
            String did = WaterApi.getDid(context);
            views.setOnClickPendingIntent(R.id.btn_start,
                    buildPI(context, widgetId, did));
            views.setTextViewText(R.id.widget_status,
                    status != null ? status : "点击启动当前设备");
        }
        return views;
    }

    private static PendingIntent buildPI(Context context, int widgetId, String did) {
        Intent intent = new Intent(context, WaterWidgetProvider.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_DID, did);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        return PendingIntent.getBroadcast(context, widgetId * 10 + 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
