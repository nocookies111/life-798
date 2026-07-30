package com.water.widget;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/** 快捷设置磁贴：启动当前选择的饮水设备。 */
public class WaterTileService extends TileService {
    private static final String LABEL = "启动设备";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile(Tile.STATE_INACTIVE, LABEL);
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        updateTile(Tile.STATE_INACTIVE, LABEL);
    }

    @Override
    public void onClick() {
        super.onClick();
        String did = WaterApi.getDid(this);
        if (!WaterApi.isConfigured(this) || did.isEmpty()) {
            showToast("请先在 App 中登录并选择设备");
            openConfig(false);
            return;
        }

        WaterService.StartResult result = WaterService.start(
                this,
                did,
                AppWidgetManager.INVALID_APPWIDGET_ID
        );
        if (result == WaterService.StartResult.ALREADY_RUNNING) {
            showToast("已有接水会话正在监测");
            return;
        }
        if (result == WaterService.StartResult.FAILED) {
            showToast("设备启动失败，请稍后重试");
            return;
        }

        updateTile(Tile.STATE_ACTIVE, "启动中…");
        showToast("设备启动中…");
        mainHandler.postDelayed(() -> {
            updateTile(Tile.STATE_INACTIVE, LABEL);
        }, 1200L);
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @SuppressWarnings("deprecation")
    private void openConfig(boolean recovery) {
        Intent cfg = new Intent(this, ConfigActivity.class);
        if (recovery) cfg.putExtra(ConfigActivity.EXTRA_OPEN_WATER_RECOVERY, true);
        cfg.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    cfg,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(cfg);
        }
    }

    private void updateTile(int state, String label) {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(state);
        tile.setLabel(label);
        tile.updateTile();
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
