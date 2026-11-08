package com.glasshome.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.FrameLayout;

/**
 * Vetro simulato senza blur in tempo reale (RenderEffect richiede API 31, qui siamo a 28):
 * lo sfondo viene ridotto a 1/8 e sfocato UNA volta con StackBlur in background; ogni pannello
 * ne disegna il ritaglio esattamente dietro di sé, poi tinta, riflesso diagonale e bordo chiaro.
 */
final class Glass {
    private Glass() {}

    /** Chi fornisce lo sfondo sfocato e la sua posizione sullo schermo. */
    interface Backdrop {
        Bitmap blur();
        int screenW();
        int screenH();
        float wallOffsetY(); // parallasse verticale dello sfondo
    }

    /** Lo sfondo ridotto a 1/8 e sfocato. */
    static Bitmap blurred(Bitmap photo) {
        int w = Math.max(1, photo.getWidth() / 8), h = Math.max(1, photo.getHeight() / 8);
        Bitmap small = Bitmap.createScaledBitmap(photo, w, h, true);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        if (small != photo) small.recycle();
        com.glasshome.launcher.tiles.Bitmaps.stackBlur(px, w, h, 10);
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    /** Un pannello di vetro: contiene altre view, disegna il vetro dietro di loro. */
    static class Panel extends FrameLayout {
        final Backdrop backdrop;
        final float radius;
        final Paint photo = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF rect = new RectF();
        final Matrix m = new Matrix();
        final int[] loc = new int[2];
        Bitmap shaderFor;

        Panel(Context c, Backdrop backdrop, float radius, int tintColor) {
            super(c);
            this.backdrop = backdrop;
            this.radius = radius;
            tint.setColor(tintColor);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(1f); // 1px reale
            setWillNotDraw(false);
            setClipChildren(false);
            setClipToPadding(false);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            // Bordo: più chiaro in alto, quasi invisibile in basso. Riflesso: diagonale dall'alto a sinistra.
            edge.setShader(new LinearGradient(0, 0, 0, h, 0x66FFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP));
            sheen.setShader(new LinearGradient(0, 0, w * 0.55f, h * 1.1f,
                    new int[] {0x1EFFFFFF, 0x08FFFFFF, 0x00FFFFFF}, new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            rect.set(0, 0, getWidth(), getHeight());
            Bitmap blur = backdrop.blur();
            if (blur != null) {
                if (shaderFor != blur) {
                    photo.setShader(new BitmapShader(blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                    shaderFor = blur;
                }
                // Stesso center-crop dello sfondo, spostato nella posizione di questo pannello.
                int sw = backdrop.screenW(), sh = backdrop.screenH();
                float k = Math.max((float) sw / blur.getWidth(), (float) sh / blur.getHeight());
                getLocationInWindow(loc);
                m.setScale(k, k);
                m.postTranslate((sw - blur.getWidth() * k) / 2f - loc[0],
                        (sh - blur.getHeight() * k) / 2f - loc[1] + backdrop.wallOffsetY());
                photo.getShader().setLocalMatrix(m);
                c.drawRoundRect(rect, radius, radius, photo);
            }
            c.drawRoundRect(rect, radius, radius, tint);
            c.drawRoundRect(rect, radius, radius, sheen);
            if (radius > 0) {
                rect.inset(0.5f, 0.5f);
                c.drawRoundRect(rect, radius, radius, edge);
            }
        }
    }
}
