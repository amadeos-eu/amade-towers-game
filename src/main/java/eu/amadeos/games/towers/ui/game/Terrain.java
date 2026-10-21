package eu.amadeos.games.towers.ui.game;

import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.towers.domain.Field;
import eu.amadeos.games.towers.domain.Ground;
import eu.amadeos.games.towers.domain.Level;
import eu.amadeos.games.towers.domain.Mode;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The ground of a level: grass, water, rocks, the shadows of the trees, the lair, the castle, and the road.
 * On open ground there is no road and no path: the enemies' way shows only in where they walk.
 * All of it lies still, so it is drawn once into sprites, in squares of {@value #CHUNK} × {@value #CHUNK}
 * tiles, and in each frame only copied. A square is drawn when the camera first sees it, so a battle starts
 * without drawing the whole map. The squares are {@link Sprite#opaque() opaque}: they cover everything below
 * them, and the copy needs no blending.
 */
// --8<-- [start:terrain]
final class Terrain {
    static final int CHUNK = 8;
    private static final int SIZE = CHUNK * Field.TILE;

    private final Level level;
    private final Mode mode;
    private final BufferedImage grass;
    private final int across;
    private final Sprite[] squares;

    Terrain(Level level, Mode mode) {
        this.level = level;
        this.mode = mode;
        this.grass = Art.grass(level.width() * Field.TILE, level.height() * Field.TILE, level.number());
        this.across = (level.width() + CHUNK - 1) / CHUNK;
        int down = (level.height() + CHUNK - 1) / CHUNK;
        squares = new Sprite[across * down];
        for (int i = 0; i < squares.length; i++) squares[i] = picture(i % across * CHUNK, i / across * CHUNK);
    }
    // --8<-- [end:terrain]

    // --8<-- [start:render]
    /**
     * The squares the camera sees. A square is a unit larger than its tiles, to the right and below, so
     * that rounding to whole pixels never leaves a gap between two; the next square covers the overlap.
     */
    void render(Graphics2D g, Camera camera, double alpha) {
        for (int i = 0; i < squares.length; i++) {
            double x = i % across * CHUNK * Field.TILE;
            double y = i / across * CHUNK * Field.TILE;
            if (camera.sees(x, y, SIZE + 1, SIZE + 1, alpha)) squares[i].draw(g, x, y);
        }
    }
    // --8<-- [end:render]

    /** The picture of the square from tile {@code col}, {@code row}; drawn when it is first shown. */
    private Sprite picture(int col, int row) {
        double x0 = col * Field.TILE;
        double y0 = row * Field.TILE;
        double w = Math.min(SIZE + 1, level.width() * Field.TILE - x0);
        double h = Math.min(SIZE + 1, level.height() * Field.TILE - y0);
        return Sprite.drawn(w, h, (g, width, height) -> {
            g.translate(-x0, -y0);
            g.clip(new Rectangle2D.Double(x0, y0, width, height));
            draw(g, col, row, x0, y0, width, height);
        }).opaque();
    }

    // --8<-- [start:draw]
    private void draw(Graphics2D g, int col0, int row0, double x0, double y0, double w, double h) {
        int p = Art.GRASS_PIXELS;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(grass, (int) x0, (int) y0, (int) (x0 + w), (int) (y0 + h),
                (int) x0 * p, (int) y0 * p, (int) (x0 + w) * p, (int) (y0 + h) * p, null);
        int fromCol = col0 - 2;
        int toCol = col0 + CHUNK + 1;
        int fromRow = row0 - 2;
        int toRow = row0 + CHUNK + 1;
        Area pond = new Area();
        List<Double> ripples = new ArrayList<>();
        for (int row = fromRow; row <= toRow; row++) {
            for (int col = fromCol; col <= toCol; col++) {
                if (level.contains(col, row) && level.ground(col, row) == Ground.WATER) {
                    pond.add(new Area(new RoundRectangle2D.Double(col * Field.TILE - 1, row * Field.TILE - 1,
                            Field.TILE + 2, Field.TILE + 2, 12, 12)));
                    ripples.add((double) col * Field.TILE);
                    ripples.add((double) row * Field.TILE);
                }
            }
        }
        if (!pond.isEmpty()) Art.water(g, pond, ripples.stream().mapToDouble(Double::doubleValue).toArray());
        if (mode == Mode.ROAD) Art.road(g, road(fromCol, fromRow, toCol, toRow));
        for (int row = fromRow; row <= toRow; row++) {
            for (int col = fromCol; col <= toCol; col++) {
                if (!level.contains(col, row)) continue;
                double x = col * Field.TILE;
                double y = row * Field.TILE;
                Random random = new Random(col * 7919L + row * 104729L + level.number());
                switch (level.ground(col, row)) {
                    case ROCK -> Art.rock(g, x, y, random);
                    case TREE -> Art.treeShadow(g, x, y, false);
                    case PINE -> Art.treeShadow(g, x, y, true);
                    case ROAD -> {
                        if (mode == Mode.ROAD) Art.pebbles(g, x + 10, y + 10, random);
                    }
                    default -> {
                    }
                }
            }
        }
        for (Cell lair : level.lairs()) Art.lair(g, lair.col() * Field.TILE, (lair.row() + 0.5) * Field.TILE);
        Level.Box castle = level.castleBox();
        Art.castle(g, castle.x() + 3, castle.y() + 3, castle.width() - 6, castle.height() - 6);
    }
    // --8<-- [end:draw]

    /**
     * The road's tiles, with the lairs and the castle it leads from and to; where it meets the edge of the
     * map it goes on beyond it, so that it does not end in a rounded tip at the edge.
     */
    private Area road(int fromCol, int fromRow, int toCol, int toRow) {
        Area road = new Area();
        Rectangle2D.Double tile = new Rectangle2D.Double();
        for (int row = fromRow; row <= toRow; row++) {
            for (int col = fromCol; col <= toCol; col++) {
                if (!level.contains(col, row)) continue;
                Ground ground = level.ground(col, row);
                if (ground != Ground.ROAD && ground != Ground.LAIR && ground != Ground.CASTLE) continue;
                double x = col * Field.TILE;
                double y = row * Field.TILE;
                double w = Field.TILE;
                double h = Field.TILE;
                if (ground != Ground.CASTLE) {
                    if (col == 0) { x -= Field.TILE; w += Field.TILE; }
                    if (col == level.width() - 1) w += Field.TILE;
                    if (row == 0) { y -= Field.TILE; h += Field.TILE; }
                    if (row == level.height() - 1) h += Field.TILE;
                }
                tile.setRect(x, y, w, h);
                road.add(new Area(tile));
            }
        }
        return road;
    }

    /** The number of squares across, for the tests. */
    int across() {
        return across;
    }
}
