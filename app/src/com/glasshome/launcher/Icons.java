package com.glasshome.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;

/** Icone semplici disegnate in codice (niente risorse in più): lente, Wi-Fi, rete via cavo, meteo. */
final class Icons {
    private Icons() {}

    static Paint stroke(float w, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        return p;
    }

    static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }

    static Bitmap search(int s) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = stroke(s * 0.11f, 0xFFFFFFFF);
        c.drawCircle(s * 0.42f, s * 0.42f, s * 0.27f, p);
        c.drawLine(s * 0.62f, s * 0.62f, s * 0.86f, s * 0.86f, p);
        return b;
    }

    /** level 0..4 tacche; -1 = nessuna rete (Wi-Fi barrato). */
    static Bitmap wifi(int s, int level) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float cx = s / 2f, cy = s * 0.80f;
        Paint dot = fill(0xFFFFFFFF);
        for (int i = 1; i <= 3; i++) {
            float r = s * 0.22f * i;
            Paint p = stroke(s * 0.09f, level >= i + 1 ? 0xFFFFFFFF : 0x55FFFFFF);
            c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), 225, 90, false, p);
        }
        dot.setColor(level >= 1 ? 0xFFFFFFFF : 0x55FFFFFF);
        c.drawCircle(cx, cy, s * 0.07f, dot);
        if (level < 0) {
            Paint x = stroke(s * 0.09f, 0xFFFF6B6B);
            c.drawLine(s * 0.18f, s * 0.18f, s * 0.82f, s * 0.86f, x);
        }
        return b;
    }

    static Bitmap ethernet(int s) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = stroke(s * 0.08f, 0xFFFFFFFF);
        c.drawRoundRect(new RectF(s * 0.2f, s * 0.28f, s * 0.8f, s * 0.72f), s * 0.06f, s * 0.06f, p);
        for (int i = 0; i < 3; i++) {
            float x = s * (0.36f + i * 0.14f);
            c.drawLine(x, s * 0.42f, x, s * 0.56f, p);
        }
        c.drawLine(s * 0.5f, s * 0.72f, s * 0.5f, s * 0.86f, p);
        return b;
    }

    /** Icona meteo dal codice WMO di Open-Meteo. */
    static Bitmap weather(int s, int code, boolean day) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (code == 0) { sky(c, s, s * 0.5f, s * 0.5f, s * 0.22f, day); return b; }
        if (code == 1 || code == 2) {
            sky(c, s, s * 0.36f, s * 0.34f, s * 0.15f, day);
            cloud(c, s, s * 0.08f, 0xFFFFFFFF);
            return b;
        }
        if (code == 45 || code == 48) {
            Paint p = stroke(s * 0.08f, 0xFFFFFFFF);
            for (int i = 0; i < 3; i++) c.drawLine(s * 0.18f + i * s * 0.05f, s * (0.35f + i * 0.16f), s * 0.82f - i * s * 0.05f, s * (0.35f + i * 0.16f), p);
            return b;
        }
        cloud(c, s, -s * 0.08f, 0xFFFFFFFF);
        Paint p = stroke(s * 0.07f, 0xFF9BD1FF);
        if (code >= 95) {
            Path bolt = new Path();
            bolt.moveTo(s * 0.55f, s * 0.58f); bolt.lineTo(s * 0.40f, s * 0.78f); bolt.lineTo(s * 0.52f, s * 0.78f);
            bolt.lineTo(s * 0.44f, s * 0.95f); bolt.lineTo(s * 0.64f, s * 0.70f); bolt.lineTo(s * 0.52f, s * 0.70f); bolt.close();
            c.drawPath(bolt, fill(0xFFFFD54F));
        } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            Paint d = fill(0xFFFFFFFF);
            for (int i = 0; i < 3; i++) c.drawCircle(s * (0.32f + i * 0.18f), s * (0.76f + (i % 2) * 0.1f), s * 0.05f, d);
        } else if (code >= 51) {
            for (int i = 0; i < 3; i++) {
                float x = s * (0.34f + i * 0.17f);
                c.drawLine(x, s * 0.66f, x - s * 0.06f, s * 0.86f, p);
            }
        }
        return b;
    }

    private static void sky(Canvas c, int s, float cx, float cy, float r, boolean day) {
        if (day) {
            Paint sun = fill(0xFFFFD54F);
            c.drawCircle(cx, cy, r, sun);
            Paint ray = stroke(s * 0.06f, 0xFFFFD54F);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                c.drawLine(cx + (float) Math.cos(a) * r * 1.45f, cy + (float) Math.sin(a) * r * 1.45f,
                        cx + (float) Math.cos(a) * r * 1.85f, cy + (float) Math.sin(a) * r * 1.85f, ray);
            }
        } else {
            int save = c.saveLayer(0, 0, s, s, null);
            c.drawCircle(cx, cy, r * 1.2f, fill(0xFFE8E6F0));
            Paint cut = fill(0);
            cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            c.drawCircle(cx + r * 0.6f, cy - r * 0.45f, r * 1.0f, cut);
            c.restoreToCount(save);
        }
    }

    private static void cloud(Canvas c, int s, float dy, int color) {
        Paint p = fill(color);
        c.drawCircle(s * 0.38f, s * 0.58f + dy, s * 0.16f, p);
        c.drawCircle(s * 0.56f, s * 0.50f + dy, s * 0.20f, p);
        c.drawCircle(s * 0.72f, s * 0.62f + dy, s * 0.13f, p);
        c.drawRoundRect(new RectF(s * 0.22f, s * 0.58f + dy, s * 0.85f, s * 0.75f + dy), s * 0.08f, s * 0.08f, p);
    }
}
