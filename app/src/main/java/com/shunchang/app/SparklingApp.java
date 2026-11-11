package com.shunchang.app;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.window.OnBackInvokedDispatcher;

import androidx.appcompat.view.WindowCallbackWrapper;

import com.lynx.react.bridge.JavaOnlyArray;
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

    private static LynxView findLynxView(View view) {
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
