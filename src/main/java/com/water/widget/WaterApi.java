package com.water.widget;

import android.content.Context;

/**
 * 出水逻辑（向后兼容入口）。
 * 实际请求委托给 IlifeApi，token/did 从 AccountStore 当前账户读取。
 */
public class WaterApi {
    public static final String PREFS = "water_cfg";

    public interface Callback {
        void onResult(String status);
    }

    /** 启动指定饮水设备。水温由设备上的实体按钮决定。 */
    public static void start(Context ctx, final String did, final Callback cb) {
        IlifeApi.devStart(ctx, did, new IlifeApi.TextCallback() {
            @Override
            public void onResult(String text, String err) {
                if (text != null) {
                    cb.onResult("设备 " + text);
                } else if ("TOKEN_EXPIRED".equals(err)) {
                    cb.onResult("启动失败：登录已过期，请重新登录");
                } else {
                    cb.onResult("启动失败：" + err);
                }
            }
        });
    }

    static String getToken(Context ctx) {
        Account a = AccountStore.getCurrent(ctx);
        return a != null ? a.token : "";
    }

    static String getDid(Context ctx) {
        Account a = AccountStore.getCurrent(ctx);
        return a != null ? a.selectedDeviceId() : "";
    }

    public static boolean isConfigured(Context ctx) {
        Account a = AccountStore.getCurrent(ctx);
        return a != null && a.hasAppToken() && a.hasDevices();
    }
}
