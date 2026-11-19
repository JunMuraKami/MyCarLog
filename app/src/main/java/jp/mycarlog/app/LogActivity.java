package jp.mycarlog.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Log page for fuel or maintenance: list past records, edit them, add new ones. */
public class LogActivity extends Activity {
    public static final String EXTRA_MODE = "mode";
    public static final int MODE_FUEL = 0, MODE_SERVICE = 1;

    private Store store;
    private int mode;
    private int view = 0; // fuel only: 0 records, 1 monthly totals, 2 yearly totals
    private final List<Button> tabs = new ArrayList<>();
    private TextView summary;
    private BaseAdapter adapter;
    private final List<Object> shown = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_FUEL);
        store = new Store(this);
        setTitle((mode == MODE_FUEL ? "給油ログ" : "メンテナンスログ") + " - " + store.vehicle().name);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        final int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        summary = new TextView(this);
        summary.setTextSize(14);
        summary.setPadding(pad, dp(10), pad, dp(10));
        summary.setBackgroundColor(0xFFE3F2FD);
        root.addView(summary);

        if (mode == MODE_FUEL) {
            LinearLayout bar = new LinearLayout(this);
            bar.setPadding(dp(8), dp(4), dp(8), 0);
            String[] names = { "記録", "月別", "年別" };
            for (int k = 0; k < names.length; k++) {
                final int target = k;
                Button tb = new Button(this);
                tb.setText(names[k]);
                tb.setOnClickListener(v -> {
                    view = target;
                    refresh();
                });
                tabs.add(tb);
                bar.addView(tb, new LinearLayout.LayoutParams(0, dp(44), 1f));
            }
            root.addView(bar);
        }

        ListView list = new ListView(this);
        adapter = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                TextView t = new TextView(LogActivity.this);
                t.setPadding(pad, dp(10), pad, dp(10));
                t.setTextSize(15);
                Object o = shown.get(i);
                if (o instanceof FuelStats.Period) {
                    FuelStats.Period q = (FuelStats.Period) o;
                    String l2 = q.line2();
                    String head = view == 1 ? q.key.replace("-", "年") + "月" : q.key + "年";
                    t.setText(head + "\n" + q.line1() + (l2.isEmpty() ? "" : "\n" + l2));
                } else {
                    t.setText(o instanceof Fill ? fillText((Fill) o) : serviceText((Service) o));
                }
                return t;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((a, v, i, id) -> {
            Object o = shown.get(i);
            if (o instanceof Fill) editFill((Fill) o);
            else if (o instanceof Service) editService((Service) o);
        });
        TextView empty = new TextView(this);
        empty.setText("記録がありません。下のボタンから追加してください。");
        empty.setPadding(pad, pad, pad, pad);
        root.addView(empty);
        list.setEmptyView(empty);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button add = new Button(this);
        add.setText(mode == MODE_FUEL ? "＋ 給油を記録" : "＋ メンテナンスを記録");
        add.setTextSize(16);
        add.setOnClickListener(v -> {
            if (mode == MODE_FUEL) editFill(null); else editService(null);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(60));
        lp.setMargins(dp(8), dp(4), dp(8), dp(8));
        root.addView(add, lp);

        setContentView(root);
        refresh();
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refresh() {
        shown.clear();
        if (mode == MODE_FUEL) {
            for (int k = 0; k < tabs.size(); k++) tabs.get(k).setEnabled(k != view);
            if (view == 0) {
                List<Fill> f = new ArrayList<>(store.fills);
                Collections.reverse(f); // newest first
                shown.addAll(f);
            } else {
                shown.addAll(FuelStats.compute(store.fills, view == 1));
            }
            double liters = 0;
            for (Fill f : store.fills) liters += f.liters;
            summary.setText(String.format(Locale.JAPAN, "合計: %.2f L ・ ¥%,.0f(%d回)",
                    liters, store.totalCost(), store.fills.size()));
        } else {
            shown.addAll(store.services);
            summary.setText(String.format(Locale.JAPAN, "総整備費用: ¥%,.0f(%d件)",
                    store.serviceCost(), store.services.size()));
        }
        adapter.notifyDataSetChanged();
    }

    private String fillText(Fill f) {
        String eco = f.economy > 0 ? String.format(Locale.JAPAN, "%.2f km/L", f.economy)
                : (f.full ? "-" : "-- km/L (Not FULL)");
        return String.format(Locale.JAPAN,
                "%s   %,.0f km\n%.2f L  /  ¥%,.0f  /  ¥%.0f/L\n燃費: %s",
                f.date, f.odo, f.liters, f.cost,
                f.liters > 0 ? f.cost / f.liters : 0, eco);
    }

    private String serviceText(Service s) {
        StringBuilder sb = new StringBuilder(String.format(Locale.JAPAN,
                "%s   %s\n%,.0f km  /  ¥%,.0f", s.date, s.label(), s.odo, s.cost));
        if (!s.memo.trim().isEmpty() && !Service.OTHER.equals(s.type)) sb.append("\n").append(s.memo.trim());
        List<String> next = new ArrayList<>();
        if (s.intervalKm > 0) next.add(String.format(Locale.JAPAN, "%,.0f km", s.nextOdo()));
        if (s.intervalMonths > 0) next.add(s.nextDate().toString());
        if (!next.isEmpty())
            sb.append("\n次回: ").append(String.join(" / ", next))
                    .append(store.isLatest(s) ? "" : "(実施済み)");
        return sb.toString();
    }

    // ---- editor building blocks ----

    private static String num(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), 0, dp(20), 0);
        return box;
    }

    /** Date chosen with a calendar picker (no typing); returns a 1-element holder. */
    private String[] datePicker(LinearLayout box, String initial) {
        Ui.label(this, box, "日付");
        final String[] date = { initial != null ? initial : LocalDate.now().toString() };
        final Button btn = new Button(this);
        btn.setText("📅  " + date[0]);
        btn.setTextSize(16);
        btn.setOnClickListener(v -> {
            LocalDate d = LocalDate.parse(date[0]);
            new DatePickerDialog(this, (dp, y, m, dd) -> {
                date[0] = LocalDate.of(y, m + 1, dd).toString();
                btn.setText("📅  " + date[0]);
            }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
        });
        box.addView(btn);
        return date;
    }

    private void showDialog(String title, LinearLayout box, Runnable onDelete, Runnable onSave) {
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog.Builder d = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(sv)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("保存", null);
        if (onDelete != null) d.setNeutralButton("削除", (x, w) -> new AlertDialog.Builder(this)
                .setMessage("この記録を削除しますか?")
                .setNegativeButton("いいえ", null)
                .setPositiveButton("削除", (y, z) -> {
                    onDelete.run();
                    store.save();
                    refresh();
                })
                .show());
        final AlertDialog dlg = d.create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                onSave.run();
                store.save();
                refresh();
                dlg.dismiss();
            } catch (NumberFormatException ex) {
                Toast.makeText(this, "数値を正しく入力してください", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String defaultOdo() {
        double cur = store.currentOdo();
        return cur > 0 ? num(cur) : "";
    }

    // ---- editors ----

    private void editFill(final Fill edit) {
        LinearLayout box = dialogBox();
        String[] date = datePicker(box, edit != null ? edit.date : null);
        EditText odo = Ui.numField(this, box, "総走行距離 (km)", edit != null ? num(edit.odo) : defaultOdo());
        EditText liters = Ui.numField(this, box, "給油量 (L)", edit != null ? num(edit.liters) : "");
        EditText cost = Ui.numField(this, box, "金額 (円)", edit != null ? num(edit.cost) : "");
        CheckBox full = new CheckBox(this);
        full.setText("満タン給油");
        full.setChecked(edit == null || edit.full);
        box.addView(full);

        showDialog(edit == null ? "給油を記録" : "給油を編集", box,
                edit == null ? null : () -> store.remove(edit),
                () -> {
                    double o = Double.parseDouble(odo.getText().toString());
                    double l = Double.parseDouble(liters.getText().toString());
                    double m = Double.parseDouble(cost.getText().toString());
                    if (l <= 0) throw new NumberFormatException();
                    Fill f = edit != null ? edit : new Fill();
                    f.date = date[0];
                    f.odo = o;
                    f.liters = l;
                    f.cost = m;
                    f.full = full.isChecked();
                    if (edit == null) store.add(f);
                });
    }

    private void editService(final Service edit) {
        LinearLayout box = dialogBox();
        String[] date = datePicker(box, edit != null ? edit.date : null);

        // Items come from settings; a record's own item stays selectable even if removed there.
        List<String> names = store.typeNames();
        Spinner type = Ui.stringSpinner(this, box, "項目", names,
                edit != null ? edit.type : names.get(0));
        final List<String> options = new ArrayList<>(names);
        if (edit != null && !options.contains(edit.type)) options.add(edit.type);
        int sel = type.getSelectedItemPosition();
        ServiceType def = store.type(options.get(sel));

        EditText odo = Ui.numField(this, box, "総走行距離 (km)", edit != null ? num(edit.odo) : defaultOdo());
        EditText cost = Ui.numField(this, box, "金額 (円)", edit != null ? num(edit.cost) : "");
        EditText memo = Ui.field(this, box, "メモ(「その他」は項目名として使用)",
                edit != null ? edit.memo : "", InputType.TYPE_CLASS_TEXT);
        Ui.Choice intKm = Ui.choice(this, box, "次回まで(距離)", Ui.KM_CHOICES,
                edit != null ? edit.intervalKm : (def != null ? def.km : 0), "km後");
        Ui.Choice intMo = Ui.choice(this, box, "次回まで(期間)", Ui.MONTH_CHOICES,
                edit != null ? edit.intervalMonths : (def != null ? def.months : 0), "ヶ月後");

        // Changing the item selects that item's standard intervals
        // (ignore the initial callback Spinner fires on first layout).
        final int[] lastSel = { sel };
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                if (i == lastSel[0]) return;
                lastSel[0] = i;
                ServiceType t = store.type(options.get(i));
                intKm.select(t != null ? t.km : 0);
                intMo.select(t != null ? t.months : 0);
            }
            public void onNothingSelected(AdapterView<?> p) { }
        });

        showDialog(edit == null ? "メンテナンスを記録" : "メンテナンスを編集", box,
                edit == null ? null : () -> store.remove(edit),
                () -> {
                    double o = Double.parseDouble(odo.getText().toString());
                    String c = cost.getText().toString().trim();
                    Service s = edit != null ? edit : new Service();
                    s.date = date[0];
                    s.type = (String) type.getSelectedItem();
                    s.odo = o;
                    s.cost = c.isEmpty() ? 0 : Double.parseDouble(c);
                    s.memo = memo.getText().toString();
                    s.intervalKm = intKm.value();
                    s.intervalMonths = intMo.value();
                    if (edit == null) store.add(s);
                });
    }

    private int dp(int v) { return Ui.dp(this, v); }
}
