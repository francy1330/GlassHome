package com.glasshome.launcher.tiles;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import java.io.File;

/** Piccoli aiuti per bitmap, pensati per una chiavetta con poca memoria. */
public final class Bitmaps {
    private Bitmaps() {}

    /** Tessere opache: RGB_565 dimezza la memoria rispetto ad ARGB_8888. */
    public static Bitmap opaque(int w, int h) {
        return Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
    }

    /** Decodifica un file abbastanza grande da coprire w x h, senza caricarlo a piena risoluzione. */
    public static Bitmap decodeFile(File f, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= w && o.outHeight / (o.inSampleSize * 2) >= h) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    /** Disegna src ritagliata al centro (center-crop) su una nuova tessera opaca w x h. */
    public static Bitmap centerCrop(Bitmap src, int w, int h, int background) {
        Bitmap out = opaque(w, h);
        Canvas c = new Canvas(out);
        c.drawColor(background);
        float k = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
        Matrix m = new Matrix();
        m.setScale(k, k);
        m.postTranslate((w - src.getWidth() * k) / 2f, (h - src.getHeight() * k) / 2f);
        c.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return out;
    }

    /** Un drawable (icona o banner) disegnato su una bitmap ARGB w x h. */
    public static Bitmap render(Drawable d, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        return b;
    }

    /** Disegna src intera (fit-center) su una tessera opaca w x h, con lo sfondo dato. */
    public static Bitmap fitCenter(Bitmap src, int w, int h, int background) {
        Bitmap out = opaque(w, h);
        Canvas c = new Canvas(out);
        c.drawColor(background);
        float k = Math.min((float) w / src.getWidth(), (float) h / src.getHeight());
        Matrix m = new Matrix();
        m.setScale(k, k);
        m.postTranslate((w - src.getWidth() * k) / 2f, (h - src.getHeight() * k) / 2f);
        c.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return out;
    }

    /** Il colore medio del bordo (1 pixel) di un'immagine, opaco. */
    public static int edgeColor(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        long r = 0, g = 0, bl = 0;
        int n = 0;
        int[] row = new int[Math.max(w, h)];
        for (int pass = 0; pass < 4; pass++) {
            int len = pass < 2 ? w : h;
            if (pass == 0) b.getPixels(row, 0, w, 0, 0, w, 1);
            else if (pass == 1) b.getPixels(row, 0, w, 0, h - 1, w, 1);
            else if (pass == 2) b.getPixels(row, 0, 1, 0, 0, 1, h);
            else b.getPixels(row, 0, 1, w - 1, 0, 1, h);
            for (int i = 0; i < len; i++) {
                int c = row[i];
                if ((c >>> 24) < 128) continue;
                r += (c >> 16) & 255; g += (c >> 8) & 255; bl += c & 255; n++;
            }
        }
        if (n == 0) return 0xFF1C1C1E;
        return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (bl / n);
    }

    /** src stirata a tutta tessera, ridotta e sfocata: uno sfondo che "continua" l'immagine. */
    public static Bitmap blurredFill(Bitmap src, int w, int h, int dim) {
        int sw = Math.max(8, w / 8), sh = Math.max(8, h / 8);
        Bitmap small = centerCrop(src, sw, sh, 0xFF000000);
        int[] px = new int[sw * sh];
        small.getPixels(px, 0, sw, 0, 0, sw, sh);
        small.recycle();
        stackBlur(px, sw, sh, Math.max(2, sw / 10));
        Bitmap soft = Bitmap.createBitmap(px, sw, sh, Bitmap.Config.ARGB_8888);
        Bitmap out = opaque(w, h);
        Canvas c = new Canvas(out);
        c.drawBitmap(soft, null, new android.graphics.Rect(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
        soft.recycle();
        c.drawColor(dim);
        return out;
    }

    /** StackBlur di Mario Klingemann (versione Java classica), su pixel ARGB opachi. */
    public static void stackBlur(int[] pix, int w, int h, int radius) {
        if (radius < 1) return;
        int wm = w - 1, hm = h - 1, wh = w * h, div = radius + radius + 1;
        int[] r = new int[wh], g = new int[wh], b = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int[] vmin = new int[Math.max(w, h)];
        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int[] dv = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) dv[i] = i / divsum;
        yw = yi = 0;
        int[][] stack = new int[div][3];
        int stackpointer, stackstart, rbs, r1 = radius + 1;
        int[] sir;
        int routsum, goutsum, boutsum, rinsum, ginsum, binsum;
        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16; sir[1] = (p & 0x00ff00) >> 8; sir[2] = p & 0x0000ff;
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs; gsum += sir[1] * rbs; bsum += sir[2] * rbs;
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; }
                else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; }
            }
            stackpointer = radius;
            for (x = 0; x < w; x++) {
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum];
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum;
                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2];
                if (y == 0) vmin[x] = Math.min(x + radius + 1, wm);
                p = pix[yw + vmin[x]];
                sir[0] = (p & 0xff0000) >> 16; sir[1] = (p & 0x00ff00) >> 8; sir[2] = p & 0x0000ff;
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
                rsum += rinsum; gsum += ginsum; bsum += binsum;
                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer % div];
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2];
                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;
                sir = stack[i + radius];
                sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi];
                rbs = r1 - Math.abs(i);
                rsum += r[yi] * rbs; gsum += g[yi] * rbs; bsum += b[yi] * rbs;
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; }
                else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; }
                if (i < hm) yp += w;
            }
            yi = x;
            stackpointer = radius;
            for (y = 0; y < h; y++) {
                pix[yi] = 0xff000000 | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum;
                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2];
                if (x == 0) vmin[y] = Math.min(y + r1, hm) * w;
                p = x + vmin[y];
                sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p];
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
                rsum += rinsum; gsum += ginsum; bsum += binsum;
                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2];
                yi += w;
            }
        }
    }
}
