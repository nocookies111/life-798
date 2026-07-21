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
 * 未配置时整个小部件点击打开配置页；已配置时热水/冷水按钮各出水。
 */
public class WaterWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_HOT = "com.water.widget.ACTION_HOT";
    public static final String ACTION_COLD = "com.water.widget.ACTION_COLD";
    public static final String EXTRA_DID = "extra_did";
    public static final String EXTRA_NAME = "extra_name";

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
        if (ACTION_HOT.equals(action) || ACTION_COLD.equals(action)) {
            String did = intent.getStringExtra(EXTRA_DID);
            String name = intent.getStringExtra(EXTRA_NAME);
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);

            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                AppWidgetManager.getInstance(context).updateAppWidget(widgetId,
                        buildViews(context, widgetId, name + " 请求中…"));
            }

            Intent svc = new Intent(context, WaterService.class);
            svc.putExtra(EXTRA_DID, did);
            svc.putExtra(EXTRA_NAME, name);
            svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            context.startService(svc);
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
            String hotDid = WaterApi.getDid(context, "hot");
            String coldDid = WaterApi.getDid(context, "cold");
            views.setOnClickPendingIntent(R.id.btn_hot,
                    buildPI(context, widgetId, 1, ACTION_HOT, hotDid, "热水"));
            views.setOnClickPendingIntent(R.id.btn_cold,
                    buildPI(context, widgetId, 2, ACTION_COLD, coldDid, "冷水"));
            views.setTextViewText(R.id.widget_status,
                    status != null ? status : "点击按钮出水");
        }
        return views;
    }

    private static PendingIntent buildPI(Context context, int widgetId, int reqCode,
                                         String action, String did, String name) {
        Intent intent = new Intent(context, WaterWidgetProvider.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_DID, did);
        intent.putExtra(EXTRA_NAME, name);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        return PendingIntent.getBroadcast(context, widgetId * 10 + reqCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
