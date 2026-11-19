package jp.mycarlog.app;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A maintenance item the user can pick when logging, with its standard next-time interval. */
public class ServiceType {
    public String name;
    public int km;      // standard "next time" distance, 0 = none
    public int months;  // standard "next time" period, 0 = none

    public ServiceType(String name, int km, int months) {
        this.name = name;
        this.km = km;
        this.months = months;
    }

    /** e.g. "5,000 km後 / 6ヶ月後", or "予定なし". */
    public String summary() {
        List<String> p = new ArrayList<>();
        if (km > 0) p.add(String.format(Locale.JAPAN, "%,d km後", km));
        if (months > 0) p.add(months + "ヶ月後");
        return p.isEmpty() ? "次回の予定なし" : "次回: " + String.join(" / ", p);
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("name", name).put("km", km).put("months", months);
    }

    public static ServiceType fromJson(JSONObject o) throws JSONException {
        return new ServiceType(o.getString("name"), o.optInt("km", 0), o.optInt("months", 0));
    }

    /** Items available before the user changes anything. */
    public static List<ServiceType> defaults() {
        List<ServiceType> l = new ArrayList<>();
        l.add(new ServiceType("オイル交換", 5000, 6));
        l.add(new ServiceType("オイルフィルター交換", 10000, 12));
        l.add(new ServiceType("タイヤ交換", 40000, 48));
        l.add(new ServiceType("タイヤローテーション", 5000, 0));
        l.add(new ServiceType("バッテリー交換", 0, 36));
        l.add(new ServiceType("ワイパー交換", 0, 12));
        l.add(new ServiceType("ブレーキ液交換", 0, 24));
        l.add(new ServiceType("車検", 0, 24));
        l.add(new ServiceType("法定点検", 0, 12));
        l.add(new ServiceType(Service.OTHER, 0, 0));
        return l;
    }
}
