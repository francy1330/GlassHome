import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Genera icon.png (512x512) e banner.png (320x180) di GlassHome. Uso: java tools/MakeArt.java */
public class MakeArt {
    public static void main(String[] a) throws Exception {
        Font inter = Font.createFont(Font.TRUETYPE_FONT, new File("app/assets/fonts/Inter-SemiBold.ttf"));
        ImageIO.write(art(512, 512, inter, false), "png", new File("app/res/drawable-nodpi/icon.png"));
        ImageIO.write(art(640, 360, inter, true), "png", new File("app/res/drawable-nodpi/banner_big.png"));
        BufferedImage big = ImageIO.read(new File("app/res/drawable-nodpi/banner_big.png"));
        BufferedImage small = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(big, 0, 0, 320, 180, null);
        g.dispose();
        ImageIO.write(small, "png", new File("app/res/drawable-nodpi/banner.png"));
        new File("app/res/drawable-nodpi/banner_big.png").delete();
    }

    static BufferedImage art(int w, int h, Font font, boolean banner) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        // Cielo notturno: blu profondo → viola, con un bagliore turchese in basso a sinistra.
        g.setPaint(new GradientPaint(0, 0, new Color(0x0B1430), w, h, new Color(0x2A1048)));
        g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.2f, h * 1.05f), Math.max(w, h) * 0.75f,
                new float[] {0f, 1f}, new Color[] {new Color(0x3AD6C4, false).brighter(), new Color(0, 0, 0, 0)}));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.85f, h * 0.1f), Math.max(w, h) * 0.6f,
                new float[] {0f, 1f}, new Color[] {new Color(0xFF7AC8), new Color(0, 0, 0, 0)}));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g.fillRect(0, 0, w, h);
        g.setComposite(AlphaComposite.SrcOver);

        // Lastra di vetro: riempimento chiaro semitrasparente, bordo luminoso, riflesso diagonale.
        float pw = banner ? w * 0.84f : w * 0.64f, ph = banner ? h * 0.62f : h * 0.64f;
        float px = (w - pw) / 2f, py = (h - ph) / 2f, r = Math.min(pw, ph) * 0.22f;
        RoundRectangle2D pane = new RoundRectangle2D.Float(px, py, pw, ph, r * 2, r * 2);
        g.setColor(new Color(255, 255, 255, 46));
        g.fill(pane);
        Shape old = g.getClip();
        g.clip(pane);
        g.setPaint(new GradientPaint(px, py, new Color(255, 255, 255, 90), px + pw * 0.55f, py + ph, new Color(255, 255, 255, 0)));
        g.fill(new Polygon(new int[] {(int) px, (int) (px + pw * 0.62f), (int) (px + pw * 0.32f), (int) px},
                new int[] {(int) py, (int) py, (int) (py + ph), (int) (py + ph)}, 4));
        g.setClip(old);
        g.setStroke(new BasicStroke(Math.max(1.5f, w / 256f)));
        g.setPaint(new GradientPaint(0, py, new Color(255, 255, 255, 200), 0, py + ph, new Color(255, 255, 255, 40)));
        g.draw(pane);

        g.setColor(Color.WHITE);
        if (banner) {
            Font f = font.deriveFont(h * 0.17f);
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            String s = "GlassHome";
            g.drawString(s, (w - fm.stringWidth(s)) / 2f, h / 2f + (fm.getAscent() - fm.getDescent()) / 2f);
        } else {
            // Una "casa" stilizzata: tre tessere 16:9 sfalsate.
            float tw = pw * 0.56f, th = tw * 9 / 16f;
            for (int i = 0; i < 3; i++) {
                float x = px + pw * 0.22f + i * pw * 0.05f, y = py + ph * 0.24f + i * ph * 0.14f;
                g.setColor(new Color(255, 255, 255, 120 + i * 60));
                g.fill(new RoundRectangle2D.Float(x, y, tw, th, th * 0.3f, th * 0.3f));
            }
        }
        g.dispose();
        return img;
    }
}
