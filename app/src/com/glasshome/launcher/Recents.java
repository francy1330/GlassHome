package com.glasshome.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Le app usate di recente, la più recente per prima. Le registra sia GlassHome quando apre
 * un'app, sia il servizio Home quando un'app arriva in primo piano in qualunque altro modo
 * (Alexa, un'altra app...): così non serve il permesso "accesso all'utilizzo".
 */
final class Recents {
    static final int MAX = 12;
    static int version;

    private Recents() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("glasshome", Context.MODE_PRIVATE);
    }

    static List<String> get(Context c) {
        String s = prefs(c).getString("recents", "");
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }

    static void record(Context c, String pkg) {
        List<String> l = get(c);
        if (!l.isEmpty() && l.get(0).equals(pkg)) return; // già in cima: niente da scrivere
        l.remove(pkg);
        l.add(0, pkg);
        while (l.size() > MAX) l.remove(l.size() - 1);
        prefs(c).edit().putString("recents", TextUtils.join(",", l)).apply();
        version++;
    }

    static void remove(Context c, String pkg) {
        List<String> l = get(c);
        if (l.remove(pkg)) {
            prefs(c).edit().putString("recents", TextUtils.join(",", l)).apply();
            version++;
        }
    }

    static void clear(Context c) {
        prefs(c).edit().remove("recents").apply();
        version++;
    }
}
