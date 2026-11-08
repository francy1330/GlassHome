package com.glasshome.launcher;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Una tessera 16:9 con il focus "alla tvOS":
 *  - si solleva con una molla (scala ~1.10, leggero rimbalzo) e proietta un'ombra morbida;
 *  - parallasse: entrando col D-pad si inclina nel verso del movimento e torna dritta;
 *  - riflesso speculare: una banda di luce diagonale che scorre sulla tessera quando prende il focus
 *    e si sposta con la parallasse.
 * Tutte le animazioni sono brevi e partono solo da un tasto premuto: a riposo non gira niente,
 * così la CPU dorme e lo screensaver parte regolarmente.
 */
final class TileView extends FrameLayout {
    /** Molla smorzata: 1 - e^(-6t)·cos(12t). Rimbalzo ~2% sulla scala, assestata a fine durata. */
    static final TimeInterpolator SPRING = t -> (float) (1 - Math.exp(-6 * t) * Math.cos(12 * t));

    final ImageView image;
    final View shine;
    final float lift, liftZ, tilt;
    float radius;
    boolean moving;

    TileView(Context c, float radius, float lift, float liftZ) {
        super(c);
        this.lift = lift;
        this.liftZ = liftZ;
        this.tilt = 5f;
        this.radius = radius;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setBackgroundColor(0xFF1C1C1E);

        image = new ImageView(c);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(image, new LayoutParams(-1, -1));

        shine = new View(c);
        shine.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0x00FFFFFF, 0x10FFFFFF, 0x3DFFFFFF, 0x10FFFFFF, 0x00FFFFFF}));
        shine.setAlpha(0);
        addView(shine, new LayoutParams(-1, -1));

        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), TileView.this.radius);
            }
        });
        setClipToOutline(true);
        setOutlineSpotShadowColor(0xFF000000);
        setOutlineAmbientShadowColor(0xFF000000);
        setCameraDistance(9000 * getResources().getDisplayMetrics().density);
    }

    void setTile(Bitmap b) {
        image.setImageBitmap(b);
    }

    void setRadius(float r) {
        radius = r;
        invalidateOutline();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        // La banda di luce è larga il doppio della tessera e parte fuori, a sinistra.
        LayoutParams lp = (LayoutParams) shine.getLayoutParams();
        if (lp.width != w * 2) {
            lp.width = w * 2;
            shine.setLayoutParams(lp);
        }
        shine.setTranslationX(-w * 2f);
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, Rect previous) {
        super.onFocusChanged(gained, direction, previous);
        animateFocus(gained, HomeScreen.lastDirX);
    }

    void animateFocus(boolean gained, int dirX) {
        int w = Math.max(1, getWidth());
        float scale = gained ? (moving ? lift + 0.04f : lift) : 1f;
        animate().cancel();
        if (gained && dirX != 0) {
            // Entra inclinata nel verso del movimento, poi la molla la raddrizza.
            setRotationY(-dirX * tilt);
            image.setTranslationX(dirX * w * 0.015f);
        }
        animate().scaleX(scale).scaleY(scale).translationZ(gained ? liftZ : 0).rotationY(0)
                .setInterpolator(SPRING).setDuration(gained ? 520 : 260).start();
        image.animate().cancel();
        image.animate().translationX(0).setInterpolator(SPRING).setDuration(520).start();

        shine.animate().cancel();
        if (gained && !moving) {
            shine.setTranslationX(-w * 2f);
            shine.animate().alpha(1).translationX(-w * 0.9f).setDuration(620)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        } else {
            shine.animate().alpha(0).setDuration(180).start();
        }
    }

    /** Il D-pad spinge contro un bordo (niente in quella direzione): piccola inclinazione e ritorno. */
    void nudge(int dirX, int dirY) {
        int w = Math.max(1, getWidth());
        setRotationY(-dirX * tilt * 1.4f);
        setRotationX(dirY * tilt);
        animate().cancel();
        float scale = isFocused() ? (moving ? lift + 0.04f : lift) : 1f;
        animate().rotationY(0).rotationX(0).scaleX(scale).scaleY(scale)
                .setInterpolator(SPRING).setDuration(560).start();
        if (!moving) {
            float rest = -w * 0.9f;
            shine.setTranslationX(rest + dirX * w * 0.18f);
            shine.animate().cancel();
            shine.animate().translationX(rest).setInterpolator(SPRING).setDuration(560).start();
        }
    }

    void setMoving(boolean m) {
        moving = m;
        animateFocus(isFocused(), 0);
    }
}
