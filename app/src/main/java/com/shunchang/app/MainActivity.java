package com.shunchang.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.tiktok.sparkling.Sparkling;
import com.tiktok.sparkling.SparklingContext;

public class MainActivity extends Activity {
    private static final String PAGE_SCHEME =
            "hybrid://lynxview_page?bundle=main.lynx.bundle&hide_nav_bar=1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SparklingContext context = new SparklingContext();
        context.setScheme(PAGE_SCHEME);
        Sparkling.build(this, context).navigate();

        finish();
    }

    /** 文件选择器结果统一交给代理页的公共处理，保留入口以防启动页承接请求。 */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        HostFileActivity.handleResult(this, requestCode, resultCode, data);
    }
}
