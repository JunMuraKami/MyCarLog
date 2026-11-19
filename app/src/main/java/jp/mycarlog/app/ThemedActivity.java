package jp.mycarlog.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

/** Base activity: applies the selected colour theme and re-creates itself when it changes. */
abstract class ThemedActivity extends Activity {
    protected AppTheme.Pal pal;

    @Override protected void onCreate(Bundle b) {
        pal = AppTheme.current(this);
        setTheme(pal.dark ? android.R.style.Theme_Material
                : android.R.style.Theme_Material_Light_DarkActionBar);
        super.onCreate(b);
        if (getActionBar() != null) getActionBar().setBackgroundDrawable(new ColorDrawable(pal.bar));
        getWindow().setStatusBarColor(Color.rgb(
                (int) (Color.red(pal.bar) * 0.75), (int) (Color.green(pal.bar) * 0.75),
                (int) (Color.blue(pal.bar) * 0.75)));
    }

    @Override protected void onResume() {
        super.onResume();
        if (!AppTheme.current(this).id.equals(pal.id)) recreate();
    }
}
