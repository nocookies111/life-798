package com.water.widget;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.IBinder;
import android.widget.RemoteViews;

/**
 * 后台执行出水请求，完成后更新小部件。
 */
public class WaterService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String did = intent.getStringExtra(WaterWidgetProvider.EXTRA_DID);
        final String name = intent.getStringExtra(WaterWidgetProvider.EXTRA_NAME);
        final int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);

        WaterApi.start(this, did, name, status -> {
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                RemoteViews views = WaterWidgetProvider.buildViews(this, widgetId, status);
                AppWidgetManager.getInstance(this).updateAppWidget(widgetId, views);
            }
            stopSelf();
        });
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
