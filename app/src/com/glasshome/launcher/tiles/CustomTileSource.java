package com.glasshome.launcher.tiles;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Tessere personalizzate: PNG 16:9 salvate nei file del launcher, una per package
 * (files/tiles/&lt;package&gt;.png). Arrivano dal PC con scripts/tile.sh (o tile.ps1) e, in futuro,
 * da altre sorgenti che vogliono rendere permanente il loro risultato.
 */
public final class CustomTileSource implements TileSource {
    public static final String ID = "custom";
    static final int STORE_W = 640, STORE_H = 360;

    @Override public String id() { return ID; }
    @Override public String label() { return null; } // si sceglie con "Carica dal PC"

    @Override
    public Bitmap load(Context c, String pkg, int w, int h) {
        File f = file(c, pkg);
        if (!f.exists()) return null;
        Bitmap b = Bitmaps.decodeFile(f, w, h);
        if (b == null) return null;
        Bitmap out = Bitmaps.centerCrop(b, w, h, 0xFF000000);
        b.recycle();
        return out;
    }

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "tiles");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    public static File file(Context c, String pkg) {
        return new File(dir(c), pkg.replaceAll("[^A-Za-z0-9._-]", "_") + ".png");
    }

    public static boolean exists(Context c, String pkg) {
        return file(c, pkg).exists();
    }

    public static boolean delete(Context c, String pkg) {
        return file(c, pkg).delete();
    }

    /** Importa un'immagine qualsiasi (es. da /data/local/tmp), normalizzata a PNG 640x360. */
    public static boolean importFrom(Context c, String pkg, String path) {
        Bitmap b = Bitmaps.decodeFile(new File(path), STORE_W, STORE_H);
        if (b == null) return false;
        return save(c, pkg, b);
    }

    /** Salva una tessera come personalizzata (ritagliata 16:9 a 640x360). */
    public static boolean save(Context c, String pkg, Bitmap src) {
        Bitmap norm = Bitmaps.centerCrop(src, STORE_W, STORE_H, 0xFF000000);
        File f = file(c, pkg), tmp = new File(f.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            norm.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            tmp.delete();
            return false;
        } finally {
            norm.recycle();
        }
        return tmp.renameTo(f) && BitmapFactory.decodeFile(f.getPath()) != null;
    }
}
