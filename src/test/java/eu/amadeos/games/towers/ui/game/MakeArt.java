package eu.amadeos.games.towers.ui.game;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Draws the files made from the game's own vector art, which are part of the repository: the tileset
 * picture that Tiled shows ({@code maps/tiles.png}: grass, tree, pine, rock, water, road) and the program icon,
 * a cannon tower on grass, at 48 and 256 pixels. {@code ./gradlew art}, then for Windows:
 *
 * <pre>
 * python3 -c "from PIL import Image; Image.open('build/icon-256.png').save('packaging/windows/amade-towers-game.ico',
 *     sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])"
 * </pre>
 */
public final class MakeArt {
    private MakeArt() {
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(tiles(), "png", new File("src/main/resources/eu/amadeos/games/towers/maps/tiles.png"));
        new File("src/main/resources/icons").mkdirs();
        ImageIO.write(icon(48), "png", new File("src/main/resources/icons/amade-towers-game.png"));
        new File("build").mkdirs();
        ImageIO.write(icon(256), "png", new File("build/icon-256.png"));
    }

    /** Six tiles of 20 × 20 pixels in a row, as the ground of the maps counts them from 0. */
    static BufferedImage tiles() {
        BufferedImage image = new BufferedImage(120, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        BufferedImage grass = Art.grass(20, 20, 1);
        for (int i = 0; i < 5; i++) g.drawImage(grass, i * 20, 0, 20, 20, null);
        Art.tree(g, 30, 10, new Random(100));
        Art.pine(g, 50, 10, new Random(200));
        Art.rock(g, 60, 0, new Random(3));
        Art.water(g, new RoundRectangle2D.Double(80, 0, 20, 20, 0, 0), new double[]{80, 0});
        g.setColor(Art.DIRT);
        g.fill(new Rectangle2D.Double(100, 0, 20, 20));
        Art.pebbles(g, 110, 10, new Random(4));
        g.dispose();
        return image;
    }

    /** A cannon tower seen from above on a round patch of grass. */
    static BufferedImage icon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(size / 24.0, size / 24.0);
        Ellipse2D.Double patch = new Ellipse2D.Double(0.5, 0.5, 23, 23);
        g.setPaint(Art.radial(9, 8, 16, new float[]{0, 1}, new Color(0x6E9A3C), new Color(0x2E5420)));
        g.fill(patch);
        g.setClip(patch);
        Art.towerShadow(g, 12, 12);
        g.setClip(null);
        Art.towerBase(g, 12, 12, "cannon", 3);
        g.rotate(-0.6, 12, 12);
        Art.cannon(g, 12, 12);
        g.dispose();
        return image;
    }
}
