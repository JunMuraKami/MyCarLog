package jp.mycarlog.app;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Base activity: applies the selected colour theme, draws the flat header and shared shapes. */
abstract class ThemedActivity extends Activity {
    protected AppTheme.Pal pal;

    @Override protected void onCreate(Bundle b) {
        pal = AppTheme.current(this);
        setTheme(pal.dark ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(b);
        getWindow().setStatusBarColor(pal.bg);
        getWindow().setNavigationBarColor(pal.bg);
        if (!pal.dark)
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!AppTheme.current(this).id.equals(pal.id)) recreate();
    }

    protected int px(int dp) { return (int) (dp * getResources().getDisplayMetrics().density); }

    protected GradientDrawable shape(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radiusDp));
        return g;
    }

    /** Rounded card background (no shadow). */
    protected void cardStyle(View v, int radiusDp) {
        v.setBackground(shape(pal.card, radiusDp));
        v.setElevation(0);
    }

    /** Pill-shaped button: filled with the accent colour, or tonal (tinted background). */
    protected void pill(Button b, boolean filled) {
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(filled ? pal.onAccent() : pal.accent);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080),
                shape(filled ? pal.accent : pal.tonal(), 100), null));
        b.setStateListAnimator(null);
        b.setElevation(0);
    }

    /** Whole screen: flat header (optional back arrow, title, optional action icon) above the body. */
    protected LinearLayout screen(boolean back, int iconRes, String iconDesc, Runnable action, View body) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(pal.bg);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(px(back ? 4 : 16), px(4), px(8), px(4));
        if (back) {
            ImageView up = icon(R.drawable.ic_back, "戻る");
            up.setOnClickListener(v -> finish());
            bar.addView(up, new LinearLayout.LayoutParams(px(48), px(48)));
        }
        TextView title = new TextView(this);
        title.setText(getTitle());
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(pal.text);
        title.setSingleLine(true);
        title.setPadding(px(back ? 8 : 0), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        if (iconRes != 0) {
            ImageView act = icon(iconRes, iconDesc);
            act.setOnClickListener(v -> action.run());
            bar.addView(act, new LinearLayout.LayoutParams(px(48), px(48)));
        }
        root.addView(bar, new LinearLayout.LayoutParams(-1, px(60)));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private ImageView icon(int res, String desc) {
        ImageView i = new ImageView(this);
        i.setImageResource(res);
        i.setContentDescription(desc);
        i.setColorFilter(pal.text);
        i.setPadding(px(12), px(12), px(12), px(12));
        i.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080), null,
                shape(Color.WHITE, 100)));
        return i;
    }
}
