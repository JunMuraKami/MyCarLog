package jp.mycarlog.app;

import org.json.JSONException;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A car whose fills and maintenance are tracked separately. */
public class Vehicle {
    /** Choices for the fuel type picker (the first entry means "not set"). */
    public static final String[] FUEL_TYPES =
            {"未設定", "レギュラー", "ハイオク", "軽油", "LPG", "電気", "その他"};

    public String id;
    public String name;          // display name shown on the home screen
    public String make = "";     // メーカー
    public String model = "";    // 車種
    public String grade = "";    // グレード
    public int year;             // 年式, 0 = not set
    public String fuelType = ""; // 油種, "" = not set
    public double tank;          // タンク容量 (L), 0 = not set
    public String plate = "";    // ナンバー
    public String vin = "";      // 車台番号
    public boolean owned = true; // 現在所有中
    public String acquired = ""; // 購入日 yyyy-MM-dd, "" = not set
    public String released = ""; // 手放した日 yyyy-MM-dd, "" = not set
    public String icon = "";     // preset icon id (see Avatar.ICON_IDS), used when there is no photo
    public String photo = "";    // アイコン写真(base64 JPEG), "" = not set

    public Vehicle(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /** Short description for the home screen / settings list, or "" if nothing is set. */
    public String subtitle() {
        List<String> a = new ArrayList<>();
        for (String s : new String[]{make, model, grade}) if (!s.trim().isEmpty()) a.add(s.trim());
        List<String> b = new ArrayList<>();
        if (year > 0) b.add(year + "年");
        if (!fuelType.isEmpty()) b.add(fuelType);
        if (tank > 0) b.add(String.format(Locale.JAPAN, "タンク %s L",
                tank == Math.floor(tank) ? String.valueOf((long) tank) : String.valueOf(tank)));
        if (!plate.trim().isEmpty()) b.add(plate.trim());
        if (!owned) b.add("過去の車両");
        List<String> lines = new ArrayList<>();
        if (!a.isEmpty()) lines.add(String.join(" ", a));
        if (!b.isEmpty()) lines.add(String.join(" ・ ", b));
        String own = ownership();
        if (!own.isEmpty()) lines.add(own);
        return String.join("\n", lines);
    }

    private static LocalDate date(String s) {
        try {
            return s == null || s.isEmpty() ? null : LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String ymd(LocalDate d) {
        return String.format(Locale.JAPAN, "%04d/%02d/%02d", d.getYear(), d.getMonthValue(), d.getDayOfMonth());
    }

    /** e.g. "所有期間: 2021/04/12 〜 2026/03/05(4年11ヶ月)", or "" when no dates are set. */
    public String ownership() {
        LocalDate from = date(acquired);
        LocalDate to = owned ? null : date(released);
        if (from == null && to == null) return "";
        StringBuilder sb = new StringBuilder("所有期間: ");
        if (from != null) sb.append(ymd(from));
        sb.append(" 〜 ");
        if (to != null) sb.append(ymd(to)); else if (owned) sb.append("現在");
        if (from != null && (to != null || owned)) {
            LocalDate end = to != null ? to : LocalDate.now();
            if (!end.isBefore(from)) {
                Period p = Period.between(from, end);
                int months = p.getYears() * 12 + p.getMonths();
                String len = months < 1 ? "1ヶ月未満"
                        : (months >= 12 ? months / 12 + "年" : "")
                        + (months % 12 > 0 ? months % 12 + "ヶ月" : "");
                sb.append("(").append(len).append(")");
            }
        }
        return sb.toString();
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("id", id).put("name", name)
                .put("make", make).put("model", model).put("grade", grade)
                .put("year", year).put("fuelType", fuelType).put("tank", tank)
                .put("plate", plate).put("vin", vin).put("owned", owned).put("photo", photo).put("icon", icon).put("acquired", acquired).put("released", released);
    }

    public static Vehicle fromJson(JSONObject o) throws JSONException {
        Vehicle v = new Vehicle(o.getString("id"), o.getString("name"));
        v.make = o.optString("make", "");
        v.model = o.optString("model", "");
        v.grade = o.optString("grade", "");
        v.year = o.optInt("year", 0);
        v.fuelType = o.optString("fuelType", "");
        v.tank = o.optDouble("tank", 0);
        v.plate = o.optString("plate", "");
        v.vin = o.optString("vin", "");
        v.owned = o.optBoolean("owned", true);
        v.photo = o.optString("photo", "");
        v.icon = o.optString("icon", "");
        v.acquired = o.optString("acquired", "");
        v.released = o.optString("released", "");
        return v;
    }
}
