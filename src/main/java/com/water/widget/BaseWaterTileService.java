package com.water.widget;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/**
 * QS Tile 基类：点击执行一次性出水动作。
 * 子类只需提供 didKey 和 label。
 */
public abstract class BaseWaterTileService extends TileService {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    abstract protected String getDidKey();

    abstract protected String getTileLabel();

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile(Tile.STATE_INACTIVE, getTileLabel());
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        updateTile(Tile.STATE_INACTIVE, getTileLabel());
    }

    @Override
    public void onClick() {
        super.onClick();
        String did = WaterApi.getDid(this, getDidKey());

        if (!WaterApi.isConfigured(this) || did.isEmpty()) {
            showToast("请先在 App 中配置");
            Intent cfg = new Intent(this, ConfigActivity.class);
            cfg.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(cfg);
            return;
        }

        // 点击反馈：临时高亮 + "出水中…"
        updateTile(Tile.STATE_ACTIVE, "出水中…");

        WaterApi.start(this, did, getTileLabel(), status ->
                mainHandler.post(() -> {
                    updateTile(Tile.STATE_INACTIVE, getTileLabel());
                    showToast(status);
                    if (isFailureStatus(status)) openConfigForRecovery();
                }));
    }

    private boolean isFailureStatus(String status) {
        return status == null || status.contains("失败") || status.contains("未登录");
    }

    /**
     * 磁贴服务在后台被系统短暂绑定，失败通常意味着凭据需要刷新或应用进程已不在前台。
     * 打开 singleTop 主页既能唤醒进程，也让用户立即看到并修复失败原因。
     */
    private void openConfigForRecovery() {
        Intent cfg = new Intent(this, ConfigActivity.class);
        cfg.putExtra(ConfigActivity.EXTRA_OPEN_WATER_RECOVERY, true);
        cfg.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
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
