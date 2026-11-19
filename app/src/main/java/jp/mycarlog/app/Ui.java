package jp.mycarlog.app;

import android.content.Context;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Small view builders shared by the log and settings pages. */
final class Ui {
    private Ui() { }

    /** Choices for the "next time" pickers; 0 = none. */
    static final int[] KM_CHOICES =
            {0, 1000, 2000, 3000, 5000, 7500, 10000, 15000, 20000, 30000, 40000, 50000, 100000};
    static final int[] MONTH_CHOICES = {0, 1, 2, 3, 6, 12, 18, 24, 36, 48, 60};

    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    static TextView label(Context c, LinearLayout box, String s) {
        TextView l = new TextView(c);
        l.setText(s);
        l.setPadding(0, dp(c, 10), 0, 0);
        box.addView(l);
        return l;
    }

    static EditText field(Context c, LinearLayout box, String label, String value, int inputType) {
        label(c, box, label);
        EditText e = new EditText(c);
        e.setText(value);
        e.setInputType(inputType);
        box.addView(e);
        return e;
    }

    static EditText numField(Context c, LinearLayout box, String label, String value) {
        return field(c, box, label, value,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    }

    /** Dropdown of fixed strings; the current value is appended if it is not among them. */
    static Spinner stringSpinner(Context c, LinearLayout box, String label,
                                 List<String> options, String current) {
        label(c, box, label);
        List<String> list = new ArrayList<>(options);
        if (!list.contains(current)) list.add(current);
        Spinner sp = new Spinner(c);
        sp.setAdapter(new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, list));
        sp.setSelection(list.indexOf(current));
        box.addView(sp);
        return sp;
    }

    /** Dropdown of interval choices; the current value is added if it is not a preset. */
    static class Choice {
        final Spinner spinner;
        final List<Integer> values = new ArrayList<>();

        Choice(Spinner s) { spinner = s; }

        int value() { return values.get(spinner.getSelectedItemPosition()); }

        void select(int v) {
            int i = values.indexOf(v);
            spinner.setSelection(i >= 0 ? i : 0);
        }
    }

    static Choice choice(Context c, LinearLayout box, String label, int[] presets,
                         int current, String unit) {
        label(c, box, label);
        Choice ch = new Choice(new Spinner(c));
        for (int v : presets) ch.values.add(v);
        if (!ch.values.contains(current)) {
            ch.values.add(current);
            Collections.sort(ch.values);
        }
        List<String> names = new ArrayList<>();
        for (int v : ch.values)
            names.add(v == 0 ? "なし" : String.format(Locale.JAPAN, "%,d %s", v, unit));
        ch.spinner.setAdapter(new ArrayAdapter<>(c,
                android.R.layout.simple_spinner_dropdown_item, names));
        ch.select(current);
        box.addView(ch.spinner);
        return ch;
    }
}
