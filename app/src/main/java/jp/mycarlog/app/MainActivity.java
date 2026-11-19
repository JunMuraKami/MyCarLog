package jp.mycarlog.app;

import android.Manifest;

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
public class MainActivity extends ThemedActivity {
    private static final int MENU_SETTINGS = 1;
    private int RED, ORANGE, GRAY, TEXT;

    private Store store;
    private LinearLayout content;
    private boolean showSold;   // expand the vehicles that were let go

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        RED = pal.red; ORANGE = pal.orange; GRAY = pal.gray; TEXT = pal.text;
        setTitle("MyCarLog");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(12));
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(12), dp(4), dp(12), dp(12));
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, dp(56), 1f);
        b1.rightMargin = dp(6);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, dp(56), 1f);
        b2.leftMargin = dp(6);
        bar.addView(navButton("給油", LogActivity.MODE_FUEL), b1);
        bar.addView(navButton("整備", LogActivity.MODE_SERVICE), b2);
        root.addView(bar);

        setContentView(screen(false, R.drawable.ic_settings, "設定",
                () -> startActivity(new Intent(this, SettingsActivity.class)), root));

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
        btn.setTextSize(17);
        pill(btn, true);
        btn.setOnClickListener(v -> openLog(mode));
        return btn;
    }

    private void openLog(int mode) {
        startActivity(new Intent(this, LogActivity.class).putExtra(LogActivity.EXTRA_MODE, mode));
    }

    // ---- menu: vehicle management ----

    @Override public boolean onCreateOptionsMenu(Menu m) {
        m.add(0, MENU_SETTINGS, 0, "設定")
                .setIcon(R.drawable.ic_settings)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
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
            bg.setColor(pal.accentBg);
            bg.setStroke(dp(1), pal.accent);
            bg.setCornerRadius(dp(16));
            banner.setBackground(bg);
            TextView t = text("新しいバージョン " + upd.version + " があります", 15, pal.accent);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            banner.addView(t);
            banner.addView(text("タップして更新", 13, pal.accent));
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
            bg.setColor(d.level == 2 ? pal.redBg : pal.orangeBg);
            bg.setStroke(dp(1), color);
            bg.setCornerRadius(dp(16));
            banner.setBackground(bg);
            TextView t = text(d.vehicle.name + "の" + d.s.label()
                    + (d.level == 2 ? "の期限が過ぎています" : "が近づいています"), 15, color);
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
        if (store.fills.isEmpty()) {
            fuel.addView(text("「給油」から記録を追加してください", 13, GRAY));
        } else {
            LocalDate now = LocalDate.now();
            FuelStats.Period m = FuelStats.find(store.fills, true, now.toString().substring(0, 7));
            FuelStats.Period y = FuelStats.find(store.fills, false, String.valueOf(now.getYear()));
            row(fuel, "今月の給油", String.format(Locale.JAPAN, "%.2f L ・ ¥%,.0f", m.liters, m.cost));
            row(fuel, "今年の給油", String.format(Locale.JAPAN, "%.2f L ・ ¥%,.0f", y.liters, y.cost));
        }

        // ---- maintenance card ----
        LinearLayout svc = card("整備", () -> openLog(LogActivity.MODE_SERVICE));
        svc.addView(text(String.format(Locale.JAPAN, "現在の走行距離: %,.0f km", store.currentOdo()), 13, GRAY));
        List<Store.Due> due = store.dueItems(LocalDate.now());
        if (due.isEmpty())
            svc.addView(text("「整備」から記録を追加すると次回予定が表示されます", 13, GRAY));
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

    /** Row of round vehicle icons (owned first, then ones let go) and the selected vehicle's name. */
    private View vehiclePicker() {
        final List<Vehicle> order = new ArrayList<>();
        int soldCount = 0;
        for (Vehicle v : store.vehicles) if (v.owned) order.add(v);
        for (Vehicle v : store.vehicles) {
            if (v.owned) continue;
            soldCount++;
            // Let-go vehicles stay hidden unless expanded or currently selected.
            if (showSold || v.id.equals(store.vehicleId)) order.add(v);
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(2), dp(4), dp(2), dp(4));
        for (final Vehicle v : order) {
            boolean sel = v.id.equals(store.vehicleId);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(dp(6), 0, dp(6), 0);
            item.addView(Avatar.view(this, v.name, v.photo, v.icon, 68, sel, pal));
            TextView n = text(v.name, 12, sel ? pal.accent : GRAY);
            n.setSingleLine(true);
            n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            n.setMaxWidth(dp(110));
            n.setGravity(Gravity.CENTER);
            n.setPadding(0, dp(4), 0, 0);
            if (sel) n.setTypeface(Typeface.DEFAULT_BOLD);
            item.addView(n);
            if (!v.owned) item.setAlpha(0.6f);
            item.setOnClickListener(x -> {
                if (v.id.equals(store.vehicleId)) return;
                store.select(v.id);
                render();
            });
            row.addView(item);
        }
        if (soldCount > 0) {
            TextView chip = text(showSold ? "過去の車両を隠す" : "過去の車両 " + soldCount, 12, pal.accent);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(12), dp(8), dp(12), dp(8));
            chip.setBackground(shape(pal.tonal(), 100));
            chip.setOnClickListener(x -> {
                showSold = !showSold;
                render();
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.gravity = Gravity.CENTER_VERTICAL;
            clp.setMargins(dp(8), 0, dp(8), dp(20));
            row.addView(chip, clp);
        }
        hs.addView(row);
        box.addView(hs);
        TextView title = text(store.vehicle().name, 24, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(4), dp(8), 0, dp(2));
        box.addView(title);
        return box;
    }

    // ---- small view helpers ----

    private LinearLayout card(String title, Runnable onTap) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        cardStyle(c, 20);
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
