package com.glasshome.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import com.glasshome.launcher.tiles.CustomTileSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Properties;

/**
 * Backup della configurazione (dock, app nascoste, tessere, sfondo, opzioni, città del meteo).
 *  - export: scrive tutto in files/backup sulla memoria esterna dell'app, da dove
 *    scripts/backup-config scarica la cartella sul computer;
 *  - import: legge una cartella copiata da scripts/restore-config in /data/local/tmp.
 * Formato: config.properties ("preferenze.tipo.chiave=valore"), tiles/*.png, wallpaper.jpg.
 */
final class ConfigBackup {
    static final String[] PREFS = {"glasshome", "tile_modes", "weather"};
    static final String[] WEATHER_KEEP = {"lat", "lon", "city"}; // dei dati meteo basta la città

    private ConfigBackup() {}

    static File exportDir(Context c) {
        File base = c.getExternalFilesDir(null);
        return base == null ? null : new File(base, "backup");
    }

    /** Restituisce la cartella scritta, o null. */
    static File export(Context c) {
        File dir = exportDir(c);
        if (dir == null) return null;
        deleteTree(dir);
        File tilesOut = new File(dir, "tiles");
        if (!tilesOut.mkdirs()) return null;
        Properties p = new Properties();
        for (String name : PREFS) {
            for (Map.Entry<String, ?> e : c.getSharedPreferences(name, Context.MODE_PRIVATE).getAll().entrySet()) {
                if (name.equals("weather") && !java.util.Arrays.asList(WEATHER_KEEP).contains(e.getKey())) continue;
                Object v = e.getValue();
                String type = v instanceof Integer ? "i" : v instanceof Boolean ? "b" : v instanceof Long ? "l" : v instanceof Float ? "f" : "s";
                p.setProperty(name + "." + type + "." + e.getKey(), String.valueOf(v));
            }
        }
        try (OutputStream out = new FileOutputStream(new File(dir, "config.properties"))) {
            p.store(out, "GlassHome - configurazione");
        } catch (Exception e) {
            return null;
        }
        File[] tiles = CustomTileSource.dir(c).listFiles();
        if (tiles != null) for (File t : tiles) if (t.getName().endsWith(".png")) copy(t, new File(tilesOut, t.getName()));
        File wall = wallpaper(c);
        if (wall != null && wall.exists()) copy(wall, new File(dir, "wallpaper.jpg"));
        return dir;
    }

    /** Importa da una cartella; restituisce il numero di impostazioni ripristinate, o -1. */
    static int importFrom(Context c, String path) {
        File dir = new File(path);
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(new File(dir, "config.properties"))) {
            p.load(in);
        } catch (Exception e) {
            return -1;
        }
        int n = 0;
        for (String name : PREFS) {
            SharedPreferences.Editor ed = c.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear();
            for (String k : p.stringPropertyNames()) {
                if (!k.startsWith(name + ".")) continue;
                String rest = k.substring(name.length() + 1);
                int dot = rest.indexOf('.');
                if (dot < 0) continue;
                String type = rest.substring(0, dot), key = rest.substring(dot + 1), v = p.getProperty(k);
                try {
                    switch (type) {
                        case "i": ed.putInt(key, Integer.parseInt(v)); break;
                        case "b": ed.putBoolean(key, Boolean.parseBoolean(v)); break;
                        case "l": ed.putLong(key, Long.parseLong(v)); break;
                        case "f": ed.putFloat(key, Float.parseFloat(v)); break;
                        default: ed.putString(key, v);
                    }
                    n++;
                } catch (NumberFormatException ignored) {}
            }
            ed.commit();
        }
        File[] tiles = new File(dir, "tiles").listFiles();
        File[] old = CustomTileSource.dir(c).listFiles();
        if (old != null) for (File t : old) t.delete();
        if (tiles != null) for (File t : tiles) if (t.getName().endsWith(".png")) copy(t, new File(CustomTileSource.dir(c), t.getName()));
        File wall = new File(dir, "wallpaper.jpg"), target = wallpaper(c);
        if (wall.exists() && target != null) copy(wall, target);
        return n;
    }

    static File wallpaper(Context c) {
        File d = c.getExternalFilesDir(null);
        return d == null ? null : new File(d, "wallpaper.jpg");
    }

    static boolean copy(File from, File to) {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
