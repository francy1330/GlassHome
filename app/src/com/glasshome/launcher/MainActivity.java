package com.glasshome.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewTreeObserver;

/**
 * GlassHome come app: la home in una finestra sua. Vedi HomeScreen per tutto il resto.
 * Nessun FLAG_KEEP_SCREEN_ON: lo screensaver di sistema parte normalmente sopra questa finestra
 * e, quando termina, si torna qui.
 */
public class MainActivity extends Activity {
    HomeScreen screen;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        screen = new HomeScreen(this);
        screen.applyExtras(getIntent());
        setContentView(screen);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        screen.applyExtras(intent);
        if (intent.getBooleanExtra("home", false)) screen.onShown(true); // tasto Home: menu chiusi, si torna al dock
    }

    @Override
    protected void onResume() {
        super.onResume();
        HomeService.pausedUntil = 0;
        screen.onShown(false);
        // Quando questa finestra ha disegnato, l'overlay sopra la home Amazon può farsi da parte.
        screen.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                screen.getViewTreeObserver().removeOnPreDrawListener(this);
                screen.postDelayed(HomeService::appShown, 150);
                return true;
            }
        });
    }

    /**
     * La finestra torna ad avere il focus (es. l'overlay del servizio si è chiuso dopo un menu):
     * recupera le modifiche fatte nell'altra copia della home (app nascoste, dock, tessere...).
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) screen.onShown(false);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // In background con poca memoria: le tessere si rigenerano quando servono.
        if (level >= TRIM_MEMORY_BACKGROUND) com.glasshome.launcher.tiles.TileRepository.get(this).clear();
    }

    /** Fire TV si avvia sulla home Amazon: GlassHome si apre appena il sistema ha finito l'avvio o dopo un aggiornamento. */
    public static class Boot extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}
