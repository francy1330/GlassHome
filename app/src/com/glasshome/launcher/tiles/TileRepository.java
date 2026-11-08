package com.glasshome.launcher.tiles;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.LruCache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Decide quale tessera mostrare per ogni app e la prepara in background.
 *
 * Ordine predefinito: tessera personalizzata (file) → banner TV dell'app → smart tile generata.
 * Ogni app ha una "modalità" salvata:
 *   ""          ordine predefinito (dopo "Ripristina originale" o un caricamento dal PC)
 *   "smart"     solo smart tile ("Automatica")
 *   altro id    una sorgente scelta dal menu (es. la futura "Genera con AI"), poi l'ordine predefinito
 */
public final class TileRepository {
    public static final String MODE_DEFAULT = "";

    /** Tutte le sorgenti, in ordine di priorità. Una nuova sorgente si aggiunge qui. */
    static final List<TileSource> SOURCES = Arrays.asList(
            new CustomTileSource(),
            new BannerTileSource(),
            new SmartTileSource()
            // new AiTileSource(lanEndpoint)   ← fase 2: tessere generate da un servizio sul PC
    );

    public interface Callback { void onTile(Bitmap tile); }

    private static TileRepository instance;

    /** Aumenta a ogni cambio di tessera: le due copie della home (app e overlay) si aggiornano. */
    public static int version;

    private final Context app;
    private final SharedPreferences modes;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            r.run();
        }, "glasshome-tiles");
        t.setDaemon(true);
        return t;
    });
    // ~10 MB di tessere in memoria: un centinaio di tessere RGB_565 alla dimensione dello schermo.
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(10 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    private TileRepository(Context c) {
        app = c.getApplicationContext();
        modes = app.getSharedPreferences("tile_modes", Context.MODE_PRIVATE);
    }

    public static synchronized TileRepository get(Context c) {
        if (instance == null) instance = new TileRepository(c);
        return instance;
    }

    /** La tessera pronta in memoria, se c'è (per disegnarla subito senza attese). */
    public Bitmap cached(String pkg, int w, int h) {
        return cache.get(key(pkg, w, h));
    }

    /** Prepara la tessera in background e la consegna sul thread UI. */
    public void request(final String pkg, final int w, final int h, final Callback cb) {
        final String key = key(pkg, w, h);
        Bitmap hit = cache.get(key);
        if (hit != null) { cb.onTile(hit); return; }
        final List<TileSource> chain = chain(pkg);
        worker.execute(() -> {
            Bitmap b = null;
            for (TileSource s : chain) {
                try {
                    b = s.load(app, pkg, w, h);
                } catch (Throwable ignored) {
                    b = null; // una sorgente che fallisce non blocca le altre
                }
                if (b != null) break;
            }
            if (b == null) b = SmartTileSource.letterTile(pkg, w, h);
            final Bitmap tile = b;
            main.post(() -> {
                cache.put(key, tile);
                cb.onTile(tile);
            });
        });
    }

    public String mode(String pkg) {
        return modes.getString(pkg, MODE_DEFAULT);
    }

    public void setMode(String pkg, String mode) {
        modes.edit().putString(pkg, mode).apply();
        invalidate(pkg);
    }

    /** "Ripristina originale": via la tessera personalizzata e la modalità scelta. */
    public void restoreOriginal(String pkg) {
        CustomTileSource.delete(app, pkg);
        modes.edit().remove(pkg).apply();
        invalidate(pkg);
    }

    /** Una tessera arrivata dal PC (scripts/tile.sh): diventa quella dell'app. */
    public boolean importCustom(String pkg, String path) {
        if (!CustomTileSource.importFrom(app, pkg, path)) return false;
        modes.edit().remove(pkg).apply();
        invalidate(pkg);
        return true;
    }

    /** Le sorgenti che compaiono come voci in più nel menu "Sistema icona". */
    public List<TileSource> selectableSources() {
        List<TileSource> out = new ArrayList<>();
        for (TileSource s : SOURCES) if (s.label() != null) out.add(s);
        return out;
    }

    public void invalidate(String pkg) {
        String prefix = pkg + "@";
        for (String k : cache.snapshot().keySet()) if (k.startsWith(prefix)) cache.remove(k);
        version++;
    }

    /** Svuota la cache in memoria (le view tengono le tessere che stanno mostrando). */
    public void clear() {
        cache.evictAll();
    }

    List<TileSource> chain(String pkg) {
        String mode = mode(pkg);
        List<TileSource> out = new ArrayList<>();
        if (SmartTileSource.ID.equals(mode)) {
            out.add(byId(SmartTileSource.ID));
            return out;
        }
        TileSource forced = mode.isEmpty() ? null : byId(mode);
        if (forced != null) out.add(forced);
        for (TileSource s : SOURCES) if (s.label() == null && !out.contains(s)) out.add(s);
        return out;
    }

    static TileSource byId(String id) {
        for (TileSource s : SOURCES) if (s.id().equals(id)) return s;
        return null;
    }

    String key(String pkg, int w, int h) {
        return pkg + "@" + w + "x" + h + "#" + mode(pkg);
    }
}
