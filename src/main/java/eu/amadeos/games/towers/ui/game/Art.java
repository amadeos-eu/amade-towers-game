package eu.amadeos.games.towers.ui.game;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * The pictures of Towers, drawn in code as vectors: grass, water, rocks and trees, the lair and the castle,
 * the three towers and their turrets, the three enemies, arrows and cannon balls. Everything is seen from
 * above, lit from the top left. Sizes are logical units, a tile is 20; things that turn are drawn around
 * a middle point and point to the right. {@link Pictures} turns them into sprites.
 */
final class Art {
    /** Where shadows fall: the light comes from the top left. */
    static final double SHADOW_X = 2.2;
    static final double SHADOW_Y = 2.6;
    /** Pixels per unit of the grass picture; it is scaled to the screen with the rest of the ground. */
    static final int GRASS_PIXELS = 2;

    static final Color DIRT = new Color(0x9E7A4C);
    static final Color DIRT_EDGE = new Color(74, 58, 34, 120);
    static final Color DIRT_RIM = new Color(122, 94, 58, 170);
    /** How round the road's corners are, in units. */
    static final double ROAD_ROUNDING = 7;

    private Art() {
    }

    static RadialGradientPaint radial(double x, double y, double r, float[] fractions, Color... colours) {
        return new RadialGradientPaint(new Point2D.Double(x, y), (float) r, fractions, colours);
    }

    // ---- noise, for the grass --------------------------------------------------------------------

    static double hash(int x, int y, int seed) {
        long h = x * 374761393L + y * 668265263L + seed * 1442695040888963407L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    static double noise(double x, double y, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double fx = x - xi;
        double fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(xi, yi, seed);
        double b = hash(xi + 1, yi, seed);
        double c = hash(xi, yi + 1, seed);
        double d = hash(xi + 1, yi + 1, seed);
        double top = a + (b - a) * fx;
        return top + (c + (d - c) * fx - top) * fy;
    }

    /** Four octaves of noise: large patches with smaller ones in them. */
    static double fbm(double x, double y, int seed) {
        double v = 0;
        double amplitude = 0.5;
        double f = 1;
        for (int i = 0; i < 4; i++) {
            v += amplitude * noise(x * f, y * f, seed + i);
            amplitude *= 0.5;
            f *= 2;
        }
        return v;
    }

    static int mix(int c1, int c2, double t) {
        t = Math.clamp(t, 0, 1);
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    // ---- the ground ------------------------------------------------------------------------------

    /**
     * Meadow grass for a map of {@code width} × {@code height} units, at {@link #GRASS_PIXELS} pixels per
     * unit: light and dark patches, single blades and a few flowers. Made once per level, pixel by pixel.
     */
    static BufferedImage grass(int width, int height, long seed) {
        int w = width * GRASS_PIXELS;
        int h = height * GRASS_PIXELS;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double patches = fbm(x / 90.0, y / 90.0, 1);
                double fine = fbm(x / 9.0, y / 9.0, 7);
                int colour = mix(0x3F6E2A, 0x6E9A3C, patches * 1.3 - 0.15);
                colour = mix(colour, 0x2E5420, (fine - 0.5) * 0.9);
                colour = mix(colour, random.nextBoolean() ? 0x24451A : 0x86B04E, random.nextDouble() * 0.18);
                row[x] = colour;
            }
            image.setRGB(0, y, w, 1, row, 0, w);
        }
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color dark = new Color(0x2C5220);
        Color light = new Color(0x7DAA48);
        for (int i = 0; i < w * h / 113; i++) {
            int x = random.nextInt(w);
            int y = random.nextInt(h);
            g.setColor(random.nextBoolean() ? dark : light);
            g.drawLine(x, y, x + random.nextInt(3) - 1, y - 2 - random.nextInt(3));
        }
        Color[] flowers = {new Color(0xE8E2C8), new Color(0xE6C84A), new Color(0xB89AD8)};
        for (int i = 0; i < w * h / 6400; i++) {
            g.setColor(flowers[random.nextInt(flowers.length)]);
            g.fillOval(random.nextInt(w), random.nextInt(h), 3, 3);
        }
        g.dispose();
        return image;
    }

    /**
     * Still water over {@code pond}, the water tiles joined with round corners: a muddy bank, the water
     * darker where it is deep, and a few ripples on each tile.
     */
    static void water(Graphics2D g, Shape pond, double[] tiles) {
        g.setColor(new Color(58, 48, 30, 150));
        g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(pond);
        // Light and deep bands across the world, the same in every square of the terrain: no seams.
        g.setPaint(new GradientPaint(0, 0, new Color(0x2D6B8F), 30, 30, new Color(0x1D4E6E), true));
        g.fill(pond);
        g.setColor(new Color(150, 210, 235, 90));
        g.setStroke(new BasicStroke(0.6f));
        for (int i = 0; i < tiles.length; i += 2) {
            double x = tiles[i];
            double y = tiles[i + 1];
            g.draw(new Arc2D.Double(x + 3, y + 5, 8, 4, 20, 140, Arc2D.OPEN));
            g.draw(new Arc2D.Double(x + 10, y + 12, 7, 3, 20, 140, Arc2D.OPEN));
        }
    }

