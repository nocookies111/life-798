package com.water.widget;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

/**
 * 系统栏工具类。
 *
 * 使用默认的非沉浸式系统栏，避免不同系统版本上透明背景和图标反色不一致。
 */
public final class UI {

    private static final int LIGHT_SYSTEM_BAR = Color.rgb(244, 248, 250);
    private static final int DARK_SYSTEM_BAR = Color.rgb(8, 26, 32);

    private UI() {}

    /**
     * 兼容旧调用点：不再启用沉浸式布局，统一使用默认系统栏样式。
     */
    public static void immersive(Activity activity) {
        applySystemBarAppearance(activity, isSystemDark(activity));
    }

    /** 依据当前应用外观设置状态栏和导航栏颜色、图标对比度。 */
    public static void applySystemBarAppearance(Activity activity, boolean darkTheme) {
        Window window = activity.getWindow();
        if (window == null) return;

        int barColor = darkTheme ? DARK_SYSTEM_BAR : LIGHT_SYSTEM_BAR;
        if (Build.VERSION.SDK_INT >= 21) {
            window.setStatusBarColor(barColor);
            window.setNavigationBarColor(barColor);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }

        View decor = window.getDecorView();
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (Build.VERSION.SDK_INT >= 23 && !darkTheme) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= 26 && !darkTheme) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    private static boolean isSystemDark(Activity activity) {
        return (activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** 获取状态栏高度（px）。 */
    public static int getStatusBarHeight(Context ctx) {
        int resId = ctx.getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        if (resId > 0) {
            return ctx.getResources().getDimensionPixelSize(resId);
        }
        float density = ctx.getResources().getDisplayMetrics().density;
        return (int) (24 * density + 0.5f);
    }
}
