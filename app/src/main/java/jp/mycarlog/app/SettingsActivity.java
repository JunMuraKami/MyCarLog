package jp.mycarlog.app;


import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import android.text.InputType;
import android.view.MenuItem;
import android.widget.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Settings: manage vehicles and the maintenance items offered when logging. */
public class SettingsActivity extends ThemedActivity {
    private int GRAY, TEXT;

    private Store store;
    private LinearLayout content;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        GRAY = pal.gray; TEXT = pal.text;
        setTitle("設定");

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(pal.bg);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(16));
        sv.addView(content);
        setContentView(screen(true, 0, null, null, sv));
        render();
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void render() {
        store = new Store(this);
        content.removeAllViews();

        header("車両");
        for (Vehicle v : store.vehicles) {
            String sub = v.subtitle();
            row(v.name, sub, v.owned ? TEXT : GRAY, () -> editVehicle(v));
        }
        addButton("＋ 車両を追加", () -> editVehicle(null));

        header("整備項目");
        note("記録を追加するときに選べる項目と、その標準の次回予定です。");
        for (ServiceType t : store.types) row(t.name, t.summary(), TEXT, () -> editType(t));
        addButton("＋ 項目を追加", () -> editType(null));

        header("テーマ");
        note("アプリの配色を切り替えます。");
        addButton("テーマ: " + pal.name, this::chooseTheme);

        header("アプリについて");
        note("バージョン " + Updater.current(this) + "  /  GitHub のリリースから更新を受け取ります。");
        addButton("更新を確認", this::checkUpdate);

        header("データ");
        note("車両・給油・整備・項目設定のすべてをファイルに書き出し/読み込みします。");
        addButton("エクスポート(ファイルに保存)", () -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "carlog-" + LocalDate.now() + ".json");
            startActivityForResult(i, REQ_EXPORT);
        });
        addButton("インポート(ファイルから復元)", () -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            startActivityForResult(i, REQ_IMPORT);
        });

        ImageView gh = new ImageView(this);
        gh.setImageResource(R.drawable.ic_github);
        gh.setContentDescription("GitHub");
        if (pal.dark) gh.setColorFilter(pal.text);
        gh.setPadding(dp(12), dp(12), dp(12), dp(12));
        gh.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)));
            } catch (Exception e) {
                Toast.makeText(this, "ブラウザを開けませんでした", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(72), dp(72));
        glp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        glp.topMargin = dp(20);
        content.addView(gh, glp);
        TextView ghNote = new TextView(this);
        ghNote.setText("最新版のダウンロード、更新履歴、READMEは\nGitHubで確認できます。");
        ghNote.setTextSize(12);
        ghNote.setTextColor(GRAY);
        ghNote.setGravity(android.view.Gravity.CENTER);
        ghNote.setPadding(dp(4), 0, dp(4), dp(8));
        content.addView(ghNote, new LinearLayout.LayoutParams(-1, -2));
    }

    private static final String GITHUB_URL = "https://github.com/JunMuraKami/MyCarLog";

    private void chooseTheme() {
        String[] names = new String[AppTheme.ALL.length];
        int sel = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = AppTheme.ALL[i].name;
            if (AppTheme.ALL[i].id.equals(pal.id)) sel = i;
        }
        new AlertDialog.Builder(this).setTitle("テーマ")
                .setSingleChoiceItems(names, sel, (d, which) -> {
                    AppTheme.set(this, AppTheme.ALL[which].id);
                    d.dismiss();
                    recreate();
                })
                .setNegativeButton("キャンセル", null).show();
    }

    // ---- app update ----

    private void checkUpdate() {
        Toast.makeText(this, "確認中…", Toast.LENGTH_SHORT).show();
        Updater.check(this, (latest, ok) -> {
            if (isFinishing()) return;
            if (!ok) Toast.makeText(this, "確認できませんでした(通信を確認してください)", Toast.LENGTH_LONG).show();
            else if (latest == null) Toast.makeText(this, "最新のバージョンです", Toast.LENGTH_SHORT).show();
            else Updater.prompt(this, latest);
        });
    }

    // ---- export / import ----

    private static final int REQ_EXPORT = 11, REQ_IMPORT = 12;
    private static final String[] KEYS = { "vehicles", "types", "fills", "services" };

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_EXPORT) doExport(uri);
        else if (req == REQ_IMPORT) confirmImport(uri);
    }

    private void doExport(Uri uri) {
        try {
            store.save();
            android.content.SharedPreferences sp = getSharedPreferences("carlog", Context.MODE_PRIVATE);
            JSONObject o = new JSONObject();
            o.put("app", "CarLog");
            o.put("format", 1);
            for (String k : KEYS) o.put(k, new JSONArray(sp.getString(k, "[]")));
            o.put("currentVehicle", sp.getString("currentVehicle", ""));
            try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
                os.write(o.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this, "エクスポートしました", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "エクスポートに失敗しました", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmImport(Uri uri) {
        final JSONObject o;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            o = new JSONObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            if (!"CarLog".equals(o.optString("app"))) throw new Exception("not CarLog");
            for (String k : KEYS) o.getJSONArray(k);
            if (o.getJSONArray("vehicles").length() == 0) throw new Exception("no vehicles");
        } catch (Exception e) {
            Toast.makeText(this, "MyCarLogのエクスポートファイルではありません", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("インポート")
                .setMessage("車両 " + o.optJSONArray("vehicles").length() + " 台、給油 "
                        + o.optJSONArray("fills").length() + " 件、整備 "
                        + o.optJSONArray("services").length() + " 件を読み込みます。\n"
                        + "現在のデータはすべて置き換えられます。元に戻せません。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("置き換える", (x, w) -> {
                    try {
                        android.content.SharedPreferences.Editor ed =
                                getSharedPreferences("carlog", Context.MODE_PRIVATE).edit();
                        for (String k : KEYS) ed.putString(k, o.getJSONArray(k).toString());
                        ed.putString("currentVehicle", o.optString("currentVehicle", ""));
                        ed.commit();
                        render();
                        Toast.makeText(this, "インポートしました", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "インポートに失敗しました", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    // ---- vehicles ----

    private void editVehicle(final Vehicle edit) {
        LinearLayout box = dialogBox();
        final Vehicle v = edit != null ? edit : new Vehicle(null, "");
        EditText name = Ui.field(this, box, "車両名(ホームでの表示名)", v.name, InputType.TYPE_CLASS_TEXT);
        EditText make = Ui.field(this, box, "メーカー", v.make, InputType.TYPE_CLASS_TEXT);
        EditText model = Ui.field(this, box, "車種", v.model, InputType.TYPE_CLASS_TEXT);
        EditText grade = Ui.field(this, box, "グレード", v.grade, InputType.TYPE_CLASS_TEXT);

        List<String> years = new ArrayList<>();
        years.add("未設定");
        for (int y = LocalDate.now().getYear() + 1; y >= 1950; y--) years.add(String.valueOf(y));
        Spinner year = Ui.stringSpinner(this, box, "年式", years, v.year > 0 ? String.valueOf(v.year) : "未設定");

        Spinner fuel = Ui.stringSpinner(this, box, "油種", Arrays.asList(Vehicle.FUEL_TYPES),
                v.fuelType.isEmpty() ? "未設定" : v.fuelType);
        EditText tank = Ui.numField(this, box, "タンク容量 (L)", v.tank > 0 ? num(v.tank) : "");
        EditText plate = Ui.field(this, box, "ナンバー", v.plate, InputType.TYPE_CLASS_TEXT);
        EditText vin = Ui.field(this, box, "VIN(車台番号)", v.vin,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        CheckBox owned = new CheckBox(this);
        owned.setText("現在所有している");
        owned.setChecked(v.owned);
        box.addView(owned);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog.Builder d = new AlertDialog.Builder(this)
                .setTitle(edit == null ? "車両を追加" : "車両を編集")
                .setView(sv)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("保存", null);
        if (edit != null && store.vehicles.size() > 1)
            d.setNeutralButton("削除", (x, w) -> new AlertDialog.Builder(this)
                    .setTitle("「" + edit.name + "」を削除")
                    .setMessage("この車両の給油・整備の記録もすべて削除されます。元に戻せません。")
                    .setNegativeButton("キャンセル", null)
                    .setPositiveButton("削除", (y, z) -> {
                        store.deleteVehicle(edit.id);
                        render();
                    })
                    .show());
        final AlertDialog dlg = d.create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) n = (make.getText().toString().trim() + " " + model.getText().toString().trim()).trim();
            if (n.isEmpty()) {
                Toast.makeText(this, "車両名を入力してください", Toast.LENGTH_SHORT).show();
                return;
            }
            double cap = 0;
            String t = tank.getText().toString().trim();
            try {
                if (!t.isEmpty()) cap = Double.parseDouble(t);
            } catch (NumberFormatException ex) {
                Toast.makeText(this, "タンク容量を正しく入力してください", Toast.LENGTH_SHORT).show();
                return;
            }
            v.name = n;
            v.make = make.getText().toString().trim();
            v.model = model.getText().toString().trim();
            v.grade = grade.getText().toString().trim();
            String y = (String) year.getSelectedItem();
            v.year = "未設定".equals(y) ? 0 : Integer.parseInt(y);
            String f = (String) fuel.getSelectedItem();
            v.fuelType = "未設定".equals(f) ? "" : f;
            v.tank = cap;
            v.plate = plate.getText().toString().trim();
            v.vin = vin.getText().toString().trim();
            v.owned = owned.isChecked();
            if (edit == null) store.addVehicle(v); else store.save();
            dlg.dismiss();
            render();
        });
    }

    // ---- maintenance items ----

    private void editType(final ServiceType edit) {
        LinearLayout box = dialogBox();
        final boolean fixed = edit != null && Service.OTHER.equals(edit.name);
        EditText name = Ui.field(this, box, "項目名", edit != null ? edit.name : "", InputType.TYPE_CLASS_TEXT);
        if (fixed) {
            name.setEnabled(false);
            TextView hint = Ui.label(this, box, "「その他」は項目名を記録ごとのメモで決めるため、名前の変更・削除はできません。");
            hint.setTextSize(12);
        }
        Ui.Choice km = Ui.choice(this, box, "標準の次回まで(距離)", Ui.KM_CHOICES,
                edit != null ? edit.km : 0, "km後");
        Ui.Choice mo = Ui.choice(this, box, "標準の次回まで(期間)", Ui.MONTH_CHOICES,
                edit != null ? edit.months : 0, "ヶ月後");

        AlertDialog.Builder d = new AlertDialog.Builder(this)
                .setTitle(edit == null ? "項目を追加" : "項目を編集")
                .setView(box)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("保存", null);
        if (edit != null && !fixed) {
            int uses = store.countServices(edit.name);
            d.setNeutralButton("削除", (x, w) -> new AlertDialog.Builder(this)
                    .setTitle("「" + edit.name + "」を削除")
                    .setMessage("選択肢から外します。" + (uses > 0
                            ? "この項目の記録 " + uses + " 件は、履歴としてそのまま残ります。" : ""))
                    .setNegativeButton("キャンセル", null)
                    .setPositiveButton("削除", (y, z) -> {
                        store.deleteType(edit);
                        store.save();
                        render();
                    })
                    .show());
        }
        final AlertDialog dlg = d.create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String n = fixed ? edit.name : name.getText().toString().trim();
            if (n.isEmpty()) {
                Toast.makeText(this, "項目名を入力してください", Toast.LENGTH_SHORT).show();
                return;
            }
            ServiceType same = store.type(n);
            if (same != null && same != edit) {
                Toast.makeText(this, "同じ名前の項目があります", Toast.LENGTH_SHORT).show();
                return;
            }
            if (edit == null) {
                ServiceType other = store.type(Service.OTHER); // keep その他 last
                store.types.add(store.types.indexOf(other),
                        new ServiceType(n, km.value(), mo.value()));
            } else {
                if (!n.equals(edit.name)) store.renameType(edit, n);
                edit.km = km.value();
                edit.months = mo.value();
            }
            store.save();
            dlg.dismiss();
            render();
        });
    }

    // ---- view helpers ----

    private void header(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(GRAY);
        t.setPadding(dp(4), dp(16), 0, dp(6));
        content.addView(t);
    }

    private void note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(GRAY);
        t.setPadding(dp(4), 0, dp(4), dp(8));
        content.addView(t);
    }

    private void row(String title, String sub, int color, Runnable onTap) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pal.card);
        bg.setCornerRadius(dp(16));
        r.setBackground(bg);
        r.setOnClickListener(v -> onTap.run());
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(color);
        r.addView(t);
        if (!sub.isEmpty()) {
            TextView s = new TextView(this);
            s.setText(sub);
            s.setTextSize(13);
            s.setTextColor(GRAY);
            r.addView(s);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(6);
        content.addView(r, lp);
    }

    private void addButton(String label, Runnable onTap) {
        Button b = new Button(this);
        b.setText(label);
        pill(b, false);
        b.setOnClickListener(v -> onTap.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        content.addView(b, lp);
    }

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), 0, dp(20), 0);
        return box;
    }

    private static String num(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private int dp(int v) { return Ui.dp(this, v); }
}
