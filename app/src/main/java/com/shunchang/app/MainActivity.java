package com.shunchang.app;

import android.app.Activity;
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
}
