package com.water.widget;

import android.content.Intent;
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

        updateTile(Tile.STATE_ACTIVE, "启动中…");
        WaterApi.start(this, did, status -> mainHandler.post(() -> {
            updateTile(Tile.STATE_INACTIVE, LABEL);
            showToast(status);
            if (isFailureStatus(status)) openConfig(true);
        }));
    }

    private boolean isFailureStatus(String status) {
        return status == null || status.contains("失败") || status.contains("未登录");
    }

    private void openConfig(boolean recovery) {
        Intent cfg = new Intent(this, ConfigActivity.class);
        if (recovery) cfg.putExtra(ConfigActivity.EXTRA_OPEN_WATER_RECOVERY, true);
        cfg.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(cfg);
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
