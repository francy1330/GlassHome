package com.glasshome.launcher.tiles;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * Una sorgente di tessere 16:9 per un'app (package name).
 *
 * Le sorgenti sono interrogate in ordine da {@link TileRepository}, sempre su un thread in background:
 * {@link #load} può quindi bloccare (leggere file, decodificare, e in futuro fare una richiesta HTTP
 * a un servizio sul PC in rete locale). Restituisce null se non ha niente per quell'app: si passa
 * alla sorgente successiva.
 *
 * Per aggiungere una sorgente (es. "Genera con AI"):
 *   1. una classe che implementa TileSource, con {@link #label()} non null perché compaia nel menu
 *      "Sistema icona";
 *   2. registrarla in {@link TileRepository#SOURCES};
 *   3. per una sorgente di rete: permesso INTERNET e usesCleartextTraffic (o una network security
 *      config per l'IP del PC) nel manifest. Il risultato può essere salvato con
 *      {@link CustomTileSource#save} per non rigenerarlo a ogni avvio.
 */
public interface TileSource {
    /** Identificativo stabile, salvato nelle preferenze come "modalità" della tessera. */
    String id();

    /** Voce del menu "Sistema icona", o null se la sorgente non si sceglie a mano. */
    String label();

    /** Una tessera w x h (già ritagliata 16:9), o null. Chiamato fuori dal thread UI. */
    Bitmap load(Context c, String pkg, int w, int h) throws Exception;
}
