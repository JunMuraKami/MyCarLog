package jp.mycarlog.app;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;

/** Maintenance items offered when logging a service, with their standard next-time intervals. */
public class ServiceTypesActivity extends ThemedActivity {
    private int GRAY, TEXT;
    private Store store;
    private LinearLayout content;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        GRAY = pal.gray; TEXT = pal.text;
        setTitle("整備項目の設定");

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(pal.bg);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(16));
        sv.addView(content);
        setContentView(screen(true, 0, null, null, sv));
        render();
    }

    private void render() {
        store = new Store(this);
        content.removeAllViews();
        TextView t = new TextView(this);
        t.setText("記録を追加するときに選べる項目と、その標準の次回予定です。");
        t.setTextSize(12);
        t.setTextColor(GRAY);
        t.setPadding(dp(4), dp(8), dp(4), dp(8));
        content.addView(t);
        for (ServiceType st : store.types) row(st.name, st.summary(), () -> editType(st));
        Button add = new Button(this);
        add.setText("＋ 項目を追加");
        pill(add, false);
        add.setOnClickListener(v -> editType(null));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(4);
        content.addView(add, lp);
    }

    private void row(String title, String sub, Runnable onTap) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pal.card);
        bg.setCornerRadius(dp(16));
        r.setBackground(bg);
        r.setOnClickListener(v -> onTap.run());
        TextView a = new TextView(this);
        a.setText(title);
        a.setTextSize(16);
        a.setTypeface(Typeface.DEFAULT_BOLD);
        a.setTextColor(TEXT);
        r.addView(a);
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

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), 0, dp(20), 0);
        return box;
    }

    private int dp(int v) { return Ui.dp(this, v); }

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
}
