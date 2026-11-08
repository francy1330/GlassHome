package com.glasshome.launcher.tiles;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/**
 * "Smart tile": una tessera 16:9 generata dall'icona dell'app.
 *  - toglie i bordi trasparenti e, se l'icona è un logo su una "piastrella" di colore pieno,
 *    toglie anche la piastrella (flood fill dal bordo), così il logo galleggia sulla tessera;
 *  - lo sfondo a tutta tessera è il colore della piastrella o, se non c'è, il colore dominante
 *    del logo scurito, con una leggera sfumatura diagonale;
 *  - il logo è centrato. Gli angoli arrotondati li applica il launcher, uguali per tutte le tessere.
 */
public final class SmartTileSource implements TileSource {
    public static final String ID = "smart";
    static final int S = 192; // risoluzione di lavoro dell'icona

    @Override public String id() { return ID; }
    @Override public String label() { return null; } // è la voce "Automatica"

    @Override
    public Bitmap load(Context c, String pkg, int w, int h) {
        PackageManager pm = c.getPackageManager();
        String label = pkg;
        Drawable icon = null;
        try {
            label = pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
            icon = pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException ignored) {}

        int[] px = null;
        Integer plate = null;
        if (icon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
            if (a.getBackground() != null) {
                int[] bg = pixels(layer(a.getBackground()));
                plate = uniformColor(bg, new Rect(0, 0, S, S));
            }
            if (a.getForeground() != null) px = pixels(layer(a.getForeground()));
        } else if (icon != null) {
            px = pixels(Bitmaps.render(icon, S, S));
        }
        Rect box = px == null ? null : trim(px);
        if (box == null || box.width() < 4 || box.height() < 4) return letterTile(label, w, h);

        if (plate == null) {
            Integer ring = ringColor(px, box);
            // Icona quadrata piena ma non di un colore solo (sfumature, foto): diventa lei stessa lo
            // sfondo, sfocata a tutta tessera, così il "quadrato" non si vede come una cornice.
            if (ring == null && ringOpacity(px, box) >= 0.9f) return fullBleed(px, box, w, h);
            if (ring != null) {
                int[] copy = px.clone();
                flood(px, box, ring);
                Rect after = trim(px);
                if (after != null && opaqueCount(px) > S * S * 0.02f) {
                    plate = ring;
                    box = after;
                } else {
                    px = copy; // il "logo" era tutta l'icona: la teniamo intera, sul suo colore
                    plate = ring;
                }
            }
        }

        int base = plate != null ? plate : deepen(dominant(px, box));
        Bitmap out = Bitmaps.opaque(w, h);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, shade(base, 1.14f, 0.05f), shade(base, 0.78f, 0f), Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, 0, 0, h * 0.6f, 0x16FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);

