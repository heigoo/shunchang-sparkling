package com.shunchang.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

/**
 * 桌面图标主题：通过启用不同的 activity-alias 切换 launcher 图标。
 * 禁用当前入口别名会让系统结束当前任务，所以切换时先启用新入口并从它重建任务，
 * 稍后再禁用旧别名，避免用户被直接丢回桌面。
 */
final class LauncherTheme {
    private static final String PREFS = "shunchang_prefs";
    private static final String KEY = "launcher_theme";
    private static final String DEFAULT_ALIAS = ".LauncherDefault";
    private static final String XISHI_ALIAS = ".LauncherXishi";
    private static final long RELAUNCH_DELAY_MS = 260;
    private static final long DISABLE_DELAY_MS = 900;

    private LauncherTheme() {
    }

    static void apply(Context context, String theme) {
        Context app = context.getApplicationContext();
        boolean xishi = "xishi".equals(theme);
        String target = xishi ? "xishi" : "default";

        PackageManager manager = app.getPackageManager();
        ComponentName on = alias(app, xishi ? XISHI_ALIAS : DEFAULT_ALIAS);
        ComponentName off = alias(app, xishi ? DEFAULT_ALIAS : XISHI_ALIAS);

        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean stateOk = isEnabled(manager, on, !xishi) && !isEnabled(manager, off, xishi);
        if (target.equals(prefs.getString(KEY, "default")) && stateOk) {
            return;
        }

        manager.setComponentEnabledSetting(
                on, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        prefs.edit().putString(KEY, target).apply();

        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            // MULTIPLE_TASK 让新入口落在独立任务里，任务的基础 Intent 指向新别名，
            // 之后禁用旧别名只会清理旧任务，不会把刚打开的界面一起结束。
            Intent intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(on)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            app.startActivity(intent);
            handler.postDelayed(
                    () -> manager.setComponentEnabledSetting(
                            off,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP),
                    DISABLE_DELAY_MS);
        }, RELAUNCH_DELAY_MS);
    }

    /** 未显式设置过状态时按 manifest 的 android:enabled 判断。 */
    private static boolean isEnabled(
            PackageManager manager, ComponentName name, boolean manifestDefault) {
        int state = manager.getComponentEnabledSetting(name);
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            return manifestDefault;
        }
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    private static ComponentName alias(Context context, String name) {
        return new ComponentName(context.getPackageName(), context.getPackageName() + name);
    }
}
