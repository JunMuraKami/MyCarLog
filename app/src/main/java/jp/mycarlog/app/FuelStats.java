package jp.mycarlog.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Monthly / yearly totals of fuel records (liters, cost, distance). */
public final class FuelStats {
    public static final class Period {
        public String key = "";   // "2026-11" or "2026"
        public int count;
        public double liters, cost, km;

        public double pricePerLiter() { return liters > 0 ? cost / liters : 0; }

        public double costPerKm() { return km > 0 ? cost / km : 0; }

        /** One-line detail, e.g. "3回 ・ 74.20 L ・ ¥12,500". */
        public String line1() {
            return String.format(Locale.JAPAN, "%d回 ・ %.2f L ・ ¥%,.0f", count, liters, cost);
        }

        /** Second line: average price and distance. */
        public String line2() {
            StringBuilder sb = new StringBuilder();
            if (liters > 0) sb.append(String.format(Locale.JAPAN, "平均 ¥%.0f/L", pricePerLiter()));
            if (km > 0) {
                if (sb.length() > 0) sb.append(" ・ ");
                sb.append(String.format(Locale.JAPAN, "走行 %,.0f km ・ ¥%.1f/km", km, costPerKm()));
            }
            return sb.toString();
        }
    }

    private FuelStats() { }

    /** Periods with fuel records, newest first. Distance is the odometer gain since the previous fill. */
    public static List<Period> compute(List<Fill> fills, boolean monthly) {
        List<Fill> sorted = new ArrayList<>(fills);
        Collections.sort(sorted, (a, b) -> {
            int c = a.date.compareTo(b.date);
            return c != 0 ? c : Double.compare(a.odo, b.odo);
        });
        Map<String, Period> map = new LinkedHashMap<>();
        Fill prev = null;
        for (Fill f : sorted) {
            String key = f.date.length() >= (monthly ? 7 : 4) ? f.date.substring(0, monthly ? 7 : 4) : f.date;
            Period p = map.get(key);
            if (p == null) {
                p = new Period();
                p.key = key;
                map.put(key, p);
            }
            p.count++;
            p.liters += f.liters;
            p.cost += f.cost;
            if (prev != null && f.odo > prev.odo) p.km += f.odo - prev.odo;
            prev = f;
        }
        List<Period> out = new ArrayList<>(map.values());
        Collections.reverse(out);
        return out;
    }

    /** The period for the given key ("2026-11" / "2026"), or an empty one. */
    public static Period find(List<Fill> fills, boolean monthly, String key) {
        for (Period p : compute(fills, monthly)) if (p.key.equals(key)) return p;
        Period none = new Period();
        none.key = key;
        return none;
    }
}