        Bitmap logo = Bitmap.createBitmap(px, S, S, Bitmap.Config.ARGB_8888);
        float maxW = w * 0.46f, maxH = h * 0.56f;
        float k = Math.min(maxW / box.width(), maxH / box.height());
        float lw = box.width() * k, lh = box.height() * k;
        RectF dst = new RectF((w - lw) / 2f, (h - lh) / 2f, (w + lw) / 2f, (h + lh) / 2f);
        if (plate == null) p.setShadowLayer(h * 0.03f, 0, h * 0.01f, 0x40000000);
        cv.drawBitmap(logo, box, dst, p);
        logo.recycle();
        return out;
    }

    // ---- Analisi dell'icona ----

    static Bitmap layer(Drawable d) {
        // I livelli adattivi sono 108dp di cui 72 visibili: li disegniamo 1.5x, centrati.
        Bitmap b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int e = S / 4;
        d.setBounds(-e, -e, S + e, S + e);
        d.draw(c);
        return b;
    }

    static int[] pixels(Bitmap b) {
        int[] a = new int[S * S];
        b.getPixels(a, 0, S, 0, 0, S, S);
        b.recycle();
        return a;
    }

    /** Il rettangolo che contiene i pixel non trasparenti, o null. */
    static Rect trim(int[] px) {
        int l = S, t = S, r = -1, b = -1;
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++)
                if ((px[y * S + x] >>> 24) > 24) {
                    if (x < l) l = x;
                    if (x > r) r = x;
                    if (y < t) t = y;
                    if (y > b) b = y;
                }
        return r < 0 ? null : new Rect(l, t, r + 1, b + 1);
    }

    static int opaqueCount(int[] px) {
        int n = 0;
        for (int c : px) if ((c >>> 24) > 24) n++;
        return n;
    }

    /** Il colore del bordo dell'icona, se è una "piastrella" uniforme e piena. */
    static Integer ringColor(int[] px, Rect box) {
        int t = Math.max(2, Math.round(Math.min(box.width(), box.height()) * 0.04f));
        long r = 0, g = 0, b = 0;
        int total = 0, opaque = 0;
        for (int y = box.top; y < box.bottom; y++)
            for (int x = box.left; x < box.right; x++) {
                boolean edge = x < box.left + t || x >= box.right - t || y < box.top + t || y >= box.bottom - t;
                if (!edge) continue;
                total++;
                int c = px[y * S + x];
                if ((c >>> 24) < 200) continue;
                opaque++;
                r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255;
            }
        if (total == 0 || opaque < total * 0.6f) return null;
        int avg = Color.rgb((int) (r / opaque), (int) (g / opaque), (int) (b / opaque));
        int similar = 0;
        for (int y = box.top; y < box.bottom; y++)
            for (int x = box.left; x < box.right; x++) {
                boolean edge = x < box.left + t || x >= box.right - t || y < box.top + t || y >= box.bottom - t;
                int c = px[y * S + x];
                if (edge && (c >>> 24) >= 200 && dist(c, avg) < 36) similar++;
            }
        return similar >= opaque * 0.85f ? avg : null;
    }

    /** Quota di pixel opachi sul bordo dell'icona ritagliata. */
    static float ringOpacity(int[] px, Rect box) {
        int t = Math.max(2, Math.round(Math.min(box.width(), box.height()) * 0.04f));
        int total = 0, opaque = 0;
        for (int y = box.top; y < box.bottom; y++)
            for (int x = box.left; x < box.right; x++) {
                if (!(x < box.left + t || x >= box.right - t || y < box.top + t || y >= box.bottom - t)) continue;
                total++;
                if ((px[y * S + x] >>> 24) >= 200) opaque++;
            }
        return total == 0 ? 0 : opaque / (float) total;
    }

    /** Sfondo = l'icona sfocata a tutta tessera; sopra, l'icona con angoli morbidi e ombra. */
    static Bitmap fullBleed(int[] px, Rect box, int w, int h) {
        Bitmap all = Bitmap.createBitmap(px, S, S, Bitmap.Config.ARGB_8888);
        Bitmap icon = Bitmap.createBitmap(all, box.left, box.top, box.width(), box.height());
        Bitmap out = Bitmaps.blurredFill(icon, w, h, 0x38000000);
        Canvas c = new Canvas(out);
        float ih = h * 0.62f, iw = ih * box.width() / box.height();
        RectF dst = new RectF((w - iw) / 2f, (h - ih) / 2f, (w + iw) / 2f, (h + ih) / 2f);
        float r = Math.min(iw, ih) * 0.2f;
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0xFF000000);
        shadow.setShadowLayer(h * 0.05f, 0, h * 0.02f, 0x66000000);
        c.drawRoundRect(dst, r, r, shadow);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        android.graphics.BitmapShader sh = new android.graphics.BitmapShader(icon, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setRectToRect(new RectF(0, 0, icon.getWidth(), icon.getHeight()), dst, android.graphics.Matrix.ScaleToFit.FILL);
        sh.setLocalMatrix(m);
        p.setShader(sh);
        c.drawRoundRect(dst, r, r, p);
        icon.recycle();
        all.recycle();
        return out;
    }

    /** Il colore medio se l'immagine è quasi uniforme, altrimenti null. */
    static Integer uniformColor(int[] px, Rect box) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int y = box.top; y < box.bottom; y += 2)
            for (int x = box.left; x < box.right; x += 2) {
                int c = px[y * S + x];
                if ((c >>> 24) < 200) continue;
                r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; n++;
            }
        if (n == 0) return null;
        int avg = Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
        int off = 0;
        for (int y = box.top; y < box.bottom; y += 2)
            for (int x = box.left; x < box.right; x += 2) {
                int c = px[y * S + x];
                if ((c >>> 24) >= 200 && dist(c, avg) > 40) off++;
            }
        return off < n * 0.1f ? avg : null;
    }

    /** Rende trasparente la piastrella: tutto ciò che, dal bordo, è trasparente o del suo colore. */
    static void flood(int[] px, Rect box, int plate) {
        int[] queue = new int[S * S];
        boolean[] seen = new boolean[S * S];
        int head = 0, tail = 0;
        for (int x = box.left; x < box.right; x++) {
            tail = push(px, seen, queue, tail, x, box.top, plate);
            tail = push(px, seen, queue, tail, x, box.bottom - 1, plate);
        }
        for (int y = box.top; y < box.bottom; y++) {
            tail = push(px, seen, queue, tail, box.left, y, plate);
            tail = push(px, seen, queue, tail, box.right - 1, y, plate);
        }
        while (head < tail) {
            int i = queue[head++];
            px[i] = 0;
            int x = i % S, y = i / S;
            if (x > box.left) tail = push(px, seen, queue, tail, x - 1, y, plate);
            if (x < box.right - 1) tail = push(px, seen, queue, tail, x + 1, y, plate);
            if (y > box.top) tail = push(px, seen, queue, tail, x, y - 1, plate);
            if (y < box.bottom - 1) tail = push(px, seen, queue, tail, x, y + 1, plate);
        }
    }

    static int push(int[] px, boolean[] seen, int[] queue, int tail, int x, int y, int plate) {
        int i = y * S + x;
        if (seen[i]) return tail;
        int c = px[i];
        if ((c >>> 24) <= 24 || dist(c, plate) < 48) {
            seen[i] = true;
            queue[tail++] = i;
        }
        return tail;
    }

    /** Il colore più rappresentativo del logo, preferendo i colori saturi al bianco e al nero. */
    static int dominant(int[] px, Rect box) {
        int[] count = new int[4096];
        float[] weight = new float[4096];
        long[] sr = new long[4096], sg = new long[4096], sb = new long[4096];
        float[] hsv = new float[3];
        for (int y = box.top; y < box.bottom; y++)
            for (int x = box.left; x < box.right; x++) {
                int c = px[y * S + x];
                if ((c >>> 24) < 160) continue;
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                int k = (r >> 4) << 8 | (g >> 4) << 4 | (b >> 4);
                Color.RGBToHSV(r, g, b, hsv);
                float wgt = (hsv[1] < 0.15f || hsv[2] < 0.12f) ? 0.25f : 1f + 3f * hsv[1];
                count[k]++;
                weight[k] += wgt;
                sr[k] += r; sg[k] += g; sb[k] += b;
            }
        int best = -1;
        for (int k = 0; k < 4096; k++) if (count[k] > 0 && (best < 0 || weight[k] > weight[best])) best = k;
        if (best < 0) return 0xFF2C2C30;
        return Color.rgb((int) (sr[best] / count[best]), (int) (sg[best] / count[best]), (int) (sb[best] / count[best]));
    }

    /** Il colore dominante scurito per fare da sfondo al logo stesso. */
    static int deepen(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        if (hsv[1] < 0.12f) return 0xFF2C2C30; // logo grigio/bianco/nero: sfondo grafite
        hsv[1] = Math.min(1f, hsv[1] * 1.05f);
        hsv[2] = Math.min(hsv[2], 0.40f);
        return Color.HSVToColor(hsv);
    }

    static int shade(int c, float mul, float add) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[2] = Math.max(0f, Math.min(1f, hsv[2] * mul + add));
        return Color.HSVToColor(hsv);
    }

    static int dist(int a, int b) {
        int dr = ((a >> 16) & 255) - ((b >> 16) & 255);
        int dg = ((a >> 8) & 255) - ((b >> 8) & 255);
        int db = (a & 255) - (b & 255);
        return (int) Math.sqrt(dr * dr * 0.3 + dg * dg * 0.59 + db * db * 0.11) * 2;
    }

    /** Ultima risorsa: l'iniziale del nome su grafite. */
    static Bitmap letterTile(String label, int w, int h) {
        Bitmap out = Bitmaps.opaque(w, h);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF3A3A40, 0xFF1E1E22, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(0xFFFFFFFF);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(h * 0.42f);
        p.setTextAlign(Paint.Align.CENTER);
        String s = label == null || label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase();
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(s, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, p);
        return out;
    }
}
