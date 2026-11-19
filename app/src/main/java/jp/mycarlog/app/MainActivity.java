package jp.mycarlog.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Home: reminders for all vehicles, vehicle picker, and the selected vehicle's summary. */
public class MainActivity extends Activity {
    private static final int MENU_SETTINGS = 1;
    static final int RED = 0xFFD32F2F, ORANGE = 0xFFEF6C00, GRAY = 0xFF616161, TEXT = 0xFF212121;

    private Store store;
    private LinearLayout content;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("MyCarLog");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF2F4F7);

        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(12));
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(8), dp(4), dp(8), dp(8));
        bar.addView(navButton("給油", LogActivity.MODE_FUEL), new LinearLayout.LayoutParams(0, dp(64), 1f));
        bar.addView(navButton("メンテナンス", LogActivity.MODE_SERVICE), new LinearLayout.LayoutParams(0, dp(64), 1f));
        root.addView(bar);

        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        Reminder.schedule(this);
    }

    @Override protected void onResume() {
        super.onResume();
        render(); // data may have changed in a log page
        if (Updater.due(this))
            Updater.check(this, (latest, ok) -> {
                if (latest != null && !isFinishing()) render();
            });
    }

    private Button navButton(String label, int mode) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(18);
        btn.setOnClickListener(v -> openLog(mode));
        return btn;
    }

    private void openLog(int mode) {
        startActivity(new Intent(this, LogActivity.class).putExtra(LogActivity.EXTRA_MODE, mode));
    }

    // ---- menu: vehicle management ----

    @Override public boolean onCreateOptionsMenu(Menu m) {
        m.add(0, MENU_SETTINGS, 0, "設定");
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SETTINGS:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---- rendering ----

    private void render() {
        store = new Store(this);
        invalidateOptionsMenu();
        content.removeAllViews();

        // ---- app update available (from GitHub Releases) ----
        final Updater.Info upd = Updater.cached(this);
        if (upd != null) {
            LinearLayout banner = new LinearLayout(this);
            banner.setOrientation(LinearLayout.VERTICAL);
            banner.setPadding(dp(14), dp(10), dp(14), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFFE3F2FD);
            bg.setStroke(dp(1), 0xFF1976D2);
            bg.setCornerRadius(dp(10));
            banner.setBackground(bg);
            TextView t = text("新しいバージョン " + upd.version + " があります", 15, 0xFF1976D2);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            banner.addView(t);
            banner.addView(text("タップして更新", 13, 0xFF1976D2));
            banner.setOnClickListener(v -> Updater.prompt(this, upd));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(8);
            content.addView(banner, lp);
        }

        // ---- reminders for every vehicle ----
        for (Store.Due d : store.alerts(LocalDate.now())) {
            int color = d.level == 2 ? RED : ORANGE;
            LinearLayout banner = new LinearLayout(this);
            banner.setOrientation(LinearLayout.VERTICAL);
            banner.setPadding(dp(14), dp(10), dp(14), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(d.level == 2 ? 0xFFFDECEA : 0xFFFFF3E0);
            bg.setStroke(dp(1), color);
            bg.setCornerRadius(dp(10));
            banner.setBackground(bg);
            TextView t = text("[" + d.vehicle.name + "] [" + d.s.label() + "] "
                    + (d.level == 2 ? "の期限を過ぎています" : "が近づいています"), 15, color);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            banner.addView(t);
            banner.addView(text(d.detail, 13, color));
            final String vid = d.vehicle.id;
            banner.setOnClickListener(v -> {
                store.select(vid);
                render();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(8);
            content.addView(banner, lp);
        }

        // ---- vehicle picker and its details ----
        content.addView(vehiclePicker());
        String info = store.vehicle().subtitle();
        if (!info.isEmpty()) {
            TextView t = text(info, 13, GRAY);
            t.setPadding(dp(4), 0, dp(4), dp(10));
            content.addView(t);
        }

        // ---- fuel card ----
        LinearLayout fuel = card("燃費", () -> openLog(LogActivity.MODE_FUEL));
        double avg = store.avgEconomy();
        TextView big = text(avg > 0 ? String.format(Locale.JAPAN, "%.2f km/L", avg) : "- km/L", 34, TEXT);
        big.setTypeface(Typeface.DEFAULT_BOLD);
        fuel.addView(text("平均燃費", 13, GRAY));
        fuel.addView(big);

        Fill eco = store.lastEconomyFill();
        Fill last = store.lastFill();
        row(fuel, "前回の燃費", eco == null ? "-"
                : String.format(Locale.JAPAN, "%.2f km/L(%s)", eco.economy, eco.date));
        row(fuel, "前回の給油価格", last == null ? "-"
                : String.format(Locale.JAPAN, "¥%.0f/L(%s)", last.cost / last.liters, last.date));
        if (last != null)
            row(fuel, "", String.format(Locale.JAPAN, "%.2f L・¥%,.0f", last.liters, last.cost));
        if (store.fills.isEmpty())
            fuel.addView(text("「給油」から記録を追加してください", 13, GRAY));

        // ---- maintenance card ----
        LinearLayout svc = card("メンテナンス", () -> openLog(LogActivity.MODE_SERVICE));
        svc.addView(text(String.format(Locale.JAPAN, "現在の走行距離: %,.0f km", store.currentOdo()), 13, GRAY));
        List<Store.Due> due = store.dueItems(LocalDate.now());
        if (due.isEmpty())
            svc.addView(text("「メンテナンス」から記録を追加すると次回予定が表示されます", 13, GRAY));
        for (Store.Due d : due) {
            int color = d.level == 2 ? RED : d.level == 1 ? ORANGE : TEXT;
            String tag = d.level == 2 ? "超過" : d.level == 1 ? "まもなく" : "";
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, dp(10), 0, 0);
            TextView name = text((tag.isEmpty() ? "" : "【" + tag + "】") + d.s.label(), 16, color);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            item.addView(name);
            item.addView(text(d.detail, 14, d.level > 0 ? color : GRAY));
            svc.addView(item);
        }
    }

    /** Dropdown of vehicles: those still owned first, then ones let go. Managed in settings. */
    private View vehiclePicker() {
        final List<Vehicle> order = new ArrayList<>();
        for (Vehicle v : store.vehicles) if (v.owned) order.add(v);
        for (Vehicle v : store.vehicles) if (!v.owned) order.add(v);
        List<String> names = new ArrayList<>();
        int sel = 0;
        for (int i = 0; i < order.size(); i++) {
            names.add(order.get(i).name + (order.get(i).owned ? "" : "(手放し)"));
            if (order.get(i).id.equals(store.vehicleId)) sel = i;
        }

        Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, names) {
            @Override public View getView(int pos, View v, android.view.ViewGroup p) {
                TextView t = (TextView) super.getView(pos, v, p);
                t.setTextSize(22);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setTextColor(TEXT);
                return t;
            }
        };
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setSelection(sel, false);
        final int current = sel;
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                if (i == current) return; // initial callback / no change
                store.select(order.get(i).id);
                render();
            }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(8);
        sp.setLayoutParams(lp);
        return sp;
    }

    // ---- small view helpers ----

    private LinearLayout card(String title, Runnable onTap) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(12));
        c.setBackground(bg);
        c.setElevation(dp(2));
        c.setOnClickListener(v -> onTap.run());
        TextView t = text(title, 18, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(6));
        c.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        content.addView(c, lp);
        return c;
    }

    private void row(LinearLayout parent, String label, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, dp(6), 0, 0);
        r.addView(text(label, 15, GRAY), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = text(value, 15, TEXT);
        v.setGravity(Gravity.END);
        r.addView(v);
        parent.addView(r);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
