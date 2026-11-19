package jp.mycarlog.app;

import org.json.JSONException;
import org.json.JSONObject;
import java.time.LocalDate;

/** One maintenance record (oil change, inspection, ...). */
public class Service {
    /** Built-in free-form item: the memo is its name, so it can be neither renamed nor removed. */
    public static final String OTHER = "その他";

    public String vehicle;     // Vehicle.id
    public String date;        // yyyy-MM-dd
    public double odo;         // km
    public String type;
    public double cost;        // yen
    public String memo = "";
    public int intervalKm;     // 0 = no km-based reminder
    public int intervalMonths; // 0 = no date-based reminder

    /** Records with the same key supersede each other for reminders. */
    public String key() {
        return OTHER.equals(type) ? type + ":" + memo.trim() : type;
    }

    public String label() {
        return OTHER.equals(type) && !memo.trim().isEmpty() ? memo.trim() : type;
    }

    public double nextOdo() { return intervalKm > 0 ? odo + intervalKm : -1; }

    public LocalDate nextDate() {
        return intervalMonths > 0 ? LocalDate.parse(date).plusMonths(intervalMonths) : null;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("vehicle", vehicle);
        o.put("date", date);
        o.put("odo", odo);
        o.put("type", type);
        o.put("cost", cost);
        o.put("memo", memo);
        o.put("intervalKm", intervalKm);
        o.put("intervalMonths", intervalMonths);
        return o;
    }

    public static Service fromJson(JSONObject o) throws JSONException {
        Service s = new Service();
        s.vehicle = o.optString("vehicle", null);
        s.date = o.getString("date");
        s.odo = o.getDouble("odo");
        s.type = o.getString("type");
        s.cost = o.optDouble("cost", 0);
        s.memo = o.optString("memo", "");
        s.intervalKm = o.optInt("intervalKm", 0);
        s.intervalMonths = o.optInt("intervalMonths", 0);
        return s;
    }
}
