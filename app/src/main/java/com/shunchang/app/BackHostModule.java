package com.shunchang.app;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import com.lynx.jsbridge.LynxMethod;
import com.lynx.jsbridge.LynxModule;

import java.lang.ref.WeakReference;

/**
 * 宿主返回键桥：JS 决定是回退页面还是退出应用。
 */
public class BackHostModule extends LynxModule {
    private static WeakReference<Activity> currentActivity = new WeakReference<>(null);

    public BackHostModule(Context context) {
        super(context);
    }

    public BackHostModule(Context context, Object param) {
        super(context, param);
    }

    static void setCurrentActivity(Activity activity) {
        currentActivity = new WeakReference<>(activity);
    }

    @LynxMethod
    public void exitApp() {
        Activity activity = currentActivity.get();
        if (activity != null) {
            activity.finish();
        }
    }

    @LynxMethod
    public void showExitHint() {
        Toast.makeText(mContext, "再按一次退出", Toast.LENGTH_SHORT).show();
    }

    /** JS 根据当前页面主题切换状态栏 / 导航栏图标明暗。 */
    @LynxMethod
    public void setBarsStyle(String style) {
        Activity activity = currentActivity.get();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(
                () -> SparklingApp.setBarsAppearance(activity, "dark".equals(style)));
    }

    /** 页面挂载后主动拉取一次系统栏高度。 */
    @LynxMethod
    public void requestInsets() {
        Activity activity = currentActivity.get();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> SparklingApp.sendInsetsToLynx(activity));
    }

    /** JS 切换喜事 / 默认主题时同步更换桌面图标。 */
    @LynxMethod
    public void applyLauncherTheme(String theme) {
        LauncherTheme.apply(mContext, theme);
    }
}
