package eu.amadeos.games.towers.ui.game;

import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.towers.domain.Field;
import eu.amadeos.games.towers.domain.Game;
import eu.amadeos.games.towers.domain.Ground;
import eu.amadeos.games.towers.domain.Level;
import eu.amadeos.games.towers.domain.Trail;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The ground of a level: grass, water, rocks, the shadows of the trees, the lair, the castle and the trail.
 * All of it lies still, so it is drawn once into sprites, in squares of {@value #CHUNK} × {@value #CHUNK}
 * tiles, and in each frame only copied. The squares are {@link Sprite#opaque() opaque}: they cover
 * everything below them, and the copy needs no blending.
 *
 * <p>Only the trail changes, when a tower makes the enemies go another way. Then the squares it crosses,
 * before or after, are drawn again, but no more than {@value #REDRAWS} in a frame: drawing one costs a few
 * milliseconds, and a new way through the whole map would cost a frame or two all at once. Until a square's
 * turn comes, its old picture shows.
 */
// --8<-- [start:terrain]
final class Terrain {
    static final int CHUNK = 8;
    static final int REDRAWS = 2;
    private static final int SIZE = CHUNK * Field.TILE;

    private final Level level;
    private final BufferedImage grass;
    private final Chunk[] chunks;
    private final int across;
    private Game game;
    private int version = -1;

    /** One square: the picture shown, and a newer one with the trail as it is now. */
    private static final class Chunk {
        final int col;
        final int row;
        int[] trail = new int[0];
        Sprite shown;
        Sprite next;

        Chunk(int col, int row) {
            this.col = col;
            this.row = row;
        }
    }

    Terrain(Level level) {
        this.level = level;
        this.grass = Art.grass(level.width() * Field.TILE, level.height() * Field.TILE, level.number());
        this.across = (level.width() + CHUNK - 1) / CHUNK;
        int down = (level.height() + CHUNK - 1) / CHUNK;
        chunks = new Chunk[across * down];
        for (int i = 0; i < chunks.length; i++) chunks[i] = new Chunk(i % across * CHUNK, i / across * CHUNK);
    }

    /**
     * After every step: a square whose trail is not the trail of the game any more gets a new picture. The
     * same terrain serves every battle on its level, so a new game is a change, too.
     */
    void update(Game game) {
        if (game == this.game && game.version() == version) return;
        this.game = game;
        version = game.version();
        int[] all = new int[level.width() * level.height()];
        for (int row = 0; row < level.height(); row++) {
            for (int col = 0; col < level.width(); col++) all[row * level.width() + col] = game.trail(col, row);
        }
        for (Chunk chunk : chunks) {
            int[] trail = around(chunk, all);
            if (chunk.shown != null && Arrays.equals(trail, chunk.trail)) continue;
            chunk.trail = trail;
            chunk.next = picture(chunk, all);
        }
    }
    // --8<-- [end:terrain]

    /** The trail of a square and of a tile around it, which its pictures reach into. */
    private int[] around(Chunk chunk, int[] all) {
        int[] trail = new int[(CHUNK + 2) * (CHUNK + 2)];
        int i = 0;
        for (int row = chunk.row - 1; row <= chunk.row + CHUNK; row++) {
            for (int col = chunk.col - 1; col <= chunk.col + CHUNK; col++) {
                trail[i++] = level.contains(col, row) ? all[row * level.width() + col] : 0;
            }
        }
        return trail;
    }

    // --8<-- [start:render]
    /**
     * The squares the camera sees. A square is a unit larger than its tiles, to the right and below, so
     * that rounding to whole pixels never leaves a gap between two; the next square covers the overlap.
     */
    void render(Graphics2D g, Camera camera, double alpha, boolean all) {
        int redraws = all ? Integer.MAX_VALUE : REDRAWS;
        for (Chunk chunk : chunks) {
            double x = chunk.col * Field.TILE;
            double y = chunk.row * Field.TILE;
            if (!camera.sees(x, y, SIZE + 1, SIZE + 1, alpha)) continue;
            if (chunk.next != null && (chunk.shown == null || redraws > 0)) {
                if (chunk.shown != null) redraws--;
                chunk.shown = chunk.next;
                chunk.next = null;
            }
            if (chunk.shown != null) chunk.shown.draw(g, x, y);
        }
    }
    // --8<-- [end:render]

    /** Whether all squares show the trail as it is: for the tests. */
    boolean settled() {
        return Arrays.stream(chunks).allMatch(chunk -> chunk.next == null);
    }

    /** Every square at once with the newest trail: for screenshots, which draw a single frame. */
    void settle() {
        for (Chunk chunk : chunks) {
            if (chunk.next == null) continue;
            chunk.shown = chunk.next;
            chunk.next = null;
        }
    }

    /** The picture of a square with the trail {@code all}; drawn when it is first shown. */
    private Sprite picture(Chunk chunk, int[] all) {
        double x0 = chunk.col * Field.TILE;
        double y0 = chunk.row * Field.TILE;
        double w = Math.min(SIZE + 1, level.width() * Field.TILE - x0);
        double h = Math.min(SIZE + 1, level.height() * Field.TILE - y0);
        return Sprite.drawn(w, h, (g, width, height) -> {
            g.translate(-x0, -y0);
            g.clip(new Rectangle2D.Double(x0, y0, width, height));
            draw(g, chunk, all, x0, y0, width, height);
        }).opaque();
    }

    // --8<-- [start:draw]
    private void draw(Graphics2D g, Chunk chunk, int[] all, double x0, double y0, double w, double h) {
        int p = Art.GRASS_PIXELS;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(grass, (int) x0, (int) y0, (int) (x0 + w), (int) (y0 + h),
                (int) x0 * p, (int) y0 * p, (int) (x0 + w) * p, (int) (y0 + h) * p, null);
        int fromCol = chunk.col - 2;
        int toCol = chunk.col + CHUNK + 1;
        int fromRow = chunk.row - 2;
        int toRow = chunk.row + CHUNK + 1;
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
        Path2D way = new Path2D.Double();
        for (int row = fromRow; row <= toRow; row++) {
            for (int col = fromCol; col <= toCol; col++) {
                if (!level.contains(col, row)) continue;
                int bits = all[row * level.width() + col];
                for (int d = 0; d < 8; d++) {
                    int dx = Trail.DX[d];
                    int dy = Trail.DY[d];
                    // Each connection once, from the tile on its left or above.
                    if ((bits & 1 << d) == 0 || dx < 0 || dx == 0 && dy < 0) continue;
                    way.moveTo((col + 0.5) * Field.TILE, (row + 0.5) * Field.TILE);
                    way.lineTo((col + 0.5 + dx) * Field.TILE, (row + 0.5 + dy) * Field.TILE);
                }
            }
        }
        for (Cell lair : level.lairs()) {               // out of the cave, past the edge of the map
            double x = (lair.col() + 0.5) * Field.TILE;
            double y = (lair.row() + 0.5) * Field.TILE;
            int dx = lair.col() == 0 ? -1 : lair.col() == level.width() - 1 ? 1 : 0;
            int dy = dx != 0 ? 0 : lair.row() == 0 ? -1 : 1;
            way.moveTo(x, y);
            way.lineTo(x + dx * Field.TILE, y + dy * Field.TILE);
        }
        Art.trail(g, way);
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
                    default -> {
                        if (all[row * level.width() + col] != 0) Art.pebbles(g, x + 10, y + 10, random);
                    }
                }
            }
        }
        for (Cell lair : level.lairs()) Art.lair(g, lair.col() * Field.TILE, (lair.row() + 0.5) * Field.TILE);
        Level.Box castle = level.castleBox();
        Art.castle(g, castle.x() + 3, castle.y() + 3, castle.width() - 6, castle.height() - 6);
    }
    // --8<-- [end:draw]

    /** The number of squares across, for the tests. */
    int across() {
        return across;
    }
}
