import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;

/**
 * Prepara gli sfondi: ritaglio 16:9 a 1920x1080 con riduzione progressiva (alta qualità), JPEG q=0.92.
 * Uso: java tools/PrepWallpapers.java <sorgente> <destinazione.jpg> [focusY 0..1] [zoom >=1]
 */
public class PrepWallpapers {
    public static void main(String[] a) throws Exception {
        BufferedImage src = ImageIO.read(new File(a[0]));
        float focusY = a.length > 2 ? Float.parseFloat(a[2]) : 0.5f;
        float zoom = a.length > 3 ? Float.parseFloat(a[3]) : 1f;
        int W = 1920, H = 1080;
        // Il riquadro 16:9 più grande possibile (diviso per zoom), centrato in orizzontale, verticale su focusY.
        double k = Math.min(src.getWidth() / (double) W, src.getHeight() / (double) H) / zoom;
        int cw = (int) Math.round(W * k), ch = (int) Math.round(H * k);
        int cx = (src.getWidth() - cw) / 2;
        int cy = (int) Math.round((src.getHeight() - ch) * focusY);
        cy = Math.max(0, Math.min(src.getHeight() - ch, cy));
        BufferedImage img = toRgb(src.getSubimage(cx, cy, cw, ch));
        // Riduzione a passi di 1/2 finché serve, poi l'ultimo passo bicubico.
        while (img.getWidth() / 2 >= W) img = scale(img, img.getWidth() / 2, img.getHeight() / 2);
        img = scale(img, W, H);
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter w = it.next();
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(0.92f);
        File out = new File(a[1]);
        out.delete();
        try (ImageOutputStream os = ImageIO.createImageOutputStream(out)) {
            w.setOutput(os);
            w.write(null, new IIOImage(img, null, null), p);
        }
        System.out.println(out + "  " + (out.length() / 1024) + " KB  (ritaglio " + cw + "x" + ch + " da " + src.getWidth() + "x" + src.getHeight() + ")");
    }

    static BufferedImage toRgb(BufferedImage s) {
        BufferedImage o = new BufferedImage(s.getWidth(), s.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = o.createGraphics();
        g.drawImage(s, 0, 0, null);
        g.dispose();
        return o;
    }

    static BufferedImage scale(BufferedImage s, int w, int h) {
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(s, 0, 0, w, h, null);
        g.dispose();
        return o;
    }
}
