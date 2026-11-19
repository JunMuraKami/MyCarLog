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
import android.view.View;
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
        note("長押ししてドラッグすると、車両の並び順を変更できます。");
        final LinearLayout vehBox = new LinearLayout(this);
        vehBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(vehBox, new LinearLayout.LayoutParams(-1, -2));
        rowTarget = vehBox;
        for (Vehicle v : store.vehicles) {
            String sub = v.subtitle();
            View r = row(v.name, sub, v.owned ? TEXT : GRAY, () -> editVehicle(v),
                    Avatar.view(this, v.name, v.photo, v.icon, 52, false, pal));
            r.setTag(v.id);
            enableDragReorder(r, vehBox);
        }
        rowTarget = null;
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

    private static final int REQ_EXPORT = 11, REQ_IMPORT = 12, REQ_PHOTO = 13;
    private String pendingPhoto;      // null = unchanged, "" = removed
    private String pendingIcon;       // null = unchanged, "" = none
    private LinearLayout photoBox;
    private Vehicle photoVehicle;
    private static final String[] KEYS = { "vehicles", "types", "fills", "services" };

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_PHOTO) {
            android.graphics.Bitmap bmp = Avatar.load(getContentResolver(), uri);
            if (bmp == null) Toast.makeText(this, "画像を読み込めませんでした", Toast.LENGTH_SHORT).show();
            else cropPhoto(bmp);
        } else if (req == REQ_EXPORT) doExport(uri);
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

    /** Long-press a vehicle row, then drag it up or down; the new order is saved when dropped. */
    private void enableDragReorder(final View row, final LinearLayout box) {
        row.setOnLongClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            v.startDragAndDrop(null, new View.DragShadowBuilder(v), v, 0);
            return true;
        });
        row.setOnDragListener((target, e) -> {
            final View dragged = (View) e.getLocalState();
            switch (e.getAction()) {
                case android.view.DragEvent.ACTION_DRAG_STARTED:
                    if (target == dragged) dragged.setAlpha(0.4f);
                    return dragged != null && dragged.getParent() == box;
                case android.view.DragEvent.ACTION_DRAG_ENTERED:
                    if (target != dragged) {
                        int to = box.indexOfChild(target);
                        box.removeView(dragged);
                        box.addView(dragged, to);
                    }
                    return true;
                case android.view.DragEvent.ACTION_DRAG_ENDED:
                    if (target == dragged) {
                        dragged.setAlpha(1f);
                        List<String> ids = new ArrayList<>();
                        for (int i = 0; i < box.getChildCount(); i++) ids.add((String) box.getChildAt(i).getTag());
                        store.reorderVehicles(ids);
                    }
                    return true;
                default:
                    return true;
            }
        });
    }

    /** A labelled date button with a clear button; the chosen date (yyyy-MM-dd or "") lives in val[0]. */
    private void dateRow(LinearLayout box, String label, final String[] val) {
        Ui.label(this, box, label);
        LinearLayout r = new LinearLayout(this);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        final Button b = new Button(this);
        pill(b, false);
        b.setText(val[0].isEmpty() ? "未設定" : val[0]);
        Button x = new Button(this);
        x.setText("×");
        pill(x, false);
        b.setOnClickListener(vw -> {
            LocalDate d = LocalDate.now();
            try {
                if (!val[0].isEmpty()) d = LocalDate.parse(val[0]);
            } catch (Exception ignored) { }
            new android.app.DatePickerDialog(this, (dp, y, m, dd) -> {
                val[0] = LocalDate.of(y, m + 1, dd).toString();
                b.setText(val[0]);
            }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
        });
        x.setOnClickListener(vw -> {
            val[0] = "";
            b.setText("未設定");
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, dp(44), 1f);
        bl.rightMargin = dp(8);
        r.addView(b, bl);
        r.addView(x, new LinearLayout.LayoutParams(dp(56), dp(44)));
        box.addView(r);
    }

    /** Lets the user pan / zoom the picked photo inside a square window, then keeps that square. */
    private void cropPhoto(android.graphics.Bitmap bmp) {
        int side = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
        final CropView crop = new CropView(this, bmp);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, dp(8), 0, 0);
        box.addView(crop, new LinearLayout.LayoutParams(side, side));
        TextView hint = new TextView(this);
        hint.setText("ドラッグで移動、ピンチで拡大・縮小");
        hint.setTextSize(12);
        hint.setPadding(0, dp(8), 0, dp(4));
        box.addView(hint);
        new AlertDialog.Builder(this).setTitle("正方形に切り取り").setView(box)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("決定", (d, w) -> {
                    pendingPhoto = Avatar.toBase64(crop.result());
                    pendingIcon = "";
                    showPhoto();
                })
                .show();
    }

    private void showPhoto() {
        photoBox.removeAllViews();
        String p = pendingPhoto != null ? pendingPhoto : photoVehicle.photo;
        String i = pendingIcon != null ? pendingIcon : photoVehicle.icon;
        photoBox.addView(Avatar.view(this, photoVehicle.name, p, i, 96, false, pal));
    }

    private View centered(View v) {
        LinearLayout w = new LinearLayout(this);
        w.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        w.addView(v);
        return w;
    }

    /** Grid of preset car icons, used when a vehicle has no photo. */
    private void chooseIcon() {
        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(4);
        grid.setPadding(dp(8), dp(8), dp(8), dp(8));
        final AlertDialog[] dlg = new AlertDialog[1];
        for (int k = 0; k < Avatar.ICON_IDS.length; k++) {
            final String id = Avatar.ICON_IDS[k];
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(4), dp(6), dp(4), dp(6));
            cell.addView(Avatar.view(this, "", "", id, 60, id.equals(
                    pendingIcon != null ? pendingIcon : photoVehicle.icon), pal));
            cell.setOnClickListener(x -> {
                pendingIcon = id;
                pendingPhoto = "";
                showPhoto();
                dlg[0].dismiss();
            });
            android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
            lp.width = dp(72);
            lp.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT;
            grid.addView(cell, lp);
        }
        dlg[0] = new AlertDialog.Builder(this).setTitle("アイコンから選ぶ").setView(centered(grid))
                .setNegativeButton("キャンセル", null).show();
    }

    private void editVehicle(final Vehicle edit) {
        LinearLayout box = dialogBox();
        final Vehicle v = edit != null ? edit : new Vehicle(null, "");
        photoVehicle = v;
        pendingPhoto = null;
        pendingIcon = null;
        photoBox = new LinearLayout(this);
        photoBox.setOrientation(LinearLayout.VERTICAL);
        photoBox.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        box.addView(photoBox);
        LinearLayout photoBtns = new LinearLayout(this);
        photoBtns.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        Button pick = new Button(this);
        pick.setText("写真を選ぶ");
        pill(pick, false);
        pick.setOnClickListener(x -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("image/*"), REQ_PHOTO));
        Button icon = new Button(this);
        icon.setText("アイコンから選ぶ");
        pill(icon, false);
        icon.setOnClickListener(x -> chooseIcon());
        Button clear = new Button(this);
        clear.setText("写真・アイコンを外す");
        pill(clear, false);
        clear.setOnClickListener(x -> { pendingPhoto = ""; pendingIcon = ""; showPhoto(); });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, dp(44), 1f);
        pl.setMargins(dp(4), dp(8), dp(4), dp(4));
        photoBtns.addView(pick, pl);
        photoBtns.addView(icon, pl);
        box.addView(photoBtns);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, dp(44));
        cl.setMargins(dp(4), dp(0), dp(4), dp(4));
        box.addView(clear, cl);
        showPhoto();
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
        final String[] acquired = { v.acquired };
        final String[] released = { v.released };
        dateRow(box, "購入日", acquired);
        CheckBox owned = new CheckBox(this);
        owned.setText("現在所有している");
        owned.setChecked(v.owned);
        box.addView(owned);
        final LinearLayout releasedBox = new LinearLayout(this);
        releasedBox.setOrientation(LinearLayout.VERTICAL);
        dateRow(releasedBox, "手放した日", released);
        releasedBox.setVisibility(v.owned ? View.GONE : View.VISIBLE);
        box.addView(releasedBox);
        owned.setOnCheckedChangeListener((cb, on) -> releasedBox.setVisibility(on ? View.GONE : View.VISIBLE));

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
            v.acquired = acquired[0];
            v.released = v.owned ? "" : released[0];
            if (pendingPhoto != null) v.photo = pendingPhoto;
            if (pendingIcon != null) v.icon = pendingIcon;
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

    private LinearLayout rowTarget;   // where row() puts the view; null = content

    private View row(String title, String sub, int color, Runnable onTap) {
        return row(title, sub, color, onTap, null);
    }

    private View row(String title, String sub, int color, Runnable onTap, View lead) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        if (lead != null) {
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(dp(52), dp(52));
            ll.rightMargin = dp(14);
            r.addView(lead, ll);
        }
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
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
        col.addView(t);
        if (!sub.isEmpty()) {
            TextView s = new TextView(this);
            s.setText(sub);
            s.setTextSize(13);
            s.setTextColor(GRAY);
            col.addView(s);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(6);
        (rowTarget != null ? rowTarget : content).addView(r, lp);
        return r;
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
