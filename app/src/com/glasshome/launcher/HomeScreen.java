package com.glasshome.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.LinearGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.glasshome.launcher.tiles.CustomTileSource;
import com.glasshome.launcher.tiles.SmartTileSource;
import com.glasshome.launcher.tiles.TileRepository;
import com.glasshome.launcher.tiles.TileSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tutta la home come un'unica view, in stile tvOS:
 *  - "Top Shelf": la foto occupa la parte alta (~56%) e sfuma verso il basso;
 *  - il dock in vetro con le app preferite, e sotto il cassetto con tutte le app (scorrendo giù
 *    la foto sale con una leggera parallasse, come su tvOS);
 *  - barra superiore minimal: Impostazioni Fire TV, Home Amazon, Opzioni; orologio e data.
 * MainActivity la mostra come app; HomeService ne tiene una seconda copia nell'overlay
 * (vedi HomeService per il perché).
 *
 * Niente keepScreenOn, wakelock o animazioni infinite: l'orologio si aggiorna una volta al minuto
 * e solo quando la view è visibile.
 */
public class HomeScreen extends FrameLayout implements Glass.Backdrop {
    // Dimensioni di progetto a 1920x1080, scalate sullo schermo reale.
    static final int HERO_H = 600, PANEL_H = 296;
    static final int DOCK_W = 336, DOCK_H = 189, DOCK_GAP = 36;
    static final int GRID_W = 300, GRID_H = 169, GRID_COLS = 5, SIDE = 96;
    static final float LIFT = 1.10f;
    static final int[] RADII = {0, 8, 14, 20, 28, 36};

    static final String[] DEFAULT_DOCK = {
        "com.netflix.ninja",
        "com.amazon.firetv.youtube", "com.google.android.youtube.tv",
        "com.disney.disneyplus",
        "com.amazon.amazonvideo.livingroom",
        "com.spotify.tv.android",
        "com.wbd.stream", "com.hbo.hbonow",
        "com.apple.atve.amazon.appletv",
        "com.plexapp.android", "org.jellyfin.androidtv", "org.xbmc.kodi",
        "com.amazon.bueller.photos",
    };

    // Altri launcher e componenti di Fire OS che si presentano come app: mai mostrati.
    static final Set<String> HIDDEN = new HashSet<>(Arrays.asList(
        HomeService.AMAZON_HOME, "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
        "com.google.android.leanbacklauncher", "com.amazon.tv.website_launcher",
        "com.amazon.ftv.profilepicker", "com.amazon.ftv.screensaver", "com.amazon.ssm",
        "com.amazon.vizzini.ftvcds", "com.android.providers.downloads.ui"));

    // Sfondi inclusi: il cosmo visto da Webb e Hubble (NASA Image Library), 1920x1080. Con
    // "Sfondo del giorno" GlassHome passa al successivo ogni giorno, al primo ritorno alla home.
    static final String[][] WALLPAPERS = {
        {"cosmiccliffs", "Cosmic Cliffs, Nebulosa della Carena · James Webb, NASA/ESA/CSA/STScI"},
        {"pillars", "Pilastri della Creazione, Nebulosa Aquila (infrarosso) · Hubble, NASA/ESA"},
        {"deepfield", "Hubble Ultra Deep Field: migliaia di galassie · Hubble, NASA/ESA"},
        {"lagoon", "Il cuore della Nebulosa Laguna · Hubble, NASA/ESA"},
        {"mysticmountain", "Mystic Mountain, Nebulosa della Carena · Hubble, NASA/ESA"},
        {"crab", "Nebulosa del Granchio · Hubble, NASA/ESA"},
        {"bubble", "Nebulosa Bolla · Hubble, NASA/ESA"},
        {"horsehead", "Nebulosa Testa di Cavallo (infrarosso) · Hubble, NASA/ESA"},
        {"earthrise", "Earthrise · Apollo 17, NASA"},
    };
    static final String CUSTOM = "custom";

    /** Ultima direzione orizzontale/verticale del D-pad: serve alla parallasse delle tessere. */
    static int lastDirX, lastDirY;

    // Stato condiviso tra le due copie (app e overlay), nello stesso processo.
    static int dockVersion, photoVersion, lastDock;
    static String cachedKey;
    static Bitmap cachedPhoto, cachedBlur;

    final Context ctx;
    final SharedPreferences prefs;
    final PackageManager pm;
    final TileRepository tiles;
    final List<ViewGroup> layers = new ArrayList<>();
    final DecelerateInterpolator ease = new DecelerateInterpolator(2f);
    final int screenW, screenH;
    final float s;
    final Typeface medium, semibold;

    ImageView wallView;
    View topScrim;
    ShelfScroll shelf;
    FrameLayout base;
    LinearLayout content, dockRow, topRow;
    DockScroll dockScroll;
    Glass.Panel dockPanel;
    GridLayout grid;
    LinearLayout recentsRow;
    TextView recentsTitle;
    ImageView wifiIcon, weatherIcon;
    TextView weatherText;
    TextView heroTitle, heroHint, credit, clock, date, caption, drawerTitle;

    Bitmap blur;
    List<String> dock = new ArrayList<>();
    Map<String, String> apps = new LinkedHashMap<>();
    boolean appsChanged;
    String shownWallpaper;
    int seenDockVersion = -1, seenPhotoVersion, seenTileVersion = -1, seenRadius = -1;
    int seenRecents = -1, seenWeather = -1, wifiLevel = -2;
    final Weather weather;
    int moving = -1; // indice nel dock dell'app che si sta spostando
    boolean ticking;

