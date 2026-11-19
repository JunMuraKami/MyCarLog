package jp.mycarlog.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Colour themes ("skins") selectable in settings. */
final class AppTheme {
    static final class Pal {
        final String id, name;
        final boolean dark;
        final int bg, card, text, gray, red, orange, accent, accentBg, redBg, orangeBg, bar;

        Pal(String id, String name, boolean dark, int bg, int card, int text, int gray,
            int red, int orange, int accent, int accentBg, int redBg, int orangeBg, int bar) {
            this.id = id; this.name = name; this.dark = dark;
            this.bg = bg; this.card = card; this.text = text; this.gray = gray;
            this.red = red; this.orange = orange; this.accent = accent; this.accentBg = accentBg;
            this.redBg = redBg; this.orangeBg = orangeBg; this.bar = bar;
        }
    }

    static final Pal[] ALL = {
        new Pal("standard", "標準", false, 0xFFF2F4F7, 0xFFFFFFFF, 0xFF212121, 0xFF616161,
                0xFFD32F2F, 0xFFEF6C00, 0xFF1976D2, 0xFFE3F2FD, 0xFFFDECEA, 0xFFFFF3E0, 0xFF212121),
        new Pal("dark", "ダーク", true, 0xFF121212, 0xFF1E1E1E, 0xFFEEEEEE, 0xFFA8A8A8,
                0xFFEF5350, 0xFFFFA726, 0xFF64B5F6, 0xFF1A2733, 0xFF331A1A, 0xFF332712, 0xFF000000),
        new Pal("blue", "ブルー", false, 0xFFE8F1FB, 0xFFFFFFFF, 0xFF16263A, 0xFF52667D,
                0xFFD32F2F, 0xFFEF6C00, 0xFF1565C0, 0xFFD6E8FA, 0xFFFDECEA, 0xFFFFF3E0, 0xFF1565C0),
        new Pal("green", "グリーン", false, 0xFFECF4ED, 0xFFFFFFFF, 0xFF1B2A1C, 0xFF566657,
                0xFFD32F2F, 0xFFEF6C00, 0xFF2E7D32, 0xFFDCEFDD, 0xFFFDECEA, 0xFFFFF3E0, 0xFF2E7D32),
        new Pal("sport", "スポーツ", true, 0xFF14100F, 0xFF241B1A, 0xFFF2E9E7, 0xFFB09E9B,
                0xFFFF5252, 0xFFFFB74D, 0xFFFF8A80, 0xFF3A1F1D, 0xFF3A1717, 0xFF352814, 0xFFB71C1C),
    };

    private AppTheme() { }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE);
    }

    static Pal current(Context c) {
        String id = sp(c).getString("theme", "standard");
        for (Pal p : ALL) if (p.id.equals(id)) return p;
        return ALL[0];
    }

    static void set(Context c, String id) {
        sp(c).edit().putString("theme", id).apply();
    }
}
