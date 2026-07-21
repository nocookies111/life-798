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

    /** 出水。which = "hot" 或 "cold"。 */
    public static void start(Context ctx, final String did, final String name, final Callback cb) {
        IlifeApi.devStart(ctx, did, new IlifeApi.TextCallback() {
            @Override
            public void onResult(String text, String err) {
                if (text != null) {
                    cb.onResult(name + " " + text);
                } else if ("TOKEN_EXPIRED".equals(err)) {
                    cb.onResult(name + " 失败：登录已过期，请重新登录");
                } else {
                    cb.onResult(name + " 失败：" + err);
                }
            }
        });
    }

    static String getToken(Context ctx) {
        Account a = AccountStore.getCurrent(ctx);
        return a != null ? a.token : "";
    }

    static String getDid(Context ctx, String which) {
        Account a = AccountStore.getCurrent(ctx);
        if (a == null) return "";
        if ("hot".equals(which)) return a.hotOrFallback();
        return a.coldOrFallback();
    }

    public static boolean isConfigured(Context ctx) {
        Account a = AccountStore.getCurrent(ctx);
        return a != null && a.hasAppToken() && a.hasDevices();
    }
}
