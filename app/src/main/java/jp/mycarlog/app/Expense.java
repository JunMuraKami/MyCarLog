package jp.mycarlog.app;

import org.json.JSONException;
import org.json.JSONObject;

/** One other expense: tax, insurance, parts bought, parking, ... */
public class Expense {
    public static final String[] CATEGORIES =
            {"税金", "保険", "車検", "パーツ購入", "駐車場", "高速料金", "その他"};

    public String vehicle;     // Vehicle.id
    public String date;        // yyyy-MM-dd
    public String category = CATEGORIES[0];
    public double cost;        // yen
    public String memo = "";

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("vehicle", vehicle).put("date", date)
                .put("category", category).put("cost", cost).put("memo", memo);
    }

    public static Expense fromJson(JSONObject o) throws JSONException {
        Expense e = new Expense();
        e.vehicle = o.optString("vehicle", null);
        e.date = o.getString("date");
        e.category = o.optString("category", CATEGORIES[CATEGORIES.length - 1]);
        e.cost = o.getDouble("cost");
        e.memo = o.optString("memo", "");
        return e;
    }
}
