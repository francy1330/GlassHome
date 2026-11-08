package com.glasshome.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Meteo da Open-Meteo (gratuito, senza chiave, solo HTTPS). La città si imposta dal computer con
 * scripts/weather.sh: viene geocodificata una volta, poi si salvano solo le coordinate.
 * Nessun timer in background: l'aggiornamento parte solo quando la home si mostra ed è più
 * vecchio di 30 minuti, su un thread a bassa priorità.
 */
final class Weather {
    static final long FRESH_MS = 30 * 60 * 1000, STALE_MS = 6 * 60 * 60 * 1000;
    static volatile boolean busy;
    static int version; // aumenta quando arrivano dati nuovi

    final SharedPreferences prefs;

    Weather(Context c) {
        prefs = c.getApplicationContext().getSharedPreferences("weather", Context.MODE_PRIVATE);
    }

    boolean configured() { return prefs.contains("lat"); }
    String city() { return prefs.getString("city", ""); }
    boolean hasFresh() {
        return configured() && prefs.contains("temp") && System.currentTimeMillis() - prefs.getLong("at", 0) < STALE_MS;
    }
    int temp() { return Math.round(prefs.getFloat("temp", 0)); }
    int code() { return prefs.getInt("code", 0); }
    boolean day() { return prefs.getBoolean("day", true); }

    void disable() {
        prefs.edit().clear().apply();
        version++;
    }

    interface Done { void done(String message); }

    /** Cerca la città (es. "Roma") e poi scarica il meteo. Il messaggio arriva sul thread UI. */
    void setCity(final String name, final Done done) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            String msg;
            try {
                JSONObject r = get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=it&format=json&name="
                        + URLEncoder.encode(name, "UTF-8"));
                JSONArray res = r.optJSONArray("results");
                if (res == null || res.length() == 0) {
                    msg = "Meteo: città \"" + name + "\" non trovata";
                } else {
                    JSONObject g = res.getJSONObject(0);
                    String label = g.optString("name", name);
                    String region = g.optString("admin1", "");
                    prefs.edit().putFloat("lat", (float) g.getDouble("latitude")).putFloat("lon", (float) g.getDouble("longitude"))
                            .putString("city", label).remove("at").apply();
                    fetchNow();
                    msg = "Meteo: " + label + (region.isEmpty() ? "" : ", " + region);
                }
            } catch (Exception e) {
                msg = "Meteo: nessuna connessione a internet";
            }
            final String m = msg;
            main.post(() -> { version++; if (done != null) done.done(m); });
        }, "glasshome-weather").start();
    }

    /** Aggiorna se servono dati nuovi; onUpdate parte sul thread UI solo se sono cambiati. */
    void refreshIfStale(final Runnable onUpdate) {
        if (!configured() || busy || System.currentTimeMillis() - prefs.getLong("at", 0) < FRESH_MS) return;
        busy = true;
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            boolean ok = fetchNow();
            busy = false;
            if (ok) main.post(() -> { version++; onUpdate.run(); });
        }, "glasshome-weather").start();
    }

    boolean fetchNow() {
        try {
            JSONObject r = get("https://api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code,is_day&timezone=auto"
                    + "&latitude=" + prefs.getFloat("lat", 0) + "&longitude=" + prefs.getFloat("lon", 0));
            JSONObject cur = r.getJSONObject("current");
            prefs.edit().putFloat("temp", (float) cur.getDouble("temperature_2m")).putInt("code", cur.getInt("weather_code"))
                    .putBoolean("day", cur.optInt("is_day", 1) == 1).putLong("at", System.currentTimeMillis()).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static JSONObject get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "GlassHome-FireTV");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new JSONObject(out.toString("UTF-8"));
        } finally {
            c.disconnect();
        }
    }

    /** Descrizione breve in italiano del codice WMO. */
    static String describe(int code) {
        if (code == 0) return "sereno";
        if (code <= 2) return "poco nuvoloso";
        if (code == 3) return "coperto";
        if (code == 45 || code == 48) return "nebbia";
        if (code <= 57) return "pioviggine";
        if (code <= 67) return "pioggia";
        if (code <= 77) return "neve";
        if (code <= 82) return "rovesci";
        if (code <= 86) return "rovesci di neve";
        return "temporale";
    }
}
