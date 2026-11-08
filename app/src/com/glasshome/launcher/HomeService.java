package com.glasshome.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Il tasto Home (da FireFrame, MIT). Fire TV non permette a un'altra app di essere la home e il
 * launcher Amazon non si può disattivare (e qui NON lo tocchiamo). Quindi questo servizio:
 *  1. appena si apre la home Amazon (Home, o uscendo da un'app) mostra GlassHome in una finestra
 *     overlay di accessibilità, che non subisce il ritardo di 5 s imposto agli avvii dopo Home;
 *  2. avvia anche l'app GlassHome; quando ha disegnato, l'overlay si fa da parte (stessa schermata);
 *  3. un'app scelta sull'overlay in quei 5 s viene aperta "premendo" la sua tessera sul launcher
 *     Amazon nascosto dietro: gli avvii di Amazon non vengono trattenuti.
 *
 * Convivenza con lo screensaver di sistema (in più rispetto a FireFrame):
 *  - le finestre overlay di accessibilità stanno SOPRA lo screensaver. Se l'overlay fosse visibile
 *    quando parte (es. con un menu aperto), lo coprirebbe. Con ACTION_DREAMING_STARTED (e con lo
 *    schermo spento) l'overlay si nasconde subito e niente lo rimostra finché lo screensaver è attivo;
 *  - con ACTION_DREAMING_STOPPED e ACTION_SCREEN_ON, se sotto è rimasta la home Amazon, GlassHome
 *    torna in primo piano (idea presa dal launcher di dumbtv);
 *  - l'overlay non usa FLAG_KEEP_SCREEN_ON, il servizio non prende wakelock e non intercetta tasti:
 *    il timer di inattività di Android resta quello di sempre.
 */
public class HomeService extends AccessibilityService {
    static final String TAG = "GlassHome";
    static final String AMAZON_HOME = "com.amazon.tv.launcher";
    static final int SHOWN = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN;
    static final int HIDDEN = SHOWN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

    // Finestre che compaiono sopra senza sostituire ciò che c'è davanti (tastiera, system UI,
    // screensaver): non cambiano lo stato "c'è la home Amazon".
    static final Set<String> IGNORED = new HashSet<>(Arrays.asList(
            "android", "com.android.systemui", "com.amazon.tv.ime",
            "com.amazon.ftv.screensaver", "com.amazon.bueller.photos"));

    // "Home Amazon" e "Impostazioni Fire TV" sospendono la copertura finché non si preme Home o si
    // apre un'altra app.
    static long pausedUntil, ignoreHomeUntil;
    static boolean forSettings, settingsShown;
    static HomeService running;

    WindowManager wm;
    WindowManager.LayoutParams lp;
    HomeScreen overlay;
    boolean added, showing, hideWhenMenusClose;
    boolean dreaming, screenOff, onAmazonHome;
    String dreamPackage;
    final Runnable hideNow = this::hide;

    // Il launcher Amazon lo invia (senza protezione) dal suo goHome: arriva prima dell'evento di accessibilità.
    final BroadcastReceiver homePressed = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (SystemClock.elapsedRealtime() < ignoreHomeUntil) return; // l'abbiamo aperta noi
            pausedUntil = 0;
            onAmazonHome = true;
            cover("HOME_PRESSED");
        }
    };

    final BroadcastReceiver power = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = String.valueOf(i.getAction());
            switch (a) {
                case Intent.ACTION_DREAMING_STARTED:
                    dreaming = true;
                    Log.i(TAG, "screensaver avviato: overlay " + (showing ? "nascosto" : "già nascosto"));
                    hideForSystem();
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    screenOff = true;
                    hideForSystem();
                    break;
                case Intent.ACTION_DREAMING_STOPPED:
                    dreaming = false;
                    Log.i(TAG, "screensaver terminato, home Amazon sotto: " + onAmazonHome);
                    if (onAmazonHome) cover("DREAMING_STOPPED");
                    break;
                case Intent.ACTION_SCREEN_ON:
                    screenOff = false;
                    Log.i(TAG, "schermo acceso, home Amazon sotto: " + onAmazonHome);
                    if (onAmazonHome) cover("SCREEN_ON");
                    break;
            }
        }
    };

    static void pause(boolean settings) {
        long now = SystemClock.elapsedRealtime();
        pausedUntil = now + 10 * 60 * 1000;
        ignoreHomeUntil = now + 2000;
        forSettings = settings;
        settingsShown = false;
        hideOverlay();
    }

    static boolean isSettingsPackage(String p) {
        return p.startsWith("com.amazon.tv.settings") || p.equals("com.android.settings");
    }

    static boolean paused() {
        return SystemClock.elapsedRealtime() < pausedUntil;
    }

    /** L'app GlassHome è davanti e disegnata: l'overlay può andare, se non ha menu aperti. */
    static void appShown() {
        HomeService s = running;
        if (s == null) return;
        s.onAmazonHome = false;
        if (!s.showing) return;
        if (s.overlay.layers.isEmpty() && s.overlay.moving < 0) s.hide();
        else s.hideWhenMenusClose = true;
    }

    static void menusClosed() {
        HomeService s = running;
        if (s != null && s.hideWhenMenusClose) s.hide();
    }

    static void hideOverlay() {
        if (running != null) running.hide();
    }

    @Override
    protected void onServiceConnected() {
        running = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new HomeScreen(this);
        overlay.setVisibility(View.GONE);
        // Finestra sempre attaccata ma nascosta, così mostrarla è istantaneo. NESSUN FLAG_KEEP_SCREEN_ON.
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, HIDDEN, PixelFormat.OPAQUE);
        lp.windowAnimations = 0;
        attach();
        registerReceiver(homePressed, new IntentFilter("com.amazon.tv.launcher.HOME_PRESSED"));
        IntentFilter f = new IntentFilter(Intent.ACTION_DREAMING_STARTED);
        f.addAction(Intent.ACTION_DREAMING_STOPPED);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(power, f);
        String comp = Settings.Secure.getString(getContentResolver(), "screensaver_components"); // solo lettura
        if (comp != null && comp.contains("/")) dreamPackage = comp.substring(0, comp.indexOf('/'));
        Log.i(TAG, "servizio Home attivo, screensaver di sistema: " + comp);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        try { unregisterReceiver(homePressed); } catch (RuntimeException ignored) {}
        try { unregisterReceiver(power); } catch (RuntimeException ignored) {}
        if (added) wm.removeViewImmediate(overlay);
        added = false;
        running = null;
        return super.onUnbind(intent);
    }

    boolean attach() {
        if (added) return true;
        try {
            wm.addView(overlay, lp);
            added = true;
        } catch (RuntimeException e) {
            added = false;
        }
        return added;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        CharSequence pkg = e.getPackageName();
        if (pkg == null) return;
        String p = pkg.toString();
        if (p.equals(AMAZON_HOME)) {
            // Le schermate delle impostazioni Fire TV stanno nel launcher Amazon: mai coprirle.
            String cls = String.valueOf(e.getClassName());
            if (cls.contains("Settings")) {
                onAmazonHome = false;
                if (forSettings) settingsShown = true;
                return;
            }
            if (settingsShown && cls.contains("HomeActivity")) pausedUntil = 0; // Back dalle impostazioni
            onAmazonHome = true;
            if (findUntil > SystemClock.elapsedRealtime() && cls.contains("HomeActivity")) clickFind(0);
            cover("finestra Amazon " + cls);
        } else if (IGNORED.contains(p) || p.equals(dreamPackage)) {
            // sopra, non al posto di
        } else if (p.equals(getPackageName())) {
            onAmazonHome = false;
        } else {
            onAmazonHome = false;
            if (!isSettingsPackage(p)) pausedUntil = 0; // si è aperta un'altra app
            if (overlay != null && overlay.apps.containsKey(p)) Recents.record(this, p);
            if (showing) {
                // L'app riceve subito il telecomando, ma la copriamo finché non ha disegnato.
                setFocusable(false);
                overlay.postDelayed(hideNow, 600);
            }
        }
    }

    /** È comparsa la home Amazon: la copriamo e portiamo davanti l'app GlassHome. */
    void cover(String why) {
        if (paused()) return;
        if (dreaming || screenOff) {
            Log.i(TAG, "copertura rimandata (" + why + "): screensaver o schermo spento");
            return; // mai sopra lo screensaver: ci pensano DREAMING_STOPPED / SCREEN_ON
        }
        show();
        startActivity(new Intent(this, MainActivity.class).putExtra("home", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    void show() {
        if (overlay == null || dreaming || screenOff || !attach()) return;
        overlay.removeCallbacks(hideNow);
        boolean appearing = !showing; // Home di nuovo: torna al dock, menu chiusi
        if (appearing) {
            showing = true;
            hideWhenMenusClose = false;
            overlay.setVisibility(View.VISIBLE);
        }
        setFocusable(true);
        overlay.onShown(appearing);
    }

    void hide() {
        if (overlay == null || !showing || !added) return;
        overlay.removeCallbacks(hideNow);
        showing = false;
        hideWhenMenusClose = false;
        overlay.setVisibility(View.GONE);
        setFocusable(false);
    }

    /** Screensaver o schermo spento: via l'overlay subito, menu compresi. */
    void hideForSystem() {
        if (overlay == null) return;
        overlay.closeAllLayers();
        overlay.endMove(true);
        hide();
    }

    void setFocusable(boolean focusable) {
        int flags = focusable ? SHOWN : HIDDEN;
        if (lp.flags == flags) return;
        lp.flags = flags;
        if (added) wm.updateViewLayout(overlay, lp);
    }

    // ---- "Cerca": la ricerca di Fire TV ("Trova") vive nella home Amazon ----

    static final String[] FIND_LABELS = {"trova", "cerca", "ricerca", "find", "search"};
    long findUntil;

    /**
     * Apre la home Amazon senza coprirla e preme la voce "Trova" della sua barra.
     * Il tasto Home riporta a GlassHome come sempre. false se non c'è nemmeno la home Amazon.
     */
    static boolean openAmazonFind(Context c) {
        pause(false);
        HomeService s = running;
        if (s != null) s.findUntil = SystemClock.elapsedRealtime() + 8000;
        try {
            c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setPackage(AMAZON_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            return false;
        }
        if (s != null) s.overlay.postDelayed(() -> s.clickFind(0), 400);
        return true;
    }

    void clickFind(final int attempt) {
        if (findUntil < SystemClock.elapsedRealtime()) return;
        for (AccessibilityWindowInfo w : getWindows()) {
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null || !AMAZON_HOME.contentEquals(root.getPackageName())) continue;
            // La voce "Trova" della barra. Il campo "Cerca" sotto ignora i comandi di accessibilità
            // (su Fire OS 7.7 la tastiera si apre solo con OK dal telecomando): ci si arriva con ▼.
            AccessibilityNodeInfo n = findExact(root, true);
            if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                findUntil = 0;
                Log.i(TAG, "ricerca Amazon aperta");
                return;
            }
        }
        if (attempt < 20) overlay.postDelayed(() -> clickFind(attempt + 1), 250);

    }


    /** byDescription: la voce della barra (content-desc); altrimenti il campo con il testo "Cerca". */
    static AccessibilityNodeInfo findExact(AccessibilityNodeInfo n, boolean byDescription) {
        CharSequence d = byDescription ? n.getContentDescription() : n.getText();
        if (d != null && (n.isClickable() || !byDescription)) {
            String s = d.toString().trim().toLowerCase();
            for (String l : FIND_LABELS) if (s.equals(l)) return clickableSelfOrParent(n);
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c == null) continue;
            AccessibilityNodeInfo hit = findExact(c, byDescription);
            if (hit != null) return hit;
        }
        return null;
    }

    static AccessibilityNodeInfo clickableSelfOrParent(AccessibilityNodeInfo n) {
        for (AccessibilityNodeInfo p = n; p != null; p = p.getParent()) if (p.isClickable()) return p;
        return n;
    }

    // Apre un'app premendone la tessera sul launcher Amazon, dietro l'overlay. Ci prova per circa
    // un secondo, poi ripiega su un avvio normale.
    void launchThroughAmazon(final String label, final Runnable otherwise, final int attempt) {
        if (pressAmazonTile(label)) return;
        if (showing && attempt < 6) overlay.postDelayed(() -> launchThroughAmazon(label, otherwise, attempt + 1), 150);
        else otherwise.run();
    }

    boolean pressAmazonTile(String label) {
        if (!showing || label == null) return false;
        for (AccessibilityWindowInfo w : getWindows()) {
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null || !AMAZON_HOME.contentEquals(root.getPackageName())) continue;
            AccessibilityNodeInfo tile = find(root, label.toLowerCase());
            if (tile != null && tile.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }
        return false;
    }

    static AccessibilityNodeInfo find(AccessibilityNodeInfo n, String label) {
        CharSequence d = n.getContentDescription();
        if (n.isClickable() && d != null && d.toString().toLowerCase().startsWith(label)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c == null) continue;
            AccessibilityNodeInfo hit = find(c, label);
            if (hit != null) return hit;
        }
        return null;
    }

    @Override
    public void onInterrupt() {}
}
