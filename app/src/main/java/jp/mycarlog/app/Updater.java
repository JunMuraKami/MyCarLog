package jp.mycarlog.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Checks GitHub Releases for a newer version and downloads / installs its APK. */
public final class Updater {
    private static final String API = "https://api.github.com/repos/JunMuraKami/MyCarLog/releases/latest";
    private static final long CHECK_INTERVAL = 12L * 60 * 60 * 1000;
    private static final String APK_MIME = "application/vnd.android.package-archive";

    public static final class Info {
        public String version = "", url = "", notes = "";
    }

    public interface Callback {
        /** latest: the newer release, or null when up to date; ok: false if the check failed. */
        void done(Info latest, boolean ok);
    }

    private Updater() { }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("update", Context.MODE_PRIVATE);
    }

    public static String current(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** True if version a is newer than b (dotted numbers, optional leading "v"). */
    static boolean newer(String a, String b) {
        int[] x = parse(a), y = parse(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int[] parse(String v) {
        String[] parts = v.trim().replaceFirst("^[vV]", "").split("\\.");
        int[] r = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                r[i] = Integer.parseInt(parts[i].replaceAll("\\D.*$", ""));
            } catch (NumberFormatException e) {
                r[i] = 0;
            }
        }
        return r;
    }

    /** The newer release found by an earlier check, or null. */
    public static Info cached(Context c) {
        SharedPreferences p = sp(c);
        String v = p.getString("version", "");
        if (v.isEmpty() || p.getString("url", "").isEmpty() || !newer(v, current(c))) return null;
        Info i = new Info();
        i.version = v.replaceFirst("^[vV]", "");
        i.url = p.getString("url", "");
        i.notes = p.getString("notes", "");
        return i;
    }

    public static boolean due(Context c) {
        return System.currentTimeMillis() - sp(c).getLong("lastCheck", 0) > CHECK_INTERVAL;
    }

    public static void check(final Context ctx, final Callback cb) {
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            Info found = null;
            boolean ok = false;
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(API).openConnection();
                h.setConnectTimeout(10000);
                h.setReadTimeout(10000);
                h.setRequestProperty("Accept", "application/vnd.github+json");
                if (h.getResponseCode() == 200) {
                    String body;
                    try (InputStream is = h.getInputStream()) {
                        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                        body = new String(bo.toByteArray(), StandardCharsets.UTF_8);
                    }
                    JSONObject o = new JSONObject(body);
                    String tag = o.optString("tag_name", "");
                    String url = "";
                    JSONArray assets = o.optJSONArray("assets");
                    if (assets != null)
                        for (int k = 0; k < assets.length(); k++) {
                            JSONObject a = assets.getJSONObject(k);
                            if (a.optString("name").endsWith(".apk")) {
                                url = a.optString("browser_download_url");
                                break;
                            }
                        }
                    ok = !tag.isEmpty();
                    if (ok) {
                        sp(c).edit().putString("version", tag).putString("url", url)
                                .putString("notes", o.optString("body", ""))
                                .putLong("lastCheck", System.currentTimeMillis()).apply();
                        if (!url.isEmpty() && newer(tag, current(c))) found = cached(c);
                    }
                }
            } catch (Exception e) {
                ok = false;
            }
            final Info result = found;
            final boolean good = ok;
            new Handler(Looper.getMainLooper()).post(() -> cb.done(result, good));
        }).start();
    }

    /** Asks whether to update, then downloads and installs the APK. */
    public static void prompt(final Activity a, final Info info) {
        String notes = info.notes.trim();
        if (notes.length() > 400) notes = notes.substring(0, 400) + "…";
        new AlertDialog.Builder(a)
                .setTitle("新しいバージョン " + info.version)
                .setMessage("現在のバージョン: " + current(a) + (notes.isEmpty() ? "" : "\n\n" + notes))
                .setNegativeButton("あとで", null)
                .setPositiveButton("更新", (x, w) -> download(a, info))
                .show();
    }

    private static void download(Activity a, Info info) {
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(a, "「このアプリからのインストールを許可」をオンにして、もう一度更新してください",
                    Toast.LENGTH_LONG).show();
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + a.getPackageName())));
            return;
        }
        final Context c = a.getApplicationContext();
        final DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(info.url))
                .setTitle("MyCarLog " + info.version)
                .setMimeType(APK_MIME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(c, null, "MyCarLog-" + info.version + ".apk");
        final long id = dm.enqueue(r);
        Toast.makeText(a, "ダウンロードを開始しました", Toast.LENGTH_SHORT).show();
        c.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return;
                try {
                    ctx.unregisterReceiver(this);
                } catch (Exception ignored) { }
                Uri u = dm.getUriForDownloadedFile(id);
                if (u == null) {
                    Toast.makeText(ctx, "ダウンロードに失敗しました", Toast.LENGTH_LONG).show();
                    return;
                }
                ctx.startActivity(new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(u, APK_MIME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION));
            }
        }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
    }
}
