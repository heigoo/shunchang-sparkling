package com.shunchang.app;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.window.OnBackInvokedDispatcher;

import androidx.appcompat.view.WindowCallbackWrapper;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.lynx.react.bridge.JavaOnlyArray;
import com.lynx.react.bridge.JavaOnlyMap;
import com.lynx.tasm.LynxView;
import com.tiktok.sparkling.SparklingActivity;
import com.tiktok.sparkling.hybridkit.HybridKit;
import com.tiktok.sparkling.hybridkit.config.BaseInfoConfig;
import com.tiktok.sparkling.hybridkit.config.SparklingHybridConfig;
import com.tiktok.sparkling.hybridkit.config.SparklingLynxConfig;
import com.tiktok.sparkling.hybridkit.lynx.SparklingLynxModuleWrapper;
import com.tiktok.sparkling.method.registry.core.SparklingBridgeManager;
import com.tiktok.sparkling.method.runtime.depend.BridgeBaseRuntime;
import com.tiktok.sparkling.method.storage.getItem.StorageGetItemMethod;
import com.tiktok.sparkling.method.storage.removeItem.StorageRemoveItemMethod;
import com.tiktok.sparkling.method.storage.setItem.StorageSetItemMethod;

import java.util.Collections;
import java.util.WeakHashMap;

import kotlin.Unit;

public class SparklingApp extends Application {
    private final WeakHashMap<Activity, Boolean> predictiveBackRegistered = new WeakHashMap<>();
    /** 记住当前系统栏图标明暗，避免 Activity 恢复时闪一下。 */
    private static Boolean lastLightIcons = null;
    @Override
    public void onCreate() {
        super.onCreate();

        HybridKit.INSTANCE.init(this);

        BaseInfoConfig baseInfoConfig = new BaseInfoConfig(BuildConfig.DEBUG);

        SparklingLynxConfig lynxConfig = SparklingLynxConfig.Companion.build(this, builder -> {
            builder.setTemplateProvider(new AssetTemplateProvider(this));
            builder.addBehaviors(AppBehaviors.create());
            builder.addLynxModules(Collections.singletonMap(
                    "ShunchangHost",
                    new SparklingLynxModuleWrapper(BackHostModule.class, null)));
            return Unit.INSTANCE;
        });

        SparklingHybridConfig hybridConfig =
                SparklingHybridConfig.Companion.build(baseInfoConfig, builder -> {
                    builder.setLynxConfig(lynxConfig);
                    return Unit.INSTANCE;
                });

        HybridKit.INSTANCE.setHybridConfig(hybridConfig, this);
        HybridKit.INSTANCE.initLynxKit();

        // 官方 sparkling-storage 通过 Sparkling Method 的 pipe 调用原生实现。
        BridgeBaseRuntime.INSTANCE.setApplicationContext(this);
        SparklingBridgeManager.registerIDLMethod(StorageGetItemMethod.class);
        SparklingBridgeManager.registerIDLMethod(StorageSetItemMethod.class);
        SparklingBridgeManager.registerIDLMethod(StorageRemoveItemMethod.class);

        registerBackHandling();
    }

    /**
     * SparklingActivity 重写了 onBackPressed，且不会走 OnBackPressedDispatcher。
     * 这里包裹 Window.Callback，在按键进入 Activity 前拦截返回键，
     * 转发为 shunchang:back 全局事件，由 JS 决定回退页面或退出应用。
     */
    private void registerBackHandling() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                if (activity instanceof SparklingActivity) {
                    BackHostModule.setCurrentActivity(activity);
                }
            }

            @Override
            public void onActivityResumed(Activity activity) {
                if (!(activity instanceof SparklingActivity)) {
                    return;
                }
                SparklingActivity sparkling = (SparklingActivity) activity;
                BackHostModule.setCurrentActivity(sparkling);
                applyEdgeToEdge(sparkling);
                Window window = sparkling.getWindow();
                Window.Callback callback = window.getCallback();
                if (callback != null && !(callback instanceof BackWindowCallback)) {
                    window.setCallback(new BackWindowCallback(callback, sparkling));
                }
                registerPredictiveBack(sparkling);
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    private void registerPredictiveBack(SparklingActivity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (predictiveBackRegistered.containsKey(activity)) {
            return;
        }
        predictiveBackRegistered.put(activity, Boolean.TRUE);
        activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                () -> {
                    LynxView lynxView = findLynxView(activity.getWindow().getDecorView());
                    if (lynxView != null) {
                        lynxView.sendGlobalEvent("shunchang:back", new JavaOnlyArray());
                    } else {
                        activity.finish();
                    }
                });
    }

    /** 内容铺到状态栏与导航栏下方，系统栏透明，图标颜色由页面主题决定。 */
    private void applyEdgeToEdge(Activity activity) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        setBarsAppearance(activity, lastLightIcons == null ? true : lastLightIcons);
        window.getDecorView().postDelayed(() -> sendInsetsToLynx(activity), 300);
    }

    /** lightIcons=true 用于暗色页面（浅色图标），false 用于浅色页面。 */
    static void setBarsAppearance(Activity activity, boolean lightIcons) {
        lastLightIcons = lightIcons;
        Window window = activity.getWindow();
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(!lightIcons);
        controller.setAppearanceLightNavigationBars(!lightIcons);
    }

    /** 把状态栏、导航栏高度（dp）发给 Lynx，页面据此留出安全区。 */
    static void sendInsetsToLynx(Activity activity) {
        LynxView lynxView = findLynxView(activity.getWindow().getDecorView());
        if (lynxView == null) {
            return;
        }
        WindowInsets insets =
                activity.getWindow().getDecorView().getRootWindowInsets();
        float density = activity.getResources().getDisplayMetrics().density;
        int top = 0;
        int bottom = 0;
        if (insets != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars =
                        insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
        }
        JavaOnlyMap map = new JavaOnlyMap();
        map.putDouble("top", top / density);
        map.putDouble("bottom", bottom / density);
        JavaOnlyArray params = new JavaOnlyArray();
        params.pushMap(map);
        lynxView.sendGlobalEvent("shunchang:insets", params);
    }

    private static final class BackWindowCallback extends WindowCallbackWrapper {
        private final SparklingActivity activity;

        BackWindowCallback(Window.Callback wrapped, SparklingActivity activity) {
            super(wrapped);
            this.activity = activity;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP && event.getRepeatCount() == 0) {
                    LynxView lynxView = findLynxView(activity.getWindow().getDecorView());
                    if (lynxView != null) {
                        lynxView.sendGlobalEvent("shunchang:back", new JavaOnlyArray());
                    } else {
                        activity.finish();
                    }
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
    }

    static LynxView findLynxView(View view) {
        if (view instanceof LynxView) {
            return (LynxView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index += 1) {
                LynxView found = findLynxView(group.getChildAt(index));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
