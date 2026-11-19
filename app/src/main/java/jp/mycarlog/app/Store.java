package jp.mycarlog.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Persists vehicles, fills and maintenance records as JSON.
 * {@link #fills} and {@link #services} are the selected vehicle's records.
 */
public class Store {
    /** Warn this many km / days before a maintenance item is due. */
    public static final int SOON_KM = 500;
    public static final int SOON_DAYS = 30;

    private final SharedPreferences sp;
    public final List<Vehicle> vehicles = new ArrayList<>();
    /** Maintenance items the user can choose when logging (editable in settings). */
    public final List<ServiceType> types = new ArrayList<>();
    public String vehicleId;
    private final List<Fill> allFills = new ArrayList<>();
    private final List<Service> allServices = new ArrayList<>();
    /** Selected vehicle's records: fills by odometer, services newest first. */
    public final List<Fill> fills = new ArrayList<>();
    public final List<Service> services = new ArrayList<>();

    public Store(Context c) {
        sp = c.getSharedPreferences("carlog", Context.MODE_PRIVATE);
        try {
            JSONArray a = new JSONArray(sp.getString("vehicles", "[]"));
            for (int i = 0; i < a.length(); i++) vehicles.add(Vehicle.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        try {
            JSONArray a = new JSONArray(sp.getString("types", "[]"));
            for (int i = 0; i < a.length(); i++) types.add(ServiceType.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        if (types.isEmpty()) types.addAll(ServiceType.defaults());
        if (type(Service.OTHER) == null) types.add(new ServiceType(Service.OTHER, 0, 0));

        boolean fresh = vehicles.isEmpty();
        if (fresh) vehicles.add(new Vehicle("v1", "マイカー"));
        String first = vehicles.get(0).id;
        try {
            JSONArray a = new JSONArray(sp.getString("fills", "[]"));
            for (int i = 0; i < a.length(); i++) allFills.add(Fill.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        try {
            JSONArray a = new JSONArray(sp.getString("services", "[]"));
            for (int i = 0; i < a.length(); i++) allServices.add(Service.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        // Records from before multi-vehicle support belong to the first vehicle.
        for (Fill f : allFills) if (f.vehicle == null) f.vehicle = first;
        for (Service s : allServices) if (s.vehicle == null) s.vehicle = first;

        vehicleId = sp.getString("currentVehicle", first);
        if (vehicle(vehicleId) == null) vehicleId = first;
        if (fresh && (!allFills.isEmpty() || !allServices.isEmpty())) save();
        else recompute();
    }

    public void save() {
        recompute();
        try {
            JSONArray v = new JSONArray();
            for (Vehicle x : vehicles) v.put(x.toJson());
            JSONArray a = new JSONArray();
            for (Fill f : allFills) a.put(f.toJson());
            JSONArray s = new JSONArray();
            for (Service x : allServices) s.put(x.toJson());
            JSONArray t = new JSONArray();
            for (ServiceType x : types) t.put(x.toJson());
            sp.edit().putString("vehicles", v.toString())
                    .putString("types", t.toString())
                    .putString("currentVehicle", vehicleId)
                    .putString("fills", a.toString())
                    .putString("services", s.toString()).apply();
        } catch (Exception ignored) { }
    }

    // ---- vehicles ----

    /** Puts the vehicles in the given id order (ids not listed keep their relative order at the end). */
    public void reorderVehicles(List<String> ids) {
        List<Vehicle> sorted = new ArrayList<>();
        for (String id : ids) {
            Vehicle v = vehicle(id);
            if (v != null && !sorted.contains(v)) sorted.add(v);
        }
        for (Vehicle v : vehicles) if (!sorted.contains(v)) sorted.add(v);
        vehicles.clear();
        vehicles.addAll(sorted);
        save();
    }

    public Vehicle vehicle(String id) {
        for (Vehicle v : vehicles) if (v.id.equals(id)) return v;
        return null;
    }

    public Vehicle vehicle() { return vehicle(vehicleId); }

    public void select(String id) {
        vehicleId = id;
        save();
    }

    /** Registers a new vehicle (its id is assigned here) and makes it the selected one. */
    public Vehicle addVehicle(Vehicle v) {
        v.id = "v" + System.currentTimeMillis();
        vehicles.add(v);
        vehicleId = v.id;
        save();
        return v;
    }

    // ---- maintenance item types ----

    public ServiceType type(String name) {
        for (ServiceType t : types) if (t.name.equals(name)) return t;
        return null;
    }

    public List<String> typeNames() {
        List<String> n = new ArrayList<>();
        for (ServiceType t : types) n.add(t.name);
        return n;
    }

    /** How many records (all vehicles) were logged under this item name. */
    public int countServices(String typeName) {
        int n = 0;
        for (Service s : allServices) if (s.type.equals(typeName)) n++;
        return n;
    }

    /** Renames an item; existing records follow the new name. */
    public void renameType(ServiceType t, String newName) {
        for (Service s : allServices) if (s.type.equals(t.name)) s.type = newName;
        t.name = newName;
    }

    /** Removes an item from the picker. Existing records keep their name as history. */
    public void deleteType(ServiceType t) {
        types.remove(t);
    }

    /** Removes the vehicle and all of its records. The last vehicle cannot be removed. */
    public void deleteVehicle(String id) {
        if (vehicles.size() <= 1) return;
        vehicles.remove(vehicle(id));
        allFills.removeIf(f -> f.vehicle.equals(id));
        allServices.removeIf(s -> s.vehicle.equals(id));
        if (vehicleId.equals(id)) vehicleId = vehicles.get(0).id;
        save();
    }

    // ---- records (for the selected vehicle) ----

    public void add(Fill f) { f.vehicle = vehicleId; allFills.add(f); }
    public void remove(Fill f) { allFills.remove(f); }
    public void add(Service s) { s.vehicle = vehicleId; allServices.add(s); }
    public void remove(Service s) { allServices.remove(s); }

    /** Filter to the selected vehicle; full-to-full economy; services newest first. */
    private void recompute() {
        fills.clear();
        for (Fill f : allFills) if (f.vehicle.equals(vehicleId)) fills.add(f);
        services.clear();
        services.addAll(servicesOf(vehicleId));

        Collections.sort(fills, (x, y) -> Double.compare(x.odo, y.odo));
        Fill prevFull = null;
        double liters = 0;
        for (Fill f : fills) {
            f.economy = -1;
            if (prevFull != null) liters += f.liters;
            if (f.full) {
                if (prevFull != null && liters > 0 && f.odo > prevFull.odo)
                    f.economy = (f.odo - prevFull.odo) / liters;
                prevFull = f;
                liters = 0;
            }
        }
    }

    private List<Service> servicesOf(String vid) {
        List<Service> out = new ArrayList<>();
        for (Service s : allServices) if (s.vehicle.equals(vid)) out.add(s);
        Collections.sort(out, (x, y) -> {
            int c = y.date.compareTo(x.date);
            return c != 0 ? c : Double.compare(y.odo, x.odo);
        });
        return out;
    }

    public double totalCost() {
        double s = 0;
        for (Fill f : fills) s += f.cost;
        return s;
    }

    public double serviceCost() {
        double s = 0;
        for (Service v : services) s += v.cost;
        return s;
    }

    /** Most recent fill (highest odometer), or null. */
    public Fill lastFill() {
        return fills.isEmpty() ? null : fills.get(fills.size() - 1);
    }

    /** Most recent fill that has a measured economy, or null. */
    public Fill lastEconomyFill() {
        for (int i = fills.size() - 1; i >= 0; i--) if (fills.get(i).economy > 0) return fills.get(i);
        return null;
    }

    /** Highest odometer reading seen in any record of the vehicle. */
    public double currentOdo(String vid) {
        double m = 0;
        for (Fill f : allFills) if (f.vehicle.equals(vid)) m = Math.max(m, f.odo);
        for (Service v : allServices) if (v.vehicle.equals(vid)) m = Math.max(m, v.odo);
        return m;
    }

    public double currentOdo() { return currentOdo(vehicleId); }

    /** Average km/L from the first to the last full-tank fill, or -1. */
    public double avgEconomy() {
        Fill first = null, last = null;
        for (Fill f : fills) {
            if (!f.full) continue;
            if (first == null) first = f;
            last = f;
        }
        if (first == null || last == first) return -1;
        double l = 0;
        for (Fill f : fills) if (f.odo > first.odo && f.odo <= last.odo) l += f.liters;
        return l > 0 ? (last.odo - first.odo) / l : -1;
    }

    // ---- maintenance due ----

    /** Upcoming maintenance item derived from the latest record of its kind. */
    public static class Due {
        public Vehicle vehicle;
        public Service s;
        public int level;      // 0 = ok, 1 = soon, 2 = overdue
        public String detail;  // e.g. "あと 300 km / あと 12日"
        /** Days until due (negative = overdue); km converted with the car's average km/day. */
        public double daysLeft = Double.MAX_VALUE;
    }

    /** Used when there is not enough history to estimate a car's daily distance. */
    private static final double DEFAULT_KM_PER_DAY = 30;

    /** Average distance per day from the vehicle's oldest to newest record. */
    private double kmPerDay(String vid) {
        String firstDate = null, lastDate = null;
        double firstOdo = 0, lastOdo = 0;
        List<Object[]> pts = new ArrayList<>();
        for (Fill f : allFills) if (f.vehicle.equals(vid)) pts.add(new Object[]{f.date, f.odo});
        for (Service s : allServices) if (s.vehicle.equals(vid)) pts.add(new Object[]{s.date, s.odo});
        for (Object[] p : pts) {
            String d = (String) p[0];
            double o = (Double) p[1];
            if (firstDate == null || d.compareTo(firstDate) < 0) { firstDate = d; firstOdo = o; }
            if (lastDate == null || d.compareTo(lastDate) > 0) { lastDate = d; lastOdo = o; }
        }
        if (firstDate == null) return DEFAULT_KM_PER_DAY;
        long days = ChronoUnit.DAYS.between(LocalDate.parse(firstDate), LocalDate.parse(lastDate));
        double km = lastOdo - firstOdo;
        return days >= 30 && km > 0 ? km / days : DEFAULT_KM_PER_DAY;
    }

    /** The newest record per key; only these drive reminders. */
    public boolean isLatest(Service s) {
        for (Service v : services) if (v.key().equals(s.key())) return v == s;
        return false;
    }

    public List<Due> dueItems(LocalDate today) { return dueItems(vehicleId, today); }

    /** Due status for every kind with an interval on one vehicle, most urgent first. */
    public List<Due> dueItems(String vid, LocalDate today) {
        Map<String, Service> latest = new LinkedHashMap<>();
        for (Service v : servicesOf(vid)) if (!latest.containsKey(v.key())) latest.put(v.key(), v);
        double cur = currentOdo(vid);
        double perDay = kmPerDay(vid);
        List<Due> out = new ArrayList<>();
        for (Service s : latest.values()) {
            if (s.intervalKm <= 0 && s.intervalMonths <= 0) continue;
            Due d = new Due();
            d.vehicle = vehicle(vid);
            d.s = s;
            List<String> parts = new ArrayList<>();
            if (s.intervalKm > 0) {
                double left = s.nextOdo() - cur;
                parts.add(left >= 0 ? String.format(Locale.JAPAN, "あと %,.0f km", left)
                        : String.format(Locale.JAPAN, "%,.0f km 超過", -left));
                d.level = Math.max(d.level, left < 0 ? 2 : left <= SOON_KM ? 1 : 0);
                d.daysLeft = Math.min(d.daysLeft, left / perDay);
            }
            if (s.intervalMonths > 0) {
                long days = ChronoUnit.DAYS.between(today, s.nextDate());
                parts.add(days >= 0 ? "あと " + days + "日(" + s.nextDate() + ")"
                        : (-days) + "日 超過(" + s.nextDate() + ")");
                d.level = Math.max(d.level, days < 0 ? 2 : days <= SOON_DAYS ? 1 : 0);
                d.daysLeft = Math.min(d.daysLeft, days);
            }
            d.detail = String.join(" / ", parts);
            out.add(d);
        }
        sortByUrgency(out);
        return out;
    }

    /** Overdue or soon items across the vehicles still owned, most urgent first. */
    public List<Due> alerts(LocalDate today) {
        List<Due> out = new ArrayList<>();
        for (Vehicle v : vehicles) {
            if (!v.owned) continue;
            for (Due d : dueItems(v.id, today)) if (d.level > 0) out.add(d);
        }
        sortByUrgency(out);
        return out;
    }

    private static void sortByUrgency(List<Due> list) {
        Collections.sort(list, (a, b) -> a.level != b.level ? b.level - a.level
                : Double.compare(a.daysLeft, b.daysLeft));
    }
}
