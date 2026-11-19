package jp.mycarlog.app;

import org.json.JSONException;
import org.json.JSONObject;

/** One refuelling record. */
public class Fill {
    public String vehicle;   // Vehicle.id
    public String date;      // yyyy-MM-dd
    public double odo;       // km
    public double liters;
    public double cost;      // yen
    public boolean full;     // filled to full tank
    public double economy = -1; // km/L, computed; -1 = n/a

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("vehicle", vehicle);
        o.put("date", date);
        o.put("odo", odo);
        o.put("liters", liters);
        o.put("cost", cost);
        o.put("full", full);
        return o;
    }

    public static Fill fromJson(JSONObject o) throws JSONException {
        Fill f = new Fill();
        f.vehicle = o.optString("vehicle", null);
        f.date = o.getString("date");
        f.odo = o.getDouble("odo");
        f.liters = o.getDouble("liters");
        f.cost = o.getDouble("cost");
        f.full = o.optBoolean("full", true);
        return f;
    }
}
