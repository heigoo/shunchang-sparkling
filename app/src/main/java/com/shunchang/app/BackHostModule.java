package com.shunchang.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.BatteryManager;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.lynx.jsbridge.LynxMethod;
import com.lynx.jsbridge.LynxModule;
import com.lynx.react.bridge.JavaOnlyArray;
import com.lynx.react.bridge.JavaOnlyMap;
import com.lynx.tasm.LynxView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;

/**
 * 宿主桥：返回键、系统栏、文件导入导出、系统打印与设备能力。
 */
public class BackHostModule extends LynxModule {
    private static final String FILE_AUTHORITY_SUFFIX = ".fileprovider";
    private static final String EXPORT_DIR = "exports";

    private static WeakReference<Activity> currentActivity = new WeakReference<>(null);
    /** 打印适配器在系统打印任务期间仍会访问 WebView，保留最近一次的强引用。 */
    private static WebView pendingPrintView = null;

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

    /** 用系统浏览器打开外部链接（使用说明书、官网等）。 */
    @LynxMethod
    public void openUrl(String url) {
        Activity activity = currentActivity.get();
        if (activity == null || url == null || url.length() == 0) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception ignored) {
            // 设备没有可处理该链接的浏览器时静默失败，前端会退回复制链接。
        }
    }

    // ---------- 文件 ----------

    /** 调起系统文件选择器；结果通过 shunchang:pickedJson 事件回传。 */
    @LynxMethod
    public void pickJson() {
        Activity activity = currentActivity.get();
        if (activity == null) {
            deliverPickedJson(null);
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(activity, HostFileActivity.class);
                intent.putExtra(HostFileActivity.EXTRA_MODE, HostFileActivity.MODE_PICK);
                activity.startActivity(intent);
            } catch (Exception error) {
                deliverPickedJson(null);
            }
        });
    }

    /** 调起系统“另存为”保存 JSON；结果通过 shunchang:savedFile 事件回传。 */
    @LynxMethod
    public void saveJson(String filename, String text) {
        Activity activity = currentActivity.get();
        if (activity == null) {
            deliverSavedFile(false, false);
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(activity, HostFileActivity.class);
                intent.putExtra(HostFileActivity.EXTRA_MODE, HostFileActivity.MODE_SAVE);
                intent.putExtra(HostFileActivity.EXTRA_FILENAME, filename);
                intent.putExtra(HostFileActivity.EXTRA_TEXT, text);
                activity.startActivity(intent);
            } catch (Exception error) {
                deliverSavedFile(false, false);
            }
        });
    }

    /** 写缓存文件并用系统分享面板发送 JSON。 */
    @LynxMethod
    public void shareJson(String filename, String text) {
        shareFile(filename, text, "application/json");
    }

    /**
     * 写缓存文件并通过 FileProvider + ACTION_SEND 分享；返回是否已成功发起分享。
     */
    @LynxMethod
    public boolean shareFile(String filename, String text, String mime) {
        File file = writeCacheFile(filename, text);
        Activity activity = currentActivity.get();
        if (file == null || activity == null) {
            return false;
        }
        shareCacheFile(activity, file, mime);
        return true;
    }

    /** 系统分享纯文本（同步文案 / 跟随链接）。 */
    @LynxMethod
    public void shareText(String text) {
        Activity activity = currentActivity.get();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, text);
                activity.startActivity(Intent.createChooser(send, "顺场 · 分享"));
            } catch (Exception error) {
                Toast.makeText(mContext, "分享失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------- 打印 ----------

    /** WebView + PrintManager 调起系统打印；失败时回退为分享 HTML。 */
    @LynxMethod
    public void printHtml(String html, String jobName) {
        Activity activity = currentActivity.get();
        String name = jobName == null || jobName.trim().length() == 0
                ? "顺场 · 流程表"
                : jobName.trim();
        String content = html == null ? "" : html;
        if (activity == null) {
            shareFile(name + ".html", content, "text/html");
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                if (pendingPrintView != null) {
                    pendingPrintView.destroy();
                    pendingPrintView = null;
                }
                WebView webView = new WebView(activity);
                webView.getSettings().setJavaScriptEnabled(false);
                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        try {
                            PrintManager manager = (PrintManager) activity.getSystemService(
                                    Context.PRINT_SERVICE);
                            if (manager == null) {
                                throw new IllegalStateException("print service unavailable");
                            }
                            PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(name);
                            manager.print(name, adapter, new PrintAttributes.Builder().build());
                        } catch (Exception error) {
                            shareFile(name + ".html", content, "text/html");
                        }
                    }
                });
                pendingPrintView = webView;
                // 打印需要真实布局；用 1x1 不可见视图挂在当前窗口上，避免遮挡界面。
                activity.addContentView(
                        webView,
                        new ViewGroup.LayoutParams(1, 1));
                webView.setVisibility(View.INVISIBLE);
                webView.loadDataWithBaseURL(null, content, "text/html", "UTF-8", null);
            } catch (Exception error) {
                shareFile(name + ".html", content, "text/html");
            }
        });
    }

    // ---------- 设备能力 ----------

    /** 设置 / 清除 FLAG_KEEP_SCREEN_ON；离开执行台时由前端关闭。 */
    @LynxMethod
    public void keepScreenOn(boolean keepOn) {
        Activity activity = currentActivity.get();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            if (keepOn) {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    /** 返回 0–100 的电量；读取失败返回 -1。 */
    @LynxMethod
    public int getBatteryLevel() {
        try {
            BatteryManager manager =
                    (BatteryManager) mContext.getSystemService(Context.BATTERY_SERVICE);
            if (manager == null) {
                return -1;
            }
            int capacity = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (capacity < 0) {
                return -1;
            }
            return Math.max(0, Math.min(100, capacity));
        } catch (Exception error) {
            return -1;
        }
    }

    // ---------- 共享实现 ----------

    static String readTextFromUri(Context context, Uri uri) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return null;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString("UTF-8");
        } catch (Exception error) {
            return null;
        }
    }

    static boolean writeTextToUri(Context context, Uri uri, String text) {
        if (text == null) {
            return false;
        }
        try (OutputStream output =
                     context.getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) {
                return false;
            }
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.flush();
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    static void deliverPickedJson(String text) {
        JavaOnlyMap payload = new JavaOnlyMap();
        if (text == null) {
            payload.putBoolean("canceled", true);
        } else {
            payload.putString("text", text);
        }
        sendGlobalEvent("shunchang:pickedJson", payload);
    }

    static void deliverSavedFile(boolean ok, boolean canceled) {
        JavaOnlyMap payload = new JavaOnlyMap();
        payload.putBoolean("ok", ok);
        payload.putBoolean("canceled", canceled);
        sendGlobalEvent("shunchang:savedFile", payload);
    }

    private static void sendGlobalEvent(String eventName, JavaOnlyMap payload) {
        Activity activity = currentActivity.get();
        if (activity == null) {
            return;
        }
        JavaOnlyArray params = new JavaOnlyArray();
        params.pushMap(payload);
        activity.runOnUiThread(() -> {
            LynxView lynxView = SparklingApp.findLynxView(
                    activity.getWindow().getDecorView());
            if (lynxView != null) {
                lynxView.sendGlobalEvent(eventName, params);
            }
        });
    }

    private File writeCacheFile(String filename, String text) {
        try {
            File dir = new File(mContext.getCacheDir(), EXPORT_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            File file = new File(dir, sanitizeFileName(filename));
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(text == null ? "" : text);
            }
            return file;
        } catch (Exception error) {
            return null;
        }
    }

    private void shareCacheFile(Activity activity, File file, String mime) {
        activity.runOnUiThread(() -> {
            try {
                Uri uri = FileProvider.getUriForFile(
                        activity,
                        activity.getPackageName() + FILE_AUTHORITY_SUFFIX,
                        file);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(mime == null || mime.length() == 0
                        ? "application/octet-stream"
                        : mime);
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(Intent.createChooser(send, "顺场 · 分享"));
            } catch (Exception error) {
                Toast.makeText(mContext, "分享失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String sanitizeFileName(String filename) {
        String value = filename == null ? "" : filename.trim();
        if (value.length() == 0) {
            value = "shunchang.json";
        }
        return value
                .replace('\\', '_')
                .replace('/', '_')
                .replace(':', '_')
                .replace('*', '_')
                .replace('?', '_')
                .replace('"', '_')
                .replace('<', '_')
                .replace('>', '_')
                .replace('|', '_');
    }
}
