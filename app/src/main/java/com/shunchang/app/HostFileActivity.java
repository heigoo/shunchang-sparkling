package com.shunchang.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * 透明代理页：用系统文件选择器导入 JSON，或用系统“另存为”导出 JSON。
 * SparklingActivity 不转发 onActivityResult，因此由本页承接结果并通过全局事件回传。
 */
public class HostFileActivity extends Activity {
    static final String EXTRA_MODE = "shunchang.mode";
    static final String EXTRA_FILENAME = "shunchang.filename";
    static final String EXTRA_TEXT = "shunchang.text";

    static final String MODE_PICK = "pick";
    static final String MODE_SAVE = "save";

    static final int REQUEST_PICK = 4801;
    static final int REQUEST_SAVE = 4802;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String mode = intent != null ? intent.getStringExtra(EXTRA_MODE) : null;

        if (MODE_PICK.equals(mode)) {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("*/*");
            pick.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/*"});
            startActivityForResult(pick, REQUEST_PICK);
            return;
        }

        if (MODE_SAVE.equals(mode)) {
            Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            save.addCategory(Intent.CATEGORY_OPENABLE);
            save.setType("application/json");
            String filename = intent.getStringExtra(EXTRA_FILENAME);
            if (filename != null && filename.length() > 0) {
                save.putExtra(Intent.EXTRA_TITLE, filename);
            }
            startActivityForResult(save, REQUEST_SAVE);
            return;
        }

        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        handleResult(this, requestCode, resultCode, data);
        finish();
        overridePendingTransition(0, 0);
    }

    /** MainActivity 与代理页共用：读取/写入成功后通过 shunchang: 事件回传给 Lynx。 */
    static void handleResult(
            Activity activity,
            int requestCode,
            int resultCode,
            Intent data
    ) {
        Uri uri = data != null ? data.getData() : null;

        if (requestCode == REQUEST_PICK) {
            String text = resultCode == RESULT_OK && uri != null
                    ? BackHostModule.readTextFromUri(activity, uri)
                    : null;
            BackHostModule.deliverPickedJson(text);
            return;
        }

        if (requestCode == REQUEST_SAVE) {
            boolean saved = resultCode == RESULT_OK
                    && uri != null
                    && BackHostModule.writeTextToUri(
                    activity,
                    uri,
                    activity.getIntent().getStringExtra(EXTRA_TEXT)
            );
            BackHostModule.deliverSavedFile(saved, resultCode != RESULT_OK);
        }
    }
}
