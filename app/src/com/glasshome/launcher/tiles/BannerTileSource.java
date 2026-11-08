package com.glasshome.launcher.tiles;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

/** Il banner TV (16:9) che l'app dichiara per i launcher Android TV / Fire TV. */
public final class BannerTileSource implements TileSource {
    public static final String ID = "banner";

    @Override public String id() { return ID; }
    @Override public String label() { return null; } // è l'"originale"

    @Override
    public Bitmap load(Context c, String pkg, int w, int h) {
        Drawable d = banner(c.getPackageManager(), pkg);
        if (d == null || d.getIntrinsicWidth() <= 0 || d.getIntrinsicHeight() <= 0) return null;
        // Proporzioni del banner (di solito 320x180), poi ritaglio 16:9 sulla tessera.
        int bw = Math.max(w, d.getIntrinsicWidth()), bh = Math.round(bw * (float) d.getIntrinsicHeight() / d.getIntrinsicWidth());
        Bitmap raw = Bitmaps.render(d, bw, bh);
        // Quasi 16:9: riempie la tessera. Altrimenti il banner resta intero, sul colore del suo bordo.
        float ra = bw / (float) bh, rt = w / (float) h;
        Bitmap out = Math.abs(ra - rt) / rt < 0.06f ? Bitmaps.centerCrop(raw, w, h, 0xFF1C1C1E)
                : Bitmaps.fitCenter(raw, w, h, Bitmaps.edgeColor(raw));
        raw.recycle();
        return out;
    }

    static Drawable banner(PackageManager pm, String pkg) {
        try {
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i != null) {
                Drawable d = pm.getActivityBanner(i);
                if (d != null) return d;
            }
            return pm.getApplicationBanner(pkg);
        } catch (Exception e) {
            return null;
        }
    }
}
