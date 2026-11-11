package com.shunchang.app;

import android.content.Context;

import com.lynx.tasm.provider.AbsTemplateProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** 从 assets 读取 .lynx.bundle，供 Sparkling 容器加载本地页面。 */
public class AssetTemplateProvider extends AbsTemplateProvider {
    private final Context context;

    public AssetTemplateProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void loadTemplate(String url, Callback callback) {
        String name = url;
        int query = name.indexOf('?');
        if (query >= 0) {
            name = name.substring(0, query);
        }
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }

        try (InputStream input = context.getAssets().open(name)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] chunk = new byte[16384];
            int read;
            while ((read = input.read(chunk)) != -1) {
                output.write(chunk, 0, read);
            }
            callback.onSuccess(output.toByteArray());
        } catch (IOException error) {
            callback.onFailed("无法读取 assets/" + name + "：" + error.getMessage());
        }
    }
}