    final Runnable tick = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            clock.setText(DateFormat.format(DateFormat.is24HourFormat(ctx) ? "H:mm" : "h:mm", now));
            String d = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now).toString();
            date.setText(d.isEmpty() ? d : Character.toUpperCase(d.charAt(0)) + d.substring(1));
            removeCallbacks(this);
            if (ticking) postDelayed(this, 60000 - System.currentTimeMillis() % 60000 + 50);
        }
    };

    final BroadcastReceiver timeChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { tick.run(); }
    };

    final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            appsChanged = true;
            if (hasWindowFocus() && layers.isEmpty() && moving < 0) rebuild();
        }
    };

    // Stato della rete per l'icona Wi-Fi: registrato solo mentre la home è visibile.
    final BroadcastReceiver netChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateNetwork(); }
    };
    boolean netRegistered;

    HomeScreen(Context c) {
        super(c);
        ctx = c;
        prefs = ctx.getSharedPreferences("glasshome", Context.MODE_PRIVATE);
        pm = ctx.getPackageManager();
        tiles = TileRepository.get(ctx);
        weather = new Weather(ctx);
        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
        screenW = Math.max(dm.widthPixels, dm.heightPixels);
        screenH = Math.min(dm.widthPixels, dm.heightPixels);
        s = screenW / 1920f;
        medium = Typeface.createFromAsset(ctx.getAssets(), "fonts/Inter-Medium.ttf");
        semibold = Typeface.createFromAsset(ctx.getAssets(), "fonts/Inter-SemiBold.ttf");
        setBackgroundColor(0xFF000000);
        setClipChildren(false);

        // Sfondo e sfumatura verso il basso (la "Top Shelf" è la parte alta della foto).
        wallView = new ImageView(ctx);
        wallView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(wallView, new LayoutParams(-1, -1));
        View shade = new View(ctx);
        PaintDrawable fade = new PaintDrawable();
        fade.setShape(new RectShape());
        fade.setShaderFactory(new ShapeDrawable.ShaderFactory() {
            @Override public Shader resize(int w, int h) {
                return new LinearGradient(0, 0, 0, h,
                        new int[] {0x59000000, 0x00000000, 0x00000000, 0xB3000000, 0xF2050507},
                        new float[] {0f, 0.2f, 0.42f, 0.72f, 1f}, Shader.TileMode.CLAMP);
            }
        });
        shade.setBackground(fade);
        addView(shade, new LayoutParams(-1, -1));

        // Il contenuto scorre in verticale: hero, dock, cassetto.
        shelf = new ShelfScroll(ctx);
        content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        shelf.addView(content, new FrameLayout.LayoutParams(-1, -2));
        addView(shelf, new LayoutParams(-1, -1));

        buildHero();
        buildDockPanel();
        buildRecents();
        buildDrawer();

        // Velo in alto che compare scorrendo, così il cassetto non passa sotto la barra in chiaro.
        topScrim = new View(ctx);
        topScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFC050507, 0xF5050507, 0xD9050507, 0x00050507}));
        topScrim.setAlpha(0);
        addView(topScrim, new LayoutParams(-1, px(260)));

        base = new FrameLayout(ctx); // barra superiore: sempre visibile
        base.setClipChildren(false);
        addView(base, new LayoutParams(-1, -1));
        buildTopBar();

        caption = new TextView(ctx);
        style(caption, 24, medium);
        caption.setAlpha(0);
        caption.setShadowLayer(px(10), 0, px(1), 0x99000000);
        LayoutParams clp = new LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        clp.topMargin = px(132);
        base.addView(caption, clp);

        getViewTreeObserver().addOnGlobalFocusChangeListener(this::onFocusMoved);

        dailyWallpaper();
        showWallpaper(wallpaperKey());
        rebuild();
        updateWeather();
    }

    // ---- Costruzione ----

    void buildHero() {
        FrameLayout hero = new FrameLayout(ctx);
        heroTitle = new TextView(ctx);
        style(heroTitle, 64, semibold);
        heroTitle.setShadowLayer(px(24), 0, px(2), 0x66000000);
        heroTitle.setSingleLine();
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        heroTitle.setMaxWidth(px(1200));
        LayoutParams tl = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT);
        tl.leftMargin = px(SIDE);
        tl.bottomMargin = px(84);
        hero.addView(heroTitle, tl);

        heroHint = new TextView(ctx);
        style(heroHint, 22, medium);
        heroHint.setAlpha(0.7f);
        heroHint.setShadowLayer(px(12), 0, px(1), 0x80000000);
        heroHint.setText("OK per aprire  ·  tieni premuto OK o ☰ per le opzioni");
        LayoutParams hl = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT);
        hl.leftMargin = px(SIDE + 2);
        hl.bottomMargin = px(44);
        hero.addView(heroHint, hl);

        credit = new TextView(ctx);
        style(credit, 18, medium);
        credit.setAlpha(0.5f);
        credit.setShadowLayer(px(8), 0, px(1), 0x80000000);
        LayoutParams cl = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
        cl.rightMargin = px(SIDE);
        cl.bottomMargin = px(46);
        hero.addView(credit, cl);
        content.addView(hero, new LinearLayout.LayoutParams(-1, px(HERO_H)));
    }

    void buildDockPanel() {
        dockPanel = new Glass.Panel(ctx, this, px(40), 0x47101014);
        dockScroll = new DockScroll(ctx);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // Le tessere restano dentro il pannello; il padding lascia spazio a scala e ombra.
        dockScroll.setClipChildren(true);
        dockScroll.setClipToPadding(false);
        dockScroll.setPadding(px(48), px(30), px(48), 0);
        dockRow = new LinearLayout(ctx);
        dockRow.setClipChildren(false);
        dockScroll.addView(dockRow, new FrameLayout.LayoutParams(-2, -1));
        dockPanel.addView(dockScroll, new FrameLayout.LayoutParams(-1, -1));
        // Il pannello ritaglia il proprio contenuto sul suo profilo arrotondato.
        final float dockRadius = px(40);
        dockPanel.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dockRadius);
            }
        });
        dockPanel.setClipToOutline(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(PANEL_H));
        lp.leftMargin = lp.rightMargin = px(SIDE - 32);
        content.addView(dockPanel, lp);
    }

    /** "Recenti": le ultime app usate, tra il dock e il cassetto. Nascosta finché è vuota. */
    void buildRecents() {
        recentsTitle = new TextView(ctx);
        style(recentsTitle, 34, semibold);
        recentsTitle.setAlpha(0.9f);
        recentsTitle.setText("Recenti");
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = px(SIDE);
        tl.topMargin = px(60);
        tl.bottomMargin = px(26);
        content.addView(recentsTitle, tl);
        recentsRow = new LinearLayout(ctx);
        recentsRow.setClipChildren(false);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, px(GRID_H + 62));
        rl.leftMargin = px(SIDE);
        content.addView(recentsRow, rl);
    }

    void buildRecentsRow() {
        seenRecents = Recents.version;
        recentsRow.removeAllViews();
        int n = 0;
        for (String p : Recents.get(ctx)) {
            if (n >= GRID_COLS || !apps.containsKey(p)) continue;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(GRID_W), -1);
            if (n > 0) lp.leftMargin = px(57);
            View c = cell(p, GRID_W, GRID_H, false);
            c.setTag(R_ROW, ROW_RECENTS);
            recentsRow.addView(c, lp);
            n++;
        }
        int vis = n == 0 ? View.GONE : View.VISIBLE;
        recentsTitle.setVisibility(vis);
        recentsRow.setVisibility(vis);
    }

    void buildDrawer() {
        drawerTitle = new TextView(ctx);
        style(drawerTitle, 34, semibold);
        drawerTitle.setAlpha(0.9f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = px(SIDE);
        tl.topMargin = px(60);
        tl.bottomMargin = px(26);
        content.addView(drawerTitle, tl);

        grid = new GridLayout(ctx);
        grid.setColumnCount(GRID_COLS);
        grid.setClipChildren(false);
        grid.setUseDefaultMargins(false);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2);
        gl.leftMargin = px(SIDE);
        gl.bottomMargin = px(140);
        content.addView(grid, gl);
    }

    void buildTopBar() {
        topRow = new LinearLayout(ctx);
        topRow.setClipChildren(false);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        View a = pill("Cerca", Icons.search(px(30)), v -> openSearch());
        pill("Impostazioni", bitmap("ui/settings.png"), this::openSettingsMenu);
        pill("Home Amazon", bitmap("ui/grid_view.png"), v -> openAmazonHome());
        optionsPill = pill("Opzioni", bitmap("ui/image.png"), this::openOptions);
        a.setNextFocusLeftId(a.getId());
        optionsPill.setNextFocusRightId(optionsPill.getId());
        LayoutParams lp = new LayoutParams(-2, px(64), Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = px(SIDE - 8);
        lp.topMargin = px(48);
        base.addView(topRow, lp);

        LinearLayout time = new LinearLayout(ctx);
        time.setOrientation(LinearLayout.VERTICAL);
        time.setGravity(Gravity.RIGHT);
        clock = new TextView(ctx);
        style(clock, 54, medium);
        clock.setShadowLayer(px(16), 0, px(2), 0x59000000);
        date = new TextView(ctx);
        style(date, 22, medium);
        date.setAlpha(0.8f);
        date.setShadowLayer(px(10), 0, px(1), 0x66000000);
        time.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
        dl.topMargin = px(6);
        time.addView(date, dl);
        // A sinistra dell'orologio: meteo e stato della rete.
        LinearLayout status = new LinearLayout(ctx);
        status.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(ctx);
        status.addView(weatherIcon, new LinearLayout.LayoutParams(px(40), px(40)));
        weatherText = new TextView(ctx);
        style(weatherText, 30, medium);
        weatherText.setShadowLayer(px(12), 0, px(1), 0x66000000);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.leftMargin = px(8);
        wl.rightMargin = px(30);
        status.addView(weatherText, wl);
        wifiIcon = new ImageView(ctx);
        wifiIcon.setAlpha(0.9f);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(px(34), px(34));
        il.rightMargin = px(30);
        status.addView(wifiIcon, il);

        LinearLayout right = new LinearLayout(ctx);
        right.addView(status, new LinearLayout.LayoutParams(-2, px(64)));
        right.addView(time, new LinearLayout.LayoutParams(-2, -2));
        LayoutParams tl = new LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        tl.rightMargin = px(SIDE);
        tl.topMargin = px(42);
        base.addView(right, tl);
    }

    View optionsPill;

    /** Un pulsante a pillola in vetro; col focus diventa bianco con testo scuro, come su tvOS. */
    View pill(String text, Bitmap icon, View.OnClickListener click) {
        final Glass.Panel p = new Glass.Panel(ctx, this, px(32), 0x33000000);
        p.setId(View.generateViewId());
        p.setContentDescription(text);
        p.setFocusable(true);
        p.setFocusableInTouchMode(true);
        p.setClickable(true);
        // Il riempimento bianco è lo sfondo della riga (wrap_content), così la pillola resta stretta.
        final GradientDrawable white = new GradientDrawable();
        white.setColor(0xF2FFFFFF);
        white.setCornerRadius(px(32));
        white.setAlpha(0);
        LinearLayout row = new LinearLayout(ctx);
        row.setBackground(white);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(22), 0, px(28), 0);
        final ImageView img = new ImageView(ctx);
        img.setImageBitmap(icon);
        row.addView(img, new LinearLayout.LayoutParams(px(30), px(30)));
        final TextView t = new TextView(ctx);
        style(t, 24, medium);
        t.setText(text);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = px(12);
        row.addView(t, tl);
        p.addView(row, new FrameLayout.LayoutParams(-2, -1));
        p.setOnClickListener(click);
        p.setOnFocusChangeListener((v, has) -> {
            android.animation.ObjectAnimator.ofInt(white, "alpha", white.getAlpha(), has ? 255 : 0).setDuration(150).start();
            t.setTextColor(has ? 0xFF111114 : 0xFFFFFFFF);
            img.setColorFilter(has ? 0xFF111114 : 0xFFFFFFFF);
            p.animate().scaleX(has ? 1.06f : 1).scaleY(has ? 1.06f : 1).setInterpolator(TileView.SPRING).setDuration(420).start();
        });
        p.setOnKeyListener((v, k, e) -> {
            if (k == KeyEvent.KEYCODE_DPAD_DOWN && e.getAction() == KeyEvent.ACTION_DOWN) { focusDock(); return true; }
            return false;
        });
        img.setColorFilter(0xFFFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -1);
        lp.rightMargin = px(16);
        topRow.addView(p, lp);
        return p;
    }

    // ---- Ciclo di vita ----

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        ctx.registerReceiver(packagesChanged, f);
        IntentFilter time = new IntentFilter(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        ctx.registerReceiver(timeChanged, time);
    }

    @Override
    protected void onDetachedFromWindow() {
        ctx.unregisterReceiver(packagesChanged);
        ctx.unregisterReceiver(timeChanged);
        setNetWatch(false);
        ticking = false;
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    /**
     * Orologio, rete e meteo lavorano solo quando la home si vede (overlay nascosto o app in
     * background: tutto fermo).
     */
    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (visible == ticking) return;
        ticking = visible;
        setNetWatch(visible);
        if (visible) {
            tick.run();
            updateNetwork();
            updateWeather();
            weather.refreshIfStale(this::updateWeather);
        } else {
            removeCallbacks(tick);
        }
    }

    void setNetWatch(boolean on) {
        if (on == netRegistered) return;
        netRegistered = on;
        if (on) {
            IntentFilter f = new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION);
            f.addAction(android.net.wifi.WifiManager.RSSI_CHANGED_ACTION);
            ctx.registerReceiver(netChanged, f);
        } else {
            try { ctx.unregisterReceiver(netChanged); } catch (RuntimeException ignored) {}
        }
    }

    /** Icona di rete: Wi-Fi con 0-4 tacche, cavo, o Wi-Fi barrato se non c'è connessione. */
    @SuppressWarnings("deprecation")
    void updateNetwork() {
        int level;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) level = -1;
            else if (ni.getType() == android.net.ConnectivityManager.TYPE_ETHERNET) level = 9;
            else {
                android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                level = Math.max(1, android.net.wifi.WifiManager.calculateSignalLevel(wm.getConnectionInfo().getRssi(), 5));
            }
        } catch (RuntimeException e) {
            level = -1;
        }
        if (level == wifiLevel) return;
        wifiLevel = level;
        wifiIcon.setImageBitmap(level == 9 ? Icons.ethernet(px(34)) : Icons.wifi(px(34), level));
    }

    void updateWeather() {
        seenWeather = Weather.version;
        boolean show = weather.hasFresh();
        weatherIcon.setVisibility(show ? View.VISIBLE : View.GONE);
        weatherText.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        weatherIcon.setImageBitmap(Icons.weather(px(40), weather.code(), weather.day()));
        weatherText.setText(weather.temp() + "°");
    }

    /** Gli script (wallpaper, tile, weather, backup-config, restore-config) avviano l'app con questi extra. */
    void applyExtras(Intent intent) {
        if (intent == null) return;
        String from = intent.getStringExtra("from");
        String city = intent.getStringExtra("weather_city");
        if (city != null) {
            if (city.trim().isEmpty() || city.equalsIgnoreCase("off")) {
                weather.disable();
                updateWeather();
                showCaption("Meteo disattivato");
            } else {
                showCaption("Cerco " + city + "…");
                weather.setCity(city.trim(), msg -> { updateWeather(); showCaption(msg); });
            }
            return;
        }
        String config = intent.getStringExtra("config");
        if ("export".equals(config)) {
            File out = ConfigBackup.export(ctx);
            showCaption(out != null ? "Configurazione pronta per il backup" : "Backup non riuscito");
            return;
        }
        if ("import".equals(config) && from != null) {
            int n = ConfigBackup.importFrom(ctx, from);
            if (n < 0) { showCaption("Backup non valido"); return; }
            tiles.clear();
            TileRepository.version++;
            dockVersion++;
            Recents.version++;
            Weather.version++;
            cachedKey = null;
            photoVersion++;
            showCaption("Configurazione ripristinata (" + n + " impostazioni)");
            return;
        }
        String tilePkg = intent.getStringExtra("tile_pkg");
        if (tilePkg != null && from != null) {
            boolean ok = tiles.importCustom(tilePkg, from);
            showCaption(ok ? "Tessera aggiornata: " + label(tilePkg) : "Immagine non valida per " + tilePkg);
            return;
        }
        String w = intent.getStringExtra("wallpaper");
        if (from != null && CUSTOM.equals(w) && customWallpaper() != null) {
            try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(customWallpaper())) {
                byte[] buf = new byte[65536];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            } catch (Exception e) {
                showCaption("Non riesco a leggere la foto");
                return;
            }
            cachedKey = null;
            photoVersion++;
            prefs.edit().putBoolean("wallpaper_daily", false).apply(); // hai scelto la tua foto
        }
        if (w != null && choices().contains(w)) prefs.edit().putString("wallpaper", w).apply();
    }

    /**
     * La home torna davanti: recupera i cambi fatti dall'altra copia o da installazioni.
     * reset: è stato premuto Home, quindi chiude i menu e torna al dock.
     */
    void onShown(boolean reset) {
        if (reset) {
            closeAllLayers();
            endMove(false);
        }
        dailyWallpaper();
        if (!wallpaperKey().equals(shownWallpaper) || seenPhotoVersion != photoVersion) showWallpaper(wallpaperKey());
        boolean stale = appsChanged || seenDockVersion != dockVersion || seenTileVersion != TileRepository.version || seenRadius != radius();
        if (stale && layers.isEmpty() && moving < 0) rebuild();
        else if (seenRecents != Recents.version && layers.isEmpty() && moving < 0 && !inRecents(findFocus())) buildRecentsRow();
        if (seenWeather != Weather.version) updateWeather();
        if (!layers.isEmpty() || dockRow.getChildCount() == 0) return;
        View f = findFocus();
        if (reset || f == null) focusDock();
    }

    // ---- App ----

    Map<String, String> installed() {
        final Map<String, String> labels = new HashMap<>();
        for (String cat : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String p = r.activityInfo.packageName;
                if (!p.equals(ctx.getPackageName()) && !HIDDEN.contains(p) && !labels.containsKey(p))
                    labels.put(p, r.activityInfo.applicationInfo.loadLabel(pm).toString());
            }
        }
        List<String> names = new ArrayList<>(labels.keySet());
        Collections.sort(names, (a, b) -> labels.get(a).compareToIgnoreCase(labels.get(b)));
        Map<String, String> sorted = new LinkedHashMap<>();
        for (String p : names) sorted.put(p, labels.get(p));
        return sorted;
    }

    String label(String p) {
        String l = apps.get(p);
        if (l != null) return l;
        try { return pm.getApplicationInfo(p, 0).loadLabel(pm).toString(); } catch (Exception e) { return p; }
    }

    List<String> dockApps(Map<String, String> all) {
        List<String> out = new ArrayList<>();
        String saved = prefs.getString("dock", null);
        if (saved != null) {
            for (String p : saved.split(",")) if (all.containsKey(p) && !out.contains(p)) out.add(p);
            return out;
        }
        for (String p : DEFAULT_DOCK) if (all.containsKey(p) && !out.contains(p)) out.add(p);
        for (String p : all.keySet()) {
            if (out.size() >= 6) break;
            if (!out.contains(p) && !isSystem(p)) out.add(p);
        }
        return out;
    }

    boolean isSystem(String p) {
        try {
            return (pm.getApplicationInfo(p, 0).flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    void saveDock() {
        prefs.edit().putString("dock", TextUtils.join(",", dock)).apply();
        dockVersion++;
        seenDockVersion = dockVersion;
    }

    /** Ricostruisce dock e cassetto (dopo installazioni, cambi del dock, tessere o angoli). */
    void rebuild() {
        appsChanged = false;
        seenDockVersion = dockVersion;
        seenTileVersion = TileRepository.version;
        seenRadius = radius();
        apps = installed();
        dock = dockApps(apps);

        dockRow.removeAllViews();
        for (int i = 0; i < dock.size(); i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(DOCK_W), -1);
            if (i > 0) lp.leftMargin = px(DOCK_GAP);
            dockRow.addView(cell(dock.get(i), DOCK_W, DOCK_H, true), lp);
        }
        if (dock.isEmpty()) {
            View add = textCell("Aggiungi app", DOCK_W, DOCK_H);
            add.setOnClickListener(v -> focusDrawer());
            dockRow.addView((View) add.getParent(), new LinearLayout.LayoutParams(px(DOCK_W), -1));
        }

        buildRecentsRow();

        grid.removeAllViews();
        Set<String> hidden = hiddenApps();
        int i = 0;
        for (String p : apps.keySet()) {
            if (hidden.contains(p)) continue; // nascosta solo dal cassetto, non dal sistema
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = px(GRID_W);
            lp.height = px(GRID_H + 62);
            int col = i % GRID_COLS;
            lp.setMargins(col == 0 ? 0 : px(57), 0, 0, px(22));
            View c = cell(p, GRID_W, GRID_H, false);
            c.setTag(R_ROW, i / GRID_COLS);
            grid.addView(c, lp);
            i++;
        }
        drawerTitle.setText("Tutte le app  ·  " + i + (hidden.isEmpty() ? "" : "   (" + countHidden(hidden) + " nascoste)"));
        if (layers.isEmpty() && moving < 0) {
            View f = findFocus();
            if (f == null || !f.isAttachedToWindow()) focusDock();
        }
    }

    static final int R_ROW = 0x7f0e0001; // tag: riga del cassetto (ROW_RECENTS per i Recenti)
    static final int ROW_RECENTS = -1;

    // ---- App nascoste dal cassetto (restano installate e attive) ----

    Set<String> hiddenApps() {
        String s = prefs.getString("hidden", "");
        return s.isEmpty() ? new HashSet<>() : new HashSet<>(Arrays.asList(s.split(",")));
    }

    int countHidden(Set<String> hidden) {
        int n = 0;
        for (String p : hidden) if (apps.containsKey(p)) n++;
        return n;
    }

    void setHidden(String p, boolean hide) {
        Set<String> h = hiddenApps();
        if (hide) h.add(p); else h.remove(p);
        prefs.edit().putString("hidden", TextUtils.join(",", h)).apply();
        dockVersion++; // l'altra copia della home ricostruisce al prossimo ritorno
    }

    /** Una tessera con il nome sotto (visibile col focus). */
    View cell(final String p, int w, int h, final boolean inDock) {
        final LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        final TileView t = new TileView(ctx, px(radius()), LIFT, px(28));
        t.setContentDescription(label(p));
        col.addView(t, new LinearLayout.LayoutParams(px(w), px(h)));
        final TextView name = new TextView(ctx);
        style(name, 24, medium);
        name.setSingleLine();
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMaxWidth(px(w));
        name.setText(label(p));
        name.setAlpha(0);
        name.setShadowLayer(px(8), 0, px(1), 0x99000000);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
        nl.topMargin = px(24);
        col.addView(name, nl);

        final int pw = px(w), ph = px(h);
        Bitmap ready = tiles.cached(p, pw, ph);
        if (ready != null) t.setTile(ready);
        else tiles.request(p, pw, ph, t::setTile);

        t.setOnFocusChangeListener((v, has) -> {
            name.animate().alpha(has ? 1 : 0).translationY(has ? 0 : -px(6)).setDuration(has ? 220 : 120).start();
            if (has && inDock) {
                lastDock = dockRow.indexOfChild(col);
                setHero(label(p));
            }
        });
        t.setOnClickListener(v -> { if (moving < 0) launch(p, label(p)); });
        t.setOnLongClickListener(v -> { if (moving < 0) appMenu(t, p, inDock); return true; });
        t.setOnKeyListener((v, k, e) -> {
            if (k == KeyEvent.KEYCODE_MENU) {
                if (e.getAction() == KeyEvent.ACTION_UP && moving < 0) appMenu(t, p, inDock);
                return true;
            }
            if (e.getAction() != KeyEvent.ACTION_DOWN || moving >= 0) return false;
            if (inDock && k == KeyEvent.KEYCODE_DPAD_UP) { topRow.getChildAt(0).requestFocus(); return true; }
            Object row = col.getTag(R_ROW);
            if (k == KeyEvent.KEYCODE_DPAD_UP && Integer.valueOf(ROW_RECENTS).equals(row)) { focusDock(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_UP && Integer.valueOf(0).equals(row)) {
                if (recentsRow.getVisibility() == View.VISIBLE && recentsRow.getChildCount() > 0) focusFirst(recentsRow);
                else focusDock();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN && Integer.valueOf(ROW_RECENTS).equals(row)) { focusDrawer(); return true; }
            if (inDock && k == KeyEvent.KEYCODE_DPAD_DOWN && recentsRow.getVisibility() == View.VISIBLE && recentsRow.getChildCount() > 0) {
                focusFirst(recentsRow);
                return true;
            }
            return false;
        });
        col.setTag(p);
        return col;
    }

    TileView textCell(String text, int w, int h) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        TileView t = new TileView(ctx, px(radius()), LIFT, px(28));
        TextView l = new TextView(ctx);
        style(l, 30, medium);
        l.setText(text);
        l.setGravity(Gravity.CENTER);
        t.addView(l, new FrameLayout.LayoutParams(-1, -1));
        col.addView(t, new LinearLayout.LayoutParams(px(w), px(h)));
        return t;
    }

    void setHero(String title) {
        if (title.equals(heroTitle.getText().toString())) return;
        heroTitle.setText(title);
        heroTitle.setAlpha(0.4f);
        heroTitle.setTranslationX(px(lastDirX * 14));
        heroTitle.animate().alpha(1).translationX(0).setDuration(260).setInterpolator(ease).start();
    }

    void launch(final String p, String label) {
        Recents.record(ctx, p);
        // Sull'overlay, nei 5 secondi dopo Home, solo il launcher Amazon può aprire subito le app.
        if (ctx instanceof HomeService) ((HomeService) ctx).launchThroughAmazon(label, () -> startApp(p), 0);
        else startApp(p);
    }

    void startApp(String p) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(p);
        if (i == null) i = pm.getLaunchIntentForPackage(p);
        if (i == null || !start(i)) showCaption("Non riesco ad aprire l'app");
    }

    // ---- Focus e scorrimento ----

    void focusDock() {
        if (dockRow.getChildCount() == 0) return;
        View col = dockRow.getChildAt(Math.max(0, Math.min(lastDock, dockRow.getChildCount() - 1)));
        View t = col instanceof ViewGroup ? ((ViewGroup) col).getChildAt(0) : col;
        t.requestFocus();
    }

    void focusDrawer() {
        focusFirst(grid);
    }

    void focusFirst(ViewGroup row) {
        if (row.getChildCount() > 0) ((ViewGroup) row.getChildAt(0)).getChildAt(0).requestFocus();
    }

    boolean inDrawer(View v) {
        for (View p = v; p != null && p != this; p = p.getParent() instanceof View ? (View) p.getParent() : null)
            if (p == grid) return true;
        return false;
    }

    boolean inRecents(View v) {
        for (View p = v; p != null && p != this; p = p.getParent() instanceof View ? (View) p.getParent() : null)
            if (p == recentsRow) return true;
        return false;
    }

    boolean inDock(View v) {
        for (View p = v; p != null && p != this; p = p.getParent() instanceof View ? (View) p.getParent() : null)
            if (p == dockRow) return true;
        return false;
    }

    /** Scorrimento deciso da noi (non dallo ScrollView): prevedibile e sempre animato. */
    void onFocusMoved(View old, View now) {
        if (now == null || !layers.isEmpty()) return;
        if (inDrawer(now) || inRecents(now)) {
            Rect r = new Rect();
            now.getDrawingRect(r);
            content.offsetDescendantRectToMyCoords(now, r);
            int max = Math.max(0, content.getHeight() - shelf.getHeight());
            shelf.smoothScrollTo(0, Math.max(0, Math.min(max, r.top - px(300))));
        } else {
            shelf.smoothScrollTo(0, 0);
            if (inDock(now)) {
                View col = (View) now.getParent();
                int left = col.getLeft(), right = col.getRight(), sx = dockScroll.getScrollX();
                int view = dockScroll.getWidth() - dockScroll.getPaddingLeft() - dockScroll.getPaddingRight();
                int margin = px(DOCK_W / 2);
                if (left - margin < sx) dockScroll.smoothScrollTo(Math.max(0, left - margin), 0);
                else if (right + margin > sx + view) dockScroll.smoothScrollTo(right + margin - view, 0);
            }
        }
    }

    /** ScrollView che non scorre da solo: lo fa onFocusMoved. Muove la foto in parallasse. */
    class ShelfScroll extends ScrollView {
        ShelfScroll(Context c) {
            super(c);
            setVerticalScrollBarEnabled(false);
            setOverScrollMode(View.OVER_SCROLL_NEVER);
            setClipChildren(false);
            setFillViewport(true);
            setFocusable(false); // solo le tessere prendono il focus, mai il contenitore
        }
        @Override public boolean executeKeyEvent(KeyEvent e) { return false; }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) { return 0; }
        @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
            super.onScrollChanged(l, t, ol, ot);
            wallView.setTranslationY(-t * 0.25f);
            topScrim.setAlpha(Math.min(1f, t / (float) px(160)));
            dockPanel.invalidate(); // il vetro deve ricalcolare cosa c'è dietro
        }
    }

    class DockScroll extends HorizontalScrollView {
        DockScroll(Context c) { super(c); setFocusable(false); }
        @Override public boolean executeKeyEvent(KeyEvent e) { return false; }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) { return 0; }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (down) {
            lastDirX = k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
            lastDirY = k == KeyEvent.KEYCODE_DPAD_UP ? -1 : k == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
        }
        if (moving >= 0) return moveKey(e);
        if (k == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (!layers.isEmpty()) closeLayer();
                else if (!inDock(findFocus())) focusDock();
            }
            return true; // è la home: dietro non c'è niente
        }
        if (k == KeyEvent.KEYCODE_MENU && layers.isEmpty() && !(findFocus() instanceof TileView)) {
            if (e.getAction() == KeyEvent.ACTION_UP) openOptions(optionsPill);
            return true;
        }
        // D-pad contro un bordo: la tessera "spinge" e torna (parallasse), invece di non fare nulla.
        View f = down && (lastDirX != 0 || lastDirY != 0) ? findFocus() : null;
        if (f instanceof TileView) {
            if (super.dispatchKeyEvent(e)) return true; // gestito da un listener (es. Su dal dock)
            int dir = lastDirX < 0 ? View.FOCUS_LEFT : lastDirX > 0 ? View.FOCUS_RIGHT : lastDirY < 0 ? View.FOCUS_UP : View.FOCUS_DOWN;
            View next = f.focusSearch(dir);
            if (next == null || next == f) {
                ((TileView) f).nudge(lastDirX, lastDirY);
                return true;
            }
            return false; // il sistema sposta il focus
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- Spostare le app nel dock ----

    void startMove(int index) {
        if (index < 0 || index >= dockRow.getChildCount()) return;
        moving = index;
        TileView t = (TileView) ((ViewGroup) dockRow.getChildAt(index)).getChildAt(0);
        t.requestFocus();
        t.setMoving(true);
        showCaption("◀ ▶ per spostare  ·  OK per confermare");
    }

    boolean moveKey(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            int to = moving + (k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            View col = dockRow.getChildAt(moving);
            TileView t = (TileView) ((ViewGroup) col).getChildAt(0);
            if (to < 0 || to >= dock.size()) { t.nudge(lastDirX, 0); return true; }
            Collections.swap(dock, moving, to);
            dockRow.removeViewAt(moving);
            dockRow.addView(col, to);
            for (int i = 0; i < dockRow.getChildCount(); i++)
                ((LinearLayout.LayoutParams) dockRow.getChildAt(i).getLayoutParams()).leftMargin = i == 0 ? 0 : px(DOCK_GAP);
            dockRow.requestLayout();
            moving = to;
            lastDock = to;
            t.requestFocus();
            post(() -> onFocusMoved(null, t));
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU)) {
            endMove(true);
        }
        return true; // durante lo spostamento nessun altro tasto fa niente
    }

    void endMove(boolean save) {
        if (moving < 0) return;
        View col = dockRow.getChildAt(moving);
        moving = -1;
        if (save) saveDock();
        if (col != null) ((TileView) ((ViewGroup) col).getChildAt(0)).setMoving(false);
    }

    // ---- Menu ----

    interface Pick { void pick(int i); }

    void appMenu(View anchor, final String p, boolean inDock) {
        final int i = dock.indexOf(p);
        final List<String> items = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        if (inDock) {
            items.add("Sposta"); actions.add(() -> startMove(i));
            items.add("Togli dal dock"); actions.add(() -> {
                dock.remove(p);
                saveDock();
                rebuild();
                lastDock = Math.max(0, Math.min(i, dock.size() - 1));
                focusDock();
                showCaption("Tolta dal dock");
            });
        } else {
            items.add(i >= 0 ? "Togli dal dock" : "Aggiungi al dock"); actions.add(() -> {
                if (i >= 0) dock.remove(p); else dock.add(p);
                saveDock();
                rebuild();
                showCaption(i >= 0 ? "Tolta dal dock" : "Aggiunta al dock");
            });
            if (inRecents(anchor)) {
                items.add("Togli dai Recenti"); actions.add(() -> {
                    Recents.remove(ctx, p);
                    buildRecentsRow();
                    focusDock();
                });
            } else {
                items.add("Nascondi dal cassetto"); actions.add(() -> {
                    setHidden(p, true);
                    rebuild();
                    focusDrawer();
                    showCaption(label(p) + " nascosta. Opzioni → App nascoste per rimostrarla");
                });
            }
        }
        items.add("Sistema icona…"); actions.add(() -> iconMenu(anchor, p));
        items.add("Info app"); actions.add(() -> start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + p))));
        menu(anchor, label(p), items.toArray(new String[0]), which -> actions.get(which).run());
    }

    /** "Sistema icona": le voci fisse più eventuali sorgenti extra (es. la futura "Genera con AI"). */
    void iconMenu(final View anchor, final String p) {
        final List<String> items = new ArrayList<>(Arrays.asList("Automatica", "Carica dal PC", "Ripristina originale"));
        final List<TileSource> extra = tiles.selectableSources();
        for (TileSource src : extra) items.add(src.label());
        String mode = tiles.mode(p);
        String now = CustomTileSource.exists(ctx, p) && mode.isEmpty() ? "personalizzata"
                : SmartTileSource.ID.equals(mode) ? "automatica" : mode.isEmpty() ? "originale" : mode;
        menu(anchor, "Sistema icona · ora: " + now, items.toArray(new String[0]), which -> {
            if (which == 0) { tiles.setMode(p, SmartTileSource.ID); rebuildKeepingFocus(p); }
            else if (which == 1) uploadHelp(anchor, p);
            else if (which == 2) { tiles.restoreOriginal(p); rebuildKeepingFocus(p); }
            else { tiles.setMode(p, extra.get(which - 3).id()); rebuildKeepingFocus(p); }
        });
    }

    void rebuildKeepingFocus(String p) {
        View f = findFocus();
        ViewGroup parent = inDrawer(f) ? grid : inRecents(f) ? recentsRow : dockRow;
        rebuild();
        for (int i = 0; i < parent.getChildCount(); i++)
            if (p.equals(parent.getChildAt(i).getTag())) { ((ViewGroup) parent.getChildAt(i)).getChildAt(0).requestFocus(); return; }
    }

    void uploadHelp(View anchor, String p) {
        info(anchor, "Carica una tessera dal computer",
                "Prepara un'immagine 16:9 (es. PNG 640×360), poi dal computer:\n\n"
                + "Mac:       ./scripts/tile.sh " + "<IP> " + p + " immagine.png\n"
                + "Windows:  .\\scripts\\tile.ps1 <IP> " + p + " immagine.png\n\n"
                + "La tessera compare subito. \"Ripristina originale\" la toglie.");
    }

    void openOptions(final View anchor) {
        boolean daily = prefs.getBoolean("wallpaper_daily", true);
        int hidden = countHidden(hiddenApps());
        String city = weather.configured() ? weather.city() : "non impostato";
        menu(anchor, "GlassHome", new String[] {
                "Sfondo: " + shortName(wallpaperKey()) + "  →",
                "Sfondo del giorno: " + (daily ? "sì" : "no"),
                "Angoli delle tessere: " + radius() + " px  →",
                "App nascoste dal cassetto: " + hidden + "  →",
                "Memoria: " + memoryFreeMb() + " MB liberi  →",
                "Meteo: " + city + "  →",
                "Svuota i Recenti",
                "Informazioni"}, which -> {
            switch (which) {
                case 0: nextWallpaper(); showCaption(creditFor(wallpaperKey())); break;
                case 1:
                    prefs.edit().putBoolean("wallpaper_daily", !daily).putString("wallpaper_day", today()).apply();
                    showCaption(!daily ? "Ogni giorno un nuovo sfondo dal cosmo" : "Sfondo fisso");
                    break;
                case 2: {
                    int r = radius(), next = RADII[0];
                    for (int i = 0; i < RADII.length; i++) if (RADII[i] == r) next = RADII[(i + 1) % RADII.length];
                    prefs.edit().putInt("radius", next).apply();
                    tiles.clear();
                    rebuild();
                    showCaption("Angoli: " + next + " px");
                    break;
                }
                case 3: hiddenAppsMenu(anchor); break;
                case 4: memoryPanel(anchor); break;
                case 5: weatherInfo(anchor); break;
                case 6: Recents.clear(ctx); buildRecentsRow(); showCaption("Recenti svuotati"); break;
                default:
                    info(anchor, "GlassHome " + version(), "Launcher ispirato a tvOS per Fire TV.\n"
                            + "Basato su FireFrame di dyascj (licenza MIT). Font Inter (OFL).\n"
                            + "Sfondi: NASA, ESA, CSA, STScI (Hubble e James Webb). Meteo: Open-Meteo.\n"
                            + "Nessun collegamento con Amazon o Apple.");
            }
        });
    }

    String shortName(String key) {
        if (CUSTOM.equals(key)) return "la tua foto";
        String c = creditFor(key);
        for (String sep : new String[] {",", " (", " · ", ":"}) {
            int cut = c.indexOf(sep);
            if (cut > 0) c = c.substring(0, cut);
        }
        return c;
    }

    void hiddenAppsMenu(View anchor) {
        final List<String> hidden = new ArrayList<>();
        for (String p : apps.keySet()) if (hiddenApps().contains(p)) hidden.add(p);
        if (hidden.isEmpty()) {
            showCaption("Nessuna app nascosta. Nel cassetto: tieni premuto OK → Nascondi dal cassetto");
            return;
        }
        String[] items = new String[hidden.size()];
        for (int i = 0; i < items.length; i++) items[i] = "Mostra di nuovo: " + label(hidden.get(i));
        menu(anchor, "App nascoste dal cassetto", items, which -> {
            setHidden(hidden.get(which), false);
            rebuild();
            showCaption(label(hidden.get(which)) + " di nuovo nel cassetto");
        });
    }

    // ---- Impostazioni Fire TV: scorciatoie alle pagine che Fire OS lascia aprire ----

    static final String SETTINGS_PKG = "com.amazon.tv.settings.v2";
    static final String[][] SETTINGS_PAGES = {
        {"Rete e Wi-Fi", ".tv.network.NetworkActivity"},
        {"Telecomandi e Bluetooth", ".tv.controllers_bluetooth_devices.ControllersAndBluetoothActivity"},
        {"Applicazioni e spazio", ".tv.applications.ApplicationsActivity"},
        {"La mia Fire TV (info, riavvio)", ".tv.device.DeviceActivity"},
    };

    void openSettingsMenu(View anchor) {
        final String[] items = new String[SETTINGS_PAGES.length + 1];
        for (int i = 0; i < SETTINGS_PAGES.length; i++) items[i] = SETTINGS_PAGES[i][0];
        items[SETTINGS_PAGES.length] = "Tutte le impostazioni";
        menu(anchor, "Impostazioni Fire TV", items, which -> {
            if (which == SETTINGS_PAGES.length) { openFireTvSettings(); return; }
            HomeService.pause(true);
            Intent i = new Intent().setClassName(SETTINGS_PKG, SETTINGS_PKG + SETTINGS_PAGES[which][1]);
            if (!start(i)) openFireTvSettings();
        });
    }

    /** "Cerca": la ricerca di Fire TV ("Trova") è nella home Amazon; la apre il servizio Home. */
    void openSearch() {
        if (HomeService.openAmazonFind(ctx)) {
            if (ctx instanceof Activity) ((Activity) ctx).finish();
        } else {
            showCaption("La ricerca non è disponibile");
        }
    }

    // ---- Memoria ----

    long[] memory() {
        android.app.ActivityManager am = (android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return new long[] {mi.availMem / (1024 * 1024), mi.totalMem / (1024 * 1024)};
    }

    long memoryFreeMb() {
        return memory()[0];
    }

    /** Chiude i processi in background delle app (non di sistema, non lo screensaver). */
    int closeBackgroundApps() {
        android.app.ActivityManager am = (android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        Set<String> skip = new HashSet<>(Arrays.asList(ctx.getPackageName(), HomeService.AMAZON_HOME,
                "com.amazon.ftv.screensaver", "com.amazon.bueller.photos", "com.amazon.tv.ime"));
        int n = 0;
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            boolean user = (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
            if (!(user || apps.containsKey(ai.packageName)) || skip.contains(ai.packageName)) continue;
            am.killBackgroundProcesses(ai.packageName);
            n++;
        }
        return n;
    }

    void memoryPanel(View anchor) {
        FrameLayout layer = new FrameLayout(ctx);
        View dim = new View(ctx);
        dim.setBackgroundColor(0x99000000);
        layer.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        Glass.Panel box = new Glass.Panel(ctx, this, px(36), 0xD9141418);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(56), px(48), px(56), px(36));
        TextView t = new TextView(ctx);
        style(t, 38, semibold);
        t.setText("Memoria");
        col.addView(t, new LinearLayout.LayoutParams(-2, -2));
        final TextView body = new TextView(ctx);
        style(body, 28, medium);
        body.setAlpha(0.9f);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.topMargin = px(22);
        col.addView(body, bl);
        // Barra della memoria usata.
        final FrameLayout bar = new FrameLayout(ctx);
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x33FFFFFF);
        track.setCornerRadius(px(8));
        bar.setBackground(track);
        final View used = new View(ctx);
        final GradientDrawable fillD = new GradientDrawable();
        fillD.setCornerRadius(px(8));
        used.setBackground(fillD);
        bar.addView(used, new FrameLayout.LayoutParams(0, -1));
        LinearLayout.LayoutParams barl = new LinearLayout.LayoutParams(px(640), px(16));
        barl.topMargin = px(20);
        barl.bottomMargin = px(30);
        col.addView(bar, barl);
        final Runnable refresh = () -> {
            long[] m = memory();
            long free = m[0], total = Math.max(1, m[1]);
            body.setText("Libera: " + free + " MB su " + total + " MB");
            float usedPart = 1f - free / (float) total;
            fillD.setColor(free < total * 0.15f ? 0xFFFFB74D : 0xFFFFFFFF);
            used.getLayoutParams().width = Math.round(px(640) * usedPart);
            used.requestLayout();
        };
        refresh.run();
        final View close = menuRow("Chiudi le app in background");
        close.setOnClickListener(v -> {
            final long before = memory()[0];
            body.setText("Chiudo le app in background…");
            new Thread(() -> {
                final int n = closeBackgroundApps();
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                post(() -> {
                    refresh.run();
                    long gained = memory()[0] - before;
                    body.setText(body.getText() + "\n" + n + " app controllate · " + (gained > 0 ? "liberati " + gained + " MB" : "niente da liberare"));
                });
            }, "glasshome-memory").start();
        });
        col.addView(close, new LinearLayout.LayoutParams(-2, -2));
        View ok = menuRow("Chiudi");
        ok.setOnClickListener(v -> closeLayer());
        col.addView(ok, new LinearLayout.LayoutParams(-2, -2));
        box.addView(col, new FrameLayout.LayoutParams(-2, -2));
        layer.addView(box, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        openLayer(layer, anchor, close);
    }

    void weatherInfo(View anchor) {
        String now = weather.hasFresh() ? "Ora: " + weather.temp() + "°, " + Weather.describe(weather.code()) + "\n\n" : "\n";
        String head = weather.configured() ? "Città: " + weather.city() + "\n" + now : "Il meteo non è ancora impostato.\n\n";
        info(anchor, "Meteo", head
                + "Per scegliere la città, dal computer:\n"
                + "Mac:       ./scripts/weather.sh <IP> \"Milano\"\n"
                + "Windows:  .\\scripts\\weather.ps1 <IP> \"Milano\"\n"
                + "Per toglierlo usa \"off\" al posto della città.\n\n"
                + "Dati di Open-Meteo, aggiornati al massimo ogni 30 minuti e solo mentre usi la home.");
    }

    // ---- Sfondo del giorno ----

    static String today() {
        return new java.text.SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    /** Un nuovo sfondo dal cosmo ogni giorno, al primo ritorno alla home (nessun timer). */
    void dailyWallpaper() {
        if (!prefs.getBoolean("wallpaper_daily", true)) return;
        String day = today(), last = prefs.getString("wallpaper_day", null);
        if (day.equals(last)) return;
        String cur = prefs.getString("wallpaper", "");
        int idx = -1;
        for (int i = 0; i < WALLPAPERS.length; i++) if (WALLPAPERS[i][0].equals(cur)) idx = i;
        String next = last == null && idx >= 0 ? cur : WALLPAPERS[(idx + 1) % WALLPAPERS.length][0];
        prefs.edit().putString("wallpaper", next).putString("wallpaper_day", day).apply();
    }

    String version() {
        try { return pm.getPackageInfo(ctx.getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    int radius() {
        return prefs.getInt("radius", 20);
    }

    void openFireTvSettings() {
        HomeService.pause(true);
        // Le impostazioni di Amazon. Settings.ACTION_SETTINGS passa dalla home Amazon su Fire OS 7.
        if (!start(new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_SETTINGS")
                .setPackage(HomeService.AMAZON_HOME))) start(new Intent(Settings.ACTION_SETTINGS));
    }

    void openAmazonHome() {
        HomeService.pause(false);
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(HomeService.AMAZON_HOME);
        if (!start(home)) showCaption("La home Amazon non è disponibile");
        else if (ctx instanceof Activity) ((Activity) ctx).finish();
    }

    boolean start(Intent i) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Un menu di vetro vicino all'elemento; Back lo chiude. */
    void menu(View anchor, String title, String[] items, final Pick pick) {
        FrameLayout layer = new FrameLayout(ctx);
        Glass.Panel box = new Glass.Panel(ctx, this, px(30), 0xC7141418);
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(12), px(12), px(12), px(12));
        if (title != null) {
            TextView t = new TextView(ctx);
            style(t, 20, medium);
            t.setAlpha(0.6f);
            t.setText(title);
            t.setPadding(px(24), px(10), px(24), px(12));
            list.addView(t, new LinearLayout.LayoutParams(-2, -2));
        }
        View first = null;
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            View row = menuRow(items[i]);
            row.setOnClickListener(v -> { closeLayer(); pick.pick(which); });
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
            if (first == null) first = row;
        }
        // Larghezza = la voce più lunga (senza andare a capo), entro metà schermo.
        int widest = 0;
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            row.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            widest = Math.max(widest, row.getMeasuredWidth());
        }
        int bw = Math.min(screenW / 2, Math.max(px(420), widest + list.getPaddingLeft() + list.getPaddingRight()));
        box.addView(list, new FrameLayout.LayoutParams(-1, -2)); // righe evidenziate a tutta larghezza
        box.measure(View.MeasureSpec.makeMeasureSpec(bw, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int bh = box.getMeasuredHeight();
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(bw, -2);
        lp.leftMargin = Math.max(px(40), Math.min(loc[0], screenW - bw - px(40)));
        int below = loc[1] + anchor.getHeight() + px(24);
        lp.topMargin = below + bh < screenH - px(40) ? below : Math.max(px(40), loc[1] - bh - px(24));
        layer.addView(box, lp);
        openLayer(layer, anchor, first);
    }

    View menuRow(String text) {
        final FrameLayout row = new FrameLayout(ctx);
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setClickable(true);
        row.setContentDescription(text);
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2FFFFFF);
        bg.setCornerRadius(px(20));
        bg.setAlpha(0);
        row.setBackground(bg);
        final TextView t = new TextView(ctx);
        style(t, 28, medium);
        t.setText(text);
        t.setPadding(px(24), px(18), px(48), px(18));
        row.addView(t, new FrameLayout.LayoutParams(-2, -2));
        row.setOnFocusChangeListener((v, has) -> {
            bg.setAlpha(has ? 255 : 0);
            t.setTextColor(has ? 0xFF111114 : 0xFFFFFFFF);
        });
        return row;
    }

    /** Un pannello informativo con un solo pulsante OK. */
    void info(View anchor, String title, String body) {
        FrameLayout layer = new FrameLayout(ctx);
        View dim = new View(ctx);
        dim.setBackgroundColor(0x99000000);
        layer.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        Glass.Panel box = new Glass.Panel(ctx, this, px(36), 0xD9141418);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(56), px(48), px(56), px(36));
        TextView t = new TextView(ctx);
        style(t, 38, semibold);
        t.setText(title);
        col.addView(t, new LinearLayout.LayoutParams(-2, -2));
        TextView b = new TextView(ctx);
        style(b, 26, medium);
        b.setAlpha(0.85f);
        b.setLineSpacing(px(8), 1f);
        b.setText(body);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.topMargin = px(24);
        bl.bottomMargin = px(28);
        col.addView(b, bl);
        View ok = menuRow("OK");
        ok.setOnClickListener(v -> closeLayer());
        col.addView(ok, new LinearLayout.LayoutParams(-2, -2));
        box.addView(col, new FrameLayout.LayoutParams(-2, -2));
        layer.addView(box, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        openLayer(layer, anchor, ok);
    }

    void openLayer(ViewGroup layer, View returnFocus, View focus) {
        layer.setTag(returnFocus);
        layer.setClickable(true);
        blockBelow(true);
        layers.add(layer);
        addView(layer, new LayoutParams(-1, -1));
        layer.setAlpha(0);
        layer.animate().alpha(1).setDuration(140).start();
        if (focus != null) focus.requestFocus();
    }

    void closeLayer() {
        if (layers.isEmpty()) return;
        ViewGroup layer = layers.remove(layers.size() - 1);
        removeView(layer);
        blockBelow(false);
        View back = (View) layer.getTag();
        if (back != null && back.isAttachedToWindow()) back.requestFocus();
        else if (layers.isEmpty()) focusDock();
        if (layers.isEmpty() && appsChanged) rebuild();
        if (layers.isEmpty() && ctx instanceof HomeService) HomeService.menusClosed();
    }

    void closeAllLayers() {
        while (!layers.isEmpty()) closeLayer();
    }

    /** Solo il livello in cima può avere il focus: la home (contenuto + barra) o l'ultimo menu. */
    void blockBelow(boolean block) {
        int f = block ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS;
        if (layers.isEmpty()) {
            shelf.setDescendantFocusability(f);
            base.setDescendantFocusability(f);
        } else {
            layers.get(layers.size() - 1).setDescendantFocusability(f);
        }
    }

    // ---- Sfondo ----

    File customWallpaper() {
        File dir = ctx.getExternalFilesDir(null);
        return dir == null ? null : new File(dir, "wallpaper.jpg");
    }

    List<String> choices() {
        List<String> c = new ArrayList<>();
        File f = customWallpaper();
        if (f != null && f.exists()) c.add(CUSTOM);
        for (String[] w : WALLPAPERS) c.add(w[0]);
        return c;
    }

    String wallpaperKey() {
        List<String> c = choices();
        String w = prefs.getString("wallpaper", c.get(0));
        return c.contains(w) ? w : c.get(0);
    }

    void nextWallpaper() {
        List<String> c = choices();
        String next = c.get((c.indexOf(wallpaperKey()) + 1) % c.size());
        // Scelto a mano: oggi resta questo (lo sfondo del giorno riprende da domani).
        prefs.edit().putString("wallpaper", next).putString("wallpaper_day", today()).apply();
        showWallpaper(next);
    }

    String creditFor(String key) {
        for (String[] w : WALLPAPERS) if (w[0].equals(key)) return w[1];
        return "La tua foto";
    }

    /** Decodifica e sfocatura una volta sola, in background; condivise tra app e overlay. */
    void showWallpaper(final String key) {
        shownWallpaper = key;
        seenPhotoVersion = photoVersion;
        credit.setText(creditFor(key));
        if (key.equals(cachedKey) && cachedPhoto != null) {
            wallView.setImageBitmap(cachedPhoto);
            blur = cachedBlur;
            invalidateGlass(this);
            return;
        }
        new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            Bitmap b = decode(key);
            if (b == null) b = decode(WALLPAPERS[0][0]);
            if (b == null) return;
            final Bitmap photo = b, soft = Glass.blurred(b);
            post(() -> {
                Bitmap old = cachedPhoto;
                cachedKey = key;
                cachedPhoto = photo;
                cachedBlur = soft;
                wallView.setImageBitmap(photo);
                blur = soft;
                invalidateGlass(HomeScreen.this);
                if (old != null && old != photo) wallView.post(() -> {}); // la vecchia la libera il GC
            });
        }, "glasshome-wallpaper").start();
    }

    void invalidateGlass(View v) {
        if (v instanceof Glass.Panel) v.invalidate();
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) invalidateGlass(((ViewGroup) v).getChildAt(i));
    }

    Bitmap decode(String key) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = open(key)) { BitmapFactory.decodeStream(in, null, o); }
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= screenW && o.outHeight / (o.inSampleSize * 2) >= screenH) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.RGB_565; // metà memoria: la foto è opaca
            try (InputStream in = open(key)) { return BitmapFactory.decodeStream(in, null, o); }
        } catch (Exception e) {
            return null;
        }
    }

    InputStream open(String key) throws Exception {
        return key.equals(CUSTOM) ? new FileInputStream(customWallpaper()) : ctx.getAssets().open("wallpapers/" + key + ".jpg");
    }

    // ---- Glass.Backdrop ----

    @Override public Bitmap blur() { return blur; }
    @Override public int screenW() { return screenW; }
    @Override public int screenH() { return screenH; }
    @Override public float wallOffsetY() { return wallView.getTranslationY(); }

    // ---- Aiuti ----

    void showCaption(String text) {
        caption.setText(text);
        caption.animate().cancel();
        caption.animate().alpha(1).setStartDelay(0).setDuration(160).withEndAction(() ->
                caption.animate().alpha(0).setStartDelay(2600).setDuration(400).start()).start();
    }

    Bitmap bitmap(String asset) {
        try (InputStream in = ctx.getAssets().open(asset)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    void style(TextView t, float size, Typeface face) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTypeface(face);
        t.setTextColor(0xFFFFFFFF);
        t.setIncludeFontPadding(false);
    }

    int px(float v) {
        return Math.round(v * s);
    }
}
