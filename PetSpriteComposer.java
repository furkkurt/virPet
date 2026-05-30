import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/** Builds a 16×48 stacked sprite: legs, body, face, hat (top). */
public final class PetSpriteComposer {

    public static final int SPRITE_W = 16;
    public static final int SPRITE_H = 48;

    private PetSpriteComposer() {}

    public static BufferedImage compose(Path hat, Path leg, Path body, Path face) {
        //TYPE_INT_ARGB demek integer değerinde alpha, red, green, blue değerleri alan
        //(255,0,0,0) gibi bir tip
        BufferedImage out = new BufferedImage(SPRITE_W, SPRITE_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            drawAt(g, leg, 0, 32);
            drawAt(g, body, 0, 16);
            drawAt(g, face, 0, 16);
            drawAt(g, hat, 0, 0);
        } finally {
            //kullanılmayan kaynakları temizlemek iyi bir alışkanlıktır
            g.dispose();
        }
        return out;
    }

    private static void drawAt(Graphics2D g, Path path, int x, int y) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try {
            BufferedImage img = ImageIO.read(path.toFile()); //path'teki görsel
            if (img != null) {
                g.drawImage(img, x, y, null); //
            }
        } catch (IOException ignored) {
            // skip missing or corrupt image
        }
    }
}