    /** A grey boulder on the tile at {@code x}, {@code y}, its shape from {@code random}. */
    static void rock(Graphics2D g, double x, double y, Random random) {
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new Ellipse2D.Double(x + 3 + SHADOW_X, y + 5 + SHADOW_Y, 15, 12));
        Path2D outline = new Path2D.Double();
        double mx = x + 10;
        double my = y + 10;
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI * 2 / 7;
            double r = 6 + random.nextDouble() * 2.5;
            double px = mx + Math.cos(a) * r;
            double py = my + Math.sin(a) * r * 0.85;
            if (i == 0) outline.moveTo(px, py);
            else outline.lineTo(px, py);
        }
        outline.closePath();
        g.setPaint(radial(mx - 3, my - 3, 10, new float[]{0, 1}, new Color(0xB3AEA4), new Color(0x5F5B55)));
        g.fill(outline);
        g.setColor(new Color(0x4A4741));
        g.setStroke(new BasicStroke(0.5f));
        g.draw(new Line2D.Double(mx - 2, my - 1, mx + 2, my + 2));
    }

    /** The shadow of a tree on the ground, below and right of the tile at {@code x}, {@code y}. */
    static void treeShadow(Graphics2D g, double x, double y, boolean pine) {
        g.setColor(new Color(10, 25, 5, 85));
        double s = pine ? 17 : 21;
        g.fill(new Ellipse2D.Double(x + 10 - s / 2 + SHADOW_X * 2, y + 10 - s / 2 + SHADOW_Y * 2, s, s * 0.9));
    }

    /** A leafy tree around {@code mx}, {@code my}: a few round clumps of leaves and one in the middle. */
    static void tree(Graphics2D g, double mx, double my, Random random) {
        Color dark = new Color(0x1F4418);
        Color mid = random.nextBoolean() ? new Color(0x3C7A2A) : new Color(0x4E8A2E);
        Color light = new Color(0x8CC05A);
        if (random.nextDouble() < 0.15) {       // now and then one turning yellow
            mid = new Color(0x7A8A2A);
            light = new Color(0xC8C060);
        }
        float[] fractions = {0, 0.55f, 1};
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = 3 + random.nextDouble() * 2.5;
            double r = 5 + random.nextDouble() * 2.5;
            double bx = mx + Math.cos(a) * d;
            double by = my + Math.sin(a) * d;
            g.setPaint(radial(bx - r * 0.4, by - r * 0.45, r * 1.1, fractions, light, mid, dark));
            g.fill(new Ellipse2D.Double(bx - r, by - r, r * 2, r * 2));
        }
        g.setPaint(radial(mx - 2.5, my - 2.5, 7.5, new float[]{0, 0.6f, 1}, light, mid, dark));
        g.fill(new Ellipse2D.Double(mx - 6, my - 6, 12, 12));
        g.setColor(new Color(255, 255, 220, 50));
        for (int i = 0; i < 5; i++) {
            g.fill(new Ellipse2D.Double(mx - 5 + random.nextDouble() * 5, my - 5 + random.nextDouble() * 5, 1.6, 1.6));
        }
    }

    /** A pine around {@code mx}, {@code my}: three stars of needles, one above the other. */
    static void pine(Graphics2D g, double mx, double my, Random random) {
        Color c0 = new Color(0x163A22);
        Color c1 = new Color(0x2F6B3C);
        Color c2 = new Color(0x5E9A5A);
        for (int layer = 0; layer < 3; layer++) {
            double big = 8.5 - layer * 2.5;
            Path2D star = new Path2D.Double();
            int points = 9;
            double turn = random.nextDouble();
            for (int i = 0; i < points * 2; i++) {
                double a = turn + i * Math.PI / points;
                double r = i % 2 == 0 ? big : big * 0.55;
                if (i == 0) star.moveTo(mx + Math.cos(a) * r, my + Math.sin(a) * r);
                else star.lineTo(mx + Math.cos(a) * r, my + Math.sin(a) * r);
            }
            star.closePath();
            g.setPaint(radial(mx - big * 0.35, my - big * 0.35, big * 1.2, new float[]{0, 0.6f, 1},
                    layer == 2 ? c2 : c1, c1, c0));
            g.fill(star);
        }
    }

    /** The cave the enemies come out of, a dark mouth in a rock with a red glow, its middle at {@code x}, {@code y}. */
    static void lair(Graphics2D g, double x, double y) {
        g.setColor(new Color(0, 0, 0, 80));
        g.fill(new Ellipse2D.Double(x - 12 + SHADOW_X, y - 22 + SHADOW_Y, 34, 46));
        Path2D rock = new Path2D.Double();
        rock.moveTo(x - 10, y - 22);
        rock.lineTo(x + 10, y - 20);
        rock.lineTo(x + 17, y - 9);
        rock.lineTo(x + 15, y + 10);
        rock.lineTo(x + 8, y + 21);
        rock.lineTo(x - 10, y + 22);
        rock.closePath();
        g.setPaint(new GradientPaint((float) x, (float) (y - 20), new Color(0x7D776E), (float) (x + 16),
                (float) (y + 20), new Color(0x3A3632)));
        g.fill(rock);
        g.setPaint(radial(x + 2, y, 16, new float[]{0, 0.4f, 1}, new Color(255, 60, 20, 200),
                new Color(140, 20, 10, 120), new Color(80, 0, 0, 0)));
        g.fill(new Ellipse2D.Double(x - 14, y - 16, 34, 32));
        g.setColor(new Color(0x120808));
        g.fill(new Ellipse2D.Double(x - 8, y - 9, 16, 18));
        g.setColor(new Color(0xFF3A1A));                    // eyes in the dark
        g.fill(new Ellipse2D.Double(x - 2, y - 3, 1.6, 1.2));
        g.fill(new Ellipse2D.Double(x + 1.2, y - 3, 1.6, 1.2));
        g.fill(new Ellipse2D.Double(x - 4, y + 3, 1.4, 1));
        g.fill(new Ellipse2D.Double(x - 1.6, y + 3, 1.4, 1));
        g.setColor(new Color(0xE6DCC4));                    // bones
        g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x + 12, y + 14, x + 17, y + 12));
        g.fill(new Ellipse2D.Double(x + 9, y - 18, 4, 3.5));
        g.setColor(new Color(0x2A2420));
        g.fill(new Ellipse2D.Double(x + 9.8, y - 17.2, 1, 1));
        g.fill(new Ellipse2D.Double(x + 11.4, y - 17.2, 1, 1));
    }

    /** The castle in the box {@code x}, {@code y}, {@code w}, {@code h}: walls, four round towers, a red roof, a banner. */
    static void castle(Graphics2D g, double x, double y, double w, double h) {
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Double(x + SHADOW_X * 3, y + SHADOW_Y * 3, w, h, 6, 6));
        stoneBlock(g, x + 3, y + 6, w - 6, h - 12);
        for (double[] p : new double[][]{{x, y}, {x + w - 12, y}, {x, y + h - 12}, {x + w - 12, y + h - 12}}) {
            roundTower(g, p[0] + 6, p[1] + 6, 6.5);
        }
        g.setPaint(new GradientPaint((float) x, (float) y, new Color(0x9C3B2E), (float) (x + w), (float) (y + h),
                new Color(0x5A1E18)));
        g.fill(new Rectangle2D.Double(x + 10, y + 16, w - 20, h - 32));
        g.setColor(new Color(0x3E1410));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Line2D.Double(x + w / 2, y + 16, x + w / 2, y + h - 16));
        g.setColor(new Color(0x3A2616));                    // the gate, facing the enemies
        g.fill(new Rectangle2D.Double(x - 1, y + h / 2 - 5, 5, 10));
        g.setColor(new Color(0x5A4632));
        g.fill(new Rectangle2D.Double(x + w / 2 - 0.5, y + 4, 1, 12));
        g.setColor(new Color(0x2F5FB8));
        Path2D flag = new Path2D.Double();
        flag.moveTo(x + w / 2, y + 4);
        flag.lineTo(x + w / 2 + 9, y + 6);
        flag.lineTo(x + w / 2 + 7, y + 8);
        flag.lineTo(x + w / 2 + 9, y + 10);
        flag.lineTo(x + w / 2, y + 10);
        flag.closePath();
        g.fill(flag);
        g.setColor(new Color(0xF2D24A));
        g.fill(new Ellipse2D.Double(x + w / 2 + 2.5, y + 5.8, 2.4, 2.4));
    }

    private static void stoneBlock(Graphics2D g, double x, double y, double w, double h) {
        g.setPaint(new GradientPaint((float) x, (float) y, new Color(0xB8B0A2), (float) (x + w), (float) (y + h),
                new Color(0x7A7266)));
        g.fill(new Rectangle2D.Double(x, y, w, h));
        g.setColor(new Color(0x5E574D));
        g.setStroke(new BasicStroke(0.35f));
        for (double yy = y + 3; yy < y + h; yy += 3) g.draw(new Line2D.Double(x, yy, x + w, yy));
        for (double yy = y; yy < y + h; yy += 3) {
            for (double xx = x + ((int) ((yy - y) / 3) % 2 == 0 ? 2 : 4.5); xx < x + w; xx += 5) {
                g.draw(new Line2D.Double(xx, yy, xx, Math.min(yy + 3, y + h)));
            }
        }
    }

    private static void roundTower(Graphics2D g, double mx, double my, double r) {
        g.setPaint(radial(mx - r * 0.4, my - r * 0.4, r * 1.3, new float[]{0, 1}, new Color(0xCFC8BA),
                new Color(0x6E665A)));
        g.fill(new Ellipse2D.Double(mx - r, my - r, r * 2, r * 2));
        g.setPaint(new GradientPaint((float) (mx - r), (float) (my - r), new Color(0xA24636), (float) (mx + r),
                (float) (my + r), new Color(0x5A1E18)));
        Path2D cone = new Path2D.Double();
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            if (i == 0) cone.moveTo(mx + Math.cos(a) * r * 0.75, my + Math.sin(a) * r * 0.75);
            else cone.lineTo(mx + Math.cos(a) * r * 0.75, my + Math.sin(a) * r * 0.75);
        }
        cone.closePath();
        g.fill(cone);
        g.setColor(new Color(0x3E1410));
        g.setStroke(new BasicStroke(0.4f));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            g.draw(new Line2D.Double(mx, my, mx + Math.cos(a) * r * 0.75, my + Math.sin(a) * r * 0.75));
        }
    }

    // ---- the trail -------------------------------------------------------------------------------

    // --8<-- [start:trail]
    /**
     * The trail along {@code way}, lines from the middle of a tile to the middle of the next: a dark rim
     * where the grass is trodden down, a lighter edge, and the bare earth in the middle. Each width is one
     * stroke of the whole way, so crossings and bends are not drawn darker twice.
     */
    static void trail(Graphics2D g, Shape way) {
        g.setColor(DIRT_EDGE);
        g.setStroke(new BasicStroke(17, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(way);
        g.setColor(DIRT_RIM);
        g.setStroke(new BasicStroke(15, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(way);
        g.setColor(DIRT);
        g.setStroke(new BasicStroke(13, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(way);
    }
    // --8<-- [end:trail]

    // --8<-- [start:road]
    /**
     * The road over {@code tiles}, the union of its tiles' squares: with its corners rounded, a band of trodden
     * grass along its edges, and the bare earth inside.
     */
    static void road(Graphics2D g, Area tiles) {
        if (tiles.isEmpty()) return;
        Area road = opened(closed(tiles, ROAD_ROUNDING), ROAD_ROUNDING);
        g.setColor(DIRT_RIM);
        g.fill(road);
        g.setColor(DIRT);
        g.fill(eroded(road, 2.5));
        g.setColor(DIRT_EDGE);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(road);
    }

    /** Rounds the inner corners of a shape: grown by {@code r} and shrunk again. */
    private static Area closed(Area area, double r) {
        return eroded(grown(area, r), r);
    }

    /** Rounds the outer corners: shrunk by {@code r} and grown again. */
    private static Area opened(Area area, double r) {
        return grown(eroded(area, r), r);
    }

    private static Area grown(Area area, double r) {
        Area grown = new Area(area);
        grown.add(new Area(new BasicStroke((float) (2 * r), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                .createStrokedShape(area)));
        return grown;
    }

    private static Area eroded(Area area, double r) {
        Area eroded = new Area(area);
        eroded.subtract(new Area(new BasicStroke((float) (2 * r), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                .createStrokedShape(area)));
        return eroded;
    }
    // --8<-- [end:road]

    /** Pebbles and darker earth on the trail tile whose middle is {@code mx}, {@code my}. */
    static void pebbles(Graphics2D g, double mx, double my, Random random) {
        g.setColor(new Color(0x8C6A42));
        for (int i = 0; i < 3; i++) {
            double r = 1.5 + random.nextDouble() * 2;
            g.fill(new Ellipse2D.Double(mx - 5 + random.nextDouble() * 10 - r, my - 5 + random.nextDouble() * 10 - r,
                    2 * r, 1.6 * r));
        }
        Color[] stones = {new Color(0x9C9282), new Color(0xB8AE9C), new Color(0x7C705E)};
        Color under = new Color(0x4A3A26);
        for (int i = 0; i < 4; i++) {
            double x = mx - 5 + random.nextDouble() * 10;
            double y = my - 5 + random.nextDouble() * 10;
            double s = 1 + random.nextDouble() * 1.2;
            g.setColor(under);
            g.fill(new Ellipse2D.Double(x + 0.4, y + 0.4, s, s * 0.8));
            g.setColor(stones[random.nextInt(stones.length)]);
            g.fill(new Ellipse2D.Double(x, y, s, s * 0.8));
        }
    }

    // ---- the towers ------------------------------------------------------------------------------

    static void towerShadow(Graphics2D g, double mx, double my) {
        g.setColor(new Color(10, 20, 5, 95));
        g.fill(new Ellipse2D.Double(mx - 9 + SHADOW_X * 2.2, my - 9 + SHADOW_Y * 2.2, 18, 18));
    }

    /**
     * A round stone tower seen from above, its middle at {@code mx}, {@code my}: battlements around a
     * plank floor. A higher {@code stage} has brass bands on its wall, one for each stage above the first.
     */
    static void towerBase(Graphics2D g, double mx, double my, String kind, int stage) {
        Color floorA = switch (kind) {
            case "cannon" -> new Color(0x8A6A48);
            case "frost" -> new Color(0x6C7C8C);
            default -> new Color(0x9C7A50);
        };
        Color floorB = switch (kind) {
            case "cannon" -> new Color(0x5A4228);
            case "frost" -> new Color(0x3C4A58);
            default -> new Color(0x6A4E30);
        };
        double big = 9.4;
        g.setPaint(radial(mx - 4, my - 4, big * 1.6, new float[]{0, 1}, new Color(0xD2CABB), new Color(0x625A4E)));
        g.fill(new Ellipse2D.Double(mx - big, my - big, big * 2, big * 2));
        g.setColor(new Color(0x4F483F));
        g.setStroke(new BasicStroke(0.35f));
        g.draw(new Ellipse2D.Double(mx - big + 1.6, my - big + 1.6, (big - 1.6) * 2, (big - 1.6) * 2));
        if (stage > 1) {
            g.setColor(new Color(0xC89A3A));
            g.setStroke(new BasicStroke(0.7f));
            for (int i = 1; i < stage; i++) {
                double r = big - 0.4 - (i - 1) * 0.9;
                g.draw(new Ellipse2D.Double(mx - r, my - r, 2 * r, 2 * r));
            }
        }
        Color merlonShadow = new Color(0x3A342D);
        Color merlonLight = new Color(0xE0D8C8);
        Color merlonDark = new Color(0x8C8374);
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5 + 0.15;
            double ex = mx + Math.cos(a) * (big - 1.4);
            double ey = my + Math.sin(a) * (big - 1.4);
            AffineTransform before = g.getTransform();
            g.rotate(a, ex, ey);
            g.setColor(merlonShadow);
            g.fill(new Rectangle2D.Double(ex - 0.7, ey - 1.2, 2.6, 3.2));
            g.setPaint(new GradientPaint((float) (ex - 1), (float) (ey - 1.5), merlonLight, (float) (ex + 1.4),
                    (float) (ey + 1.5), merlonDark));
            g.fill(new Rectangle2D.Double(ex - 1.1, ey - 1.6, 2.4, 3.0));
            g.setTransform(before);
        }
        double fr = big - 3.2;
        Shape floor = new Ellipse2D.Double(mx - fr, my - fr, fr * 2, fr * 2);
        g.setPaint(new GradientPaint((float) (mx - fr), (float) (my - fr), floorA, (float) (mx + fr), (float) (my + fr),
                floorB));
        g.fill(floor);
        Shape clip = g.getClip();
        g.clip(floor);
        g.setColor(new Color(0, 0, 0, 60));
        g.setStroke(new BasicStroke(0.3f));
        for (double yy = my - fr; yy < my + fr; yy += 1.8) g.draw(new Line2D.Double(mx - fr, yy, mx + fr, yy));
        g.setPaint(radial(mx + 1.2, my + 1.2, fr * 1.15, new float[]{0.6f, 1}, new Color(0, 0, 0, 0),
                new Color(0, 0, 0, 130)));
        g.fill(floor);
        g.setClip(clip);
    }

    /** A cast iron cannon on a wooden carriage, its trunnions at {@code mx}, {@code my}, pointing right. */
    static void cannon(Graphics2D g, double mx, double my) {
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Double(mx - 4.8, my - 3, 8.5, 7.2, 1.5, 1.5));
        g.setPaint(new GradientPaint((float) mx, (float) (my - 3.6), new Color(0x7A5230), (float) mx, (float) (my + 3.6),
                new Color(0x3E2814)));
        g.fill(new RoundRectangle2D.Double(mx - 5.4, my - 3.6, 8.5, 1.8, 0.8, 0.8));
        g.fill(new RoundRectangle2D.Double(mx - 5.4, my + 1.8, 8.5, 1.8, 0.8, 0.8));
        g.setColor(new Color(0x2A1C10));
        g.fill(new Rectangle2D.Double(mx - 0.6, my - 4.6, 1.2, 9.2));
        for (double side : new double[]{-1, 1}) {           // the wheels, edge on from above
            g.setPaint(new GradientPaint((float) (mx - 2.8), (float) my, new Color(0x5A3A1E), (float) (mx + 2.8),
                    (float) my, new Color(0x8A6038)));
            g.fill(new RoundRectangle2D.Double(mx - 2.8, my + side * 4.6 - 0.9, 5.6, 1.8, 0.9, 0.9));
            g.setColor(new Color(0x2E2E2E));
            g.fill(new Rectangle2D.Double(mx - 2.8, my + side * 4.6 - 0.9, 5.6, 0.35));
        }
        Path2D barrel = new Path2D.Double();
        barrel.moveTo(mx - 4.2, my - 2.1);
        barrel.lineTo(mx + 8.8, my - 1.45);
        barrel.lineTo(mx + 8.8, my + 1.45);
        barrel.lineTo(mx - 4.2, my + 2.1);
        barrel.closePath();
        g.setColor(new Color(0, 0, 0, 80));
        g.fill(barrel.createTransformedShape(AffineTransform.getTranslateInstance(0.7, 0.9)));
        g.setPaint(new LinearGradientPaint(new Point2D.Double(mx, my - 2.1), new Point2D.Double(mx, my + 2.1),
                new float[]{0, 0.3f, 0.55f, 1}, new Color[]{new Color(0x3A3C40), new Color(0x9EA4AC),
                new Color(0x4A4E54), new Color(0x16181A)}));
        g.fill(barrel);
        g.setPaint(new LinearGradientPaint(new Point2D.Double(mx, my - 2.3), new Point2D.Double(mx, my + 2.3),
                new float[]{0, 0.3f, 1}, new Color[]{new Color(0x2A2C30), new Color(0xB4BAC2), new Color(0x101214)}));
        g.fill(new RoundRectangle2D.Double(mx - 1.4, my - 2.0, 1.0, 4.0, 0.4, 0.4));
        g.fill(new RoundRectangle2D.Double(mx + 3.2, my - 1.75, 0.8, 3.5, 0.4, 0.4));
        g.fill(new RoundRectangle2D.Double(mx + 8.0, my - 1.95, 1.6, 3.9, 0.6, 0.6));
        g.setColor(new Color(0x050505));
        g.fill(new Ellipse2D.Double(mx + 9.0, my - 0.95, 0.9, 1.9));
        g.setPaint(radial(mx - 5.2, my - 0.5, 1.4, new float[]{0, 1}, new Color(0xA8AEB6), new Color(0x1A1C1E)));
        g.fill(new Ellipse2D.Double(mx - 6.0, my - 1.0, 2.0, 2.0));
    }

    /** An archer in a green hood with a drawn longbow, at {@code mx}, {@code my}, aiming right. */
    static void archer(Graphics2D g, double mx, double my) {
        g.setColor(new Color(0x5A3A1E));                    // the quiver
        g.fill(new Rectangle2D.Double(mx - 5, my + 2.8, 3, 1.6));
        g.setColor(new Color(0xE6DCC4));
        g.fill(new Rectangle2D.Double(mx - 5.6, my + 2.9, 0.6, 1.4));
        g.setPaint(radial(mx - 1, my - 1, 4, new float[]{0, 1}, new Color(0x4E7A3A), new Color(0x22381A)));
        g.fill(new Ellipse2D.Double(mx - 2.4, my - 3.4, 4.4, 6.8));
        g.setPaint(radial(mx - 0.4, my - 0.6, 2.2, new float[]{0, 1}, new Color(0x6A8F4A), new Color(0x2E4A22)));
        g.fill(new Ellipse2D.Double(mx - 1.6, my - 1.6, 3.2, 3.2));
        g.setColor(new Color(0x7A4E22));
        g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(mx + 1.5, my - 5.5, 6, 11, -75, 150, Arc2D.OPEN));
        g.setColor(new Color(0xEDE6D2));
        g.setStroke(new BasicStroke(0.3f));
        g.draw(new Line2D.Double(mx + 5.4, my - 5.3, mx + 1.2, my));
        g.draw(new Line2D.Double(mx + 1.2, my, mx + 5.4, my + 5.3));
        g.setColor(new Color(0xC8A878));
        g.setStroke(new BasicStroke(0.45f));
        g.draw(new Line2D.Double(mx + 1.2, my, mx + 8.6, my));
        g.setColor(new Color(0x9AA0A6));
        Path2D head = new Path2D.Double();
        head.moveTo(mx + 9.6, my);
        head.lineTo(mx + 8.2, my - 0.8);
        head.lineTo(mx + 8.2, my + 0.8);
        head.closePath();
        g.fill(head);
    }

    /** The frost tower's crystal, five icy shards around a white core, at {@code mx}, {@code my}. */
    static void frost(Graphics2D g, double mx, double my) {
        g.setPaint(radial(mx, my, 9, new float[]{0, 1}, new Color(160, 230, 255, 170), new Color(160, 230, 255, 0)));
        g.fill(new Ellipse2D.Double(mx - 9, my - 9, 18, 18));
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5;
            double length = i == 0 ? 6.4 : 4.4;
            AffineTransform before = g.getTransform();
            g.rotate(a, mx, my);
            Path2D shard = new Path2D.Double();
            shard.moveTo(mx, my - 1.1);
            shard.lineTo(mx + length * 0.75, my - 0.9);
            shard.lineTo(mx + length, my);
            shard.lineTo(mx + length * 0.75, my + 0.9);
            shard.lineTo(mx, my + 1.1);
            shard.closePath();
            g.setPaint(new LinearGradientPaint(new Point2D.Double(mx, my - 1), new Point2D.Double(mx, my + 1),
                    new float[]{0, 0.5f, 1}, new Color[]{new Color(0xE8FBFF), new Color(0x7FD0F0), new Color(0x2A7AA8)}));
            g.fill(shard);
            g.setTransform(before);
        }
        g.setColor(new Color(0xF4FFFF));
        g.fill(new Ellipse2D.Double(mx - 1.2, my - 1.2, 2.4, 2.4));
    }

    /** A whole tower of a kind, base and turret, for the menu; the turret looks up and right. */
    static void tower(Graphics2D g, double mx, double my, String kind, int stage) {
        towerBase(g, mx, my, kind, stage);
        AffineTransform before = g.getTransform();
        g.rotate(-0.5, mx, my);
        switch (kind) {
            case "cannon" -> cannon(g, mx, my);
            case "frost" -> frost(g, mx, my);
            default -> archer(g, mx, my);
        }
        g.setTransform(before);
    }

    // ---- the enemies -----------------------------------------------------------------------------

    /** How much larger the enemies are drawn than the figures below: they must read at a glance. */
    static final double CREEP_SCALE = 1.55;

    /** An enemy of {@code unit} at {@code x}, {@code y}, facing right. */
    static void creep(Graphics2D g, String unit, double x, double y) {
        AffineTransform before = g.getTransform();
        g.translate(x, y);
        g.scale(CREEP_SCALE, CREEP_SCALE);
        switch (unit) {
            case "wolf" -> wolf(g, 0, 0);
            case "troll" -> troll(g, 0, 0);
            default -> orc(g, 0, 0);
        }
        g.setTransform(before);
    }

    /** The size in units an enemy's picture needs, turned any way. */
    static double creepSize(String unit) {
        return switch (unit) {
            case "wolf" -> 28;
            case "troll" -> 30;
            default -> 24;
        };
    }

    static void creepShadow(Graphics2D g, String unit, double x, double y) {
        double size = switch (unit) {
            case "wolf" -> 12;
            case "troll" -> 15;
            default -> 10;
        } * CREEP_SCALE;
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new Ellipse2D.Double(x - size * 0.45, y - size * 0.4, size * 0.9, size * 0.8));
    }

    /** Glowing red eyes, {@code gap} apart from the middle line. */
    private static void eyes(Graphics2D g, double ex, double ey, double gap) {
        g.setPaint(radial(ex, ey, 2.6, new float[]{0, 1}, new Color(255, 40, 10, 210), new Color(255, 40, 10, 0)));
        g.fill(new Ellipse2D.Double(ex - 2.6, ey - 2.6, 5.2, 5.2));
        g.setColor(new Color(0xFF2A0A));
        g.fill(new Ellipse2D.Double(ex - 0.6, ey - gap - 0.55, 1.2, 1.1));
        g.fill(new Ellipse2D.Double(ex - 0.6, ey + gap - 0.55, 1.2, 1.1));
        g.setColor(new Color(0xFFE070));
        g.fill(new Ellipse2D.Double(ex - 0.25, ey - gap - 0.25, 0.5, 0.5));
        g.fill(new Ellipse2D.Double(ex - 0.25, ey + gap - 0.25, 0.5, 0.5));
    }

    /** An orc raider in leather with spiked shoulders and an axe. */
    static void orc(Graphics2D g, double x, double y) {
        g.setColor(new Color(0x4A3020));
        g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x - 1, y + 3.8, x + 5, y + 3.2));
        Path2D blade = new Path2D.Double();
        blade.moveTo(x + 4, y + 3.2);
        blade.quadTo(x + 5.8, y + 6.6, x + 3.4, y + 7.2);
        blade.lineTo(x + 3, y + 3.4);
        blade.closePath();
        g.setPaint(new GradientPaint((float) x, (float) (y + 3), new Color(0xC8CCD0), (float) x, (float) (y + 7),
                new Color(0x5A5E62)));
        g.fill(blade);
        g.setPaint(radial(x - 1, y - 1, 5, new float[]{0, 1}, new Color(0x4A3A2A), new Color(0x140E08)));
        g.fill(new Ellipse2D.Double(x - 3.6, y - 4.2, 6.4, 8.4));
        for (double side : new double[]{-1, 1}) {
            g.setPaint(radial(x - 0.5, y + side * 3.2 - 0.5, 2.2, new float[]{0, 1}, new Color(0x9A9EA4),
                    new Color(0x2C2E32)));
            g.fill(new Ellipse2D.Double(x - 1.9, y + side * 3.2 - 1.6, 3.4, 3.2));
            g.setColor(new Color(0xD8DCE0));
            Path2D spike = new Path2D.Double();
            spike.moveTo(x - 1, y + side * 4.2);
            spike.lineTo(x - 0.4, y + side * 6.4);
            spike.lineTo(x + 0.2, y + side * 4.2);
            spike.closePath();
            g.fill(spike);
        }
        g.setPaint(radial(x + 1.2, y - 0.7, 2.6, new float[]{0, 1}, new Color(0x8EB03A), new Color(0x2E4A10)));
        g.fill(new Ellipse2D.Double(x - 0.6, y - 2.4, 4.8, 4.8));
        g.setColor(new Color(0xF0E8D0));                    // tusks
        g.fill(new Ellipse2D.Double(x + 3.3, y - 1.5, 1.2, 0.6));
        g.fill(new Ellipse2D.Double(x + 3.3, y + 0.9, 1.2, 0.6));
        eyes(g, x + 2.7, y, 0.8);
    }

    /** A dire wolf in mid stride, with a dark mane and fangs. */
    static void wolf(Graphics2D g, double x, double y) {
        g.setColor(new Color(0x2A2624));
        Path2D tail = new Path2D.Double();
        tail.moveTo(x - 4, y - 0.8);
        tail.quadTo(x - 8, y - 2.4, x - 8.6, y + 0.4);
        tail.quadTo(x - 7, y + 0.6, x - 4, y + 0.8);
        tail.closePath();
        g.fill(tail);
        g.setColor(new Color(0x1E1A18));
        g.fill(new Ellipse2D.Double(x + 1.5, y - 3.6, 2.6, 1.3));
        g.fill(new Ellipse2D.Double(x - 0.5, y + 2.3, 2.6, 1.3));
        g.fill(new Ellipse2D.Double(x - 4.2, y - 3.2, 2.6, 1.3));
        g.fill(new Ellipse2D.Double(x - 3.2, y + 2.0, 2.6, 1.3));
        g.setPaint(radial(x - 1, y - 1, 5.5, new float[]{0, 1}, new Color(0x6A625A), new Color(0x221E1C)));
        g.fill(new Ellipse2D.Double(x - 5, y - 2.6, 9.5, 5.2));
        g.setColor(new Color(0x161210));
        for (int i = 0; i < 5; i++) {
            Path2D tuft = new Path2D.Double();
            double tx = x + 2 - i * 1.2;
            tuft.moveTo(tx, y - 0.5);
            tuft.lineTo(tx - 1.8, y - 1.6 + (i % 2) * 0.4);
            tuft.lineTo(tx - 0.8, y);
            tuft.lineTo(tx - 1.8, y + 1.6 - (i % 2) * 0.4);
            tuft.lineTo(tx, y + 0.5);
            tuft.closePath();
            g.fill(tuft);
        }
        g.setPaint(radial(x + 4.6, y - 0.6, 2.6, new float[]{0, 1}, new Color(0x7A726A), new Color(0x2A2624)));
        g.fill(new Ellipse2D.Double(x + 3, y - 2, 4, 4));
        g.fill(new Ellipse2D.Double(x + 5.6, y - 1.1, 2.8, 2.2));
        g.setColor(new Color(0x2A2624));
        for (double side : new double[]{-1, 1}) {
            Path2D ear = new Path2D.Double();
            ear.moveTo(x + 3.4, y + side * 1.4);
            ear.lineTo(x + 2.2, y + side * 2.8);
            ear.lineTo(x + 4.4, y + side * 1.8);
            ear.closePath();
            g.fill(ear);
        }
        g.setColor(new Color(0x8A1A14));
        g.fill(new Ellipse2D.Double(x + 7.4, y - 0.5, 1.4, 1.0));
        g.setColor(Color.WHITE);
        g.fill(new Rectangle2D.Double(x + 7.6, y - 0.8, 0.5, 0.3));
        g.fill(new Rectangle2D.Double(x + 7.6, y + 0.5, 0.5, 0.3));
        eyes(g, x + 5.2, y, 0.9);
    }

    /** An armoured troll with a horned helmet and a spiked club. */
    static void troll(Graphics2D g, double x, double y) {
        g.setColor(new Color(0x4A3220));
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x, y + 5.5, x + 6, y + 7.5));
        g.setPaint(radial(x + 6.5, y + 7.2, 2.6, new float[]{0, 1}, new Color(0x7A5636), new Color(0x2E1E10)));
        g.fill(new Ellipse2D.Double(x + 4.6, y + 5.6, 4.2, 3.6));
        g.setColor(new Color(0xC8CCD0));
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            double sx = x + 6.7 + Math.cos(a) * 2.1;
            double sy = y + 7.4 + Math.sin(a) * 1.8;
            g.fill(new Ellipse2D.Double(sx - 0.45, sy - 0.45, 0.9, 0.9));
        }
        g.setPaint(radial(x - 1.5, y - 1.5, 8, new float[]{0, 1}, new Color(0x7A6E80), new Color(0x2A2230)));
        g.fill(new Ellipse2D.Double(x - 5.6, y - 6.6, 10.4, 13.2));
        g.fill(new Ellipse2D.Double(x - 1.5, y - 8, 5, 3));
        g.fill(new Ellipse2D.Double(x - 1.5, y + 4.6, 5, 3));
        g.setPaint(new LinearGradientPaint(new Point2D.Double(x - 4, y - 4), new Point2D.Double(x + 2, y + 4),
                new float[]{0, 0.4f, 1}, new Color[]{new Color(0x8A8E94), new Color(0xC2C6CC), new Color(0x3A3C40)}));
        g.fill(new RoundRectangle2D.Double(x - 4.2, y - 4, 4.4, 8, 1.4, 1.4));
        g.setColor(new Color(0x2A2C30));
        g.setStroke(new BasicStroke(0.35f));
        g.draw(new Line2D.Double(x - 4.2, y - 1.4, x + 0.2, y - 1.4));
        g.draw(new Line2D.Double(x - 4.2, y + 1.4, x + 0.2, y + 1.4));
        g.setColor(new Color(0xD0D4D8));
        for (double[] r : new double[][]{{-3.4, -3.2}, {-0.9, -3.2}, {-3.4, 3.2}, {-0.9, 3.2}}) {
            g.fill(new Ellipse2D.Double(x + r[0] - 0.35, y + r[1] - 0.35, 0.7, 0.7));
        }
        g.setPaint(radial(x + 2.4, y - 0.8, 3.4, new float[]{0, 1}, new Color(0x8A8E94), new Color(0x2C2E32)));
        g.fill(new Ellipse2D.Double(x + 0.4, y - 2.8, 5.6, 5.6));
        g.setColor(new Color(0xE8DFC6));
        for (double side : new double[]{-1, 1}) {
            Path2D horn = new Path2D.Double();
            horn.moveTo(x + 2.4, y + side * 2.0);
            horn.quadTo(x + 1.6, y + side * 5.0, x + 5.6, y + side * 5.4);
            horn.quadTo(x + 3.0, y + side * 4.0, x + 3.8, y + side * 2.2);
            horn.closePath();
            g.fill(horn);
        }
        eyes(g, x + 4.6, y, 1.1);
    }

    /** Frost on an enemy: a pale glaze and two glints. */
    static void rime(Graphics2D g, double x, double y, double r) {
        g.setColor(new Color(190, 240, 255, 110));
        g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        g.setColor(new Color(240, 255, 255, 200));
        g.fill(new Ellipse2D.Double(x - r * 0.6, y - r * 0.7, 1, 1));
        g.fill(new Ellipse2D.Double(x + r * 0.4, y + r * 0.2, 1, 1));
    }

    // ---- shots -----------------------------------------------------------------------------------

    /** An arrow in flight, its middle at {@code x}, {@code y}, flying right, with its shadow below. */
    static void arrow(Graphics2D g, double x, double y) {
        g.setStroke(new BasicStroke(0.6f));
        g.setColor(new Color(0xD8C49A));
        g.draw(new Line2D.Double(x - 4, y, x + 4, y));
        g.setColor(new Color(0xB03A2A));
        g.draw(new Line2D.Double(x - 4.5, y - 0.8, x - 3, y));
        g.draw(new Line2D.Double(x - 4.5, y + 0.8, x - 3, y));
        g.setColor(new Color(0x9AA0A6));
        Path2D head = new Path2D.Double();
        head.moveTo(x + 5.6, y);
        head.lineTo(x + 3.8, y - 1);
        head.lineTo(x + 3.8, y + 1);
        head.closePath();
        g.fill(head);
    }

    static void arrowShadow(Graphics2D g, double x, double y) {
        g.setColor(new Color(0, 0, 0, 60));
        g.setStroke(new BasicStroke(0.6f));
        g.draw(new Line2D.Double(x - 4, y, x + 4, y));
    }

    /** A cannon ball of {@code size} units around {@code x}, {@code y}. */
    static void ball(Graphics2D g, double x, double y, double size) {
        double r = size / 2;
        g.setPaint(radial(x - r / 3, y - r / 3, r * 1.2, new float[]{0, 1}, new Color(0x8A8E94), new Color(0x0A0A0A)));
        g.fill(new Ellipse2D.Double(x - r, y - r, size, size));
    }

    /** A round spot of {@code size} units, dark in the middle and fading out: shadows and scorch marks. */
    static void spot(Graphics2D g, double x, double y, double size, Color colour) {
        double r = size / 2;
        g.setPaint(radial(x, y, r, new float[]{0, 0.6f, 1}, colour, colour,
                new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 0)));
        g.fill(new Ellipse2D.Double(x - r, y - r, size, size));
    }

    /** The flash at a cannon's muzzle, pointing right from {@code x}, {@code y}. */
    static void flash(Graphics2D g, double x, double y) {
        g.setPaint(radial(x + 2.5, y, 4.5, new float[]{0, 0.35f, 1}, new Color(255, 250, 210, 255),
                new Color(255, 170, 40, 220), new Color(255, 80, 0, 0)));
        g.fill(new Ellipse2D.Double(x - 2, y - 4.5, 9, 9));
    }

    /** A burst: a hot white middle, fire, and a fringe that fades out. */
    static void fireball(Graphics2D g, double x, double y, double r, double fade) {
        int a = (int) Math.round(255 * fade);
        g.setPaint(radial(x, y, r, new float[]{0, 0.25f, 0.6f, 1}, new Color(255, 255, 230, a),
                new Color(255, 210, 90, a), new Color(240, 90, 20, a * 200 / 255), new Color(120, 30, 10, 0)));
        g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
    }

    // ---- the overlay of the flow field -----------------------------------------------------------

    /** An arrow across a tile's middle at {@code x}, {@code y}, along {@code dx}, {@code dy}. */
    static void flowArrow(Graphics2D g, double x, double y, int dx, int dy, Color colour) {
        double length = Math.hypot(dx, dy);
        double ux = dx / length;
        double uy = dy / length;
        g.setColor(colour);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x - ux * 5, y - uy * 5, x + ux * 4, y + uy * 4));
        Path2D head = new Path2D.Double();
        head.moveTo(x + ux * 6.5, y + uy * 6.5);
        head.lineTo(x + ux * 2.5 - uy * 3, y + uy * 2.5 + ux * 3);
        head.lineTo(x + ux * 2.5 + uy * 3, y + uy * 2.5 - ux * 3);
        head.closePath();
        g.fill(head);
    }

    // ---- the bar at the top ----------------------------------------------------------------------

    /** A gold coin of {@code size} units at {@code x}, {@code y}. */
    static void coin(Graphics2D g, double x, double y, double size) {
        g.setPaint(radial(x + size * 0.35, y + size * 0.35, size * 0.6, new float[]{0, 1}, new Color(0xFFE88A),
                new Color(0xB8861A)));
        g.fill(new Ellipse2D.Double(x, y, size, size));
        g.setColor(new Color(0x8A6210));
        g.setStroke(new BasicStroke((float) (size / 15)));
        g.draw(new Ellipse2D.Double(x + size * 0.21, y + size * 0.21, size * 0.58, size * 0.58));
    }

    /** A red heart of about {@code size} units at {@code x}, {@code y}. */
    static void heart(Graphics2D g, double x, double y, double size) {
        double s = size / 12;
        Path2D h = new Path2D.Double();
        h.moveTo(x + 6 * s, y + 11 * s);
        h.curveTo(x - 3 * s, y + 4 * s, x + s, y - 2 * s, x + 6 * s, y + 2.5 * s);
        h.curveTo(x + 11 * s, y - 2 * s, x + 15 * s, y + 4 * s, x + 6 * s, y + 11 * s);
        g.setPaint(new GradientPaint((float) x, (float) y, new Color(0xF0604A), (float) x, (float) (y + 11 * s),
                new Color(0x9A1A10)));
        g.fill(h);
    }

    /** A gold star with five points around {@code x}, {@code y}; a grey one if not {@code won}. */
    static void star(Graphics2D g, double x, double y, double r, boolean won) {
        Path2D star = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            double d = i % 2 == 0 ? r : r * 0.45;
            if (i == 0) star.moveTo(x + Math.cos(a) * d, y + Math.sin(a) * d);
            else star.lineTo(x + Math.cos(a) * d, y + Math.sin(a) * d);
        }
        star.closePath();
        if (won) {
            g.setPaint(radial(x - r / 3, y - r / 3, r * 1.3, new float[]{0, 1}, new Color(0xFFF2A0),
                    new Color(0xD89A20)));
        } else {
            g.setColor(new Color(0x4A4440));
        }
        g.fill(star);
        g.setColor(won ? new Color(0x8A5A10) : new Color(0x2A2420));
        g.setStroke(new BasicStroke((float) (r / 10)));
        g.draw(star);
    }
}
