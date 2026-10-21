package eu.amadeos.games.towers.ui.game;

import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.path.FlowField;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.towers.domain.Creep;
import eu.amadeos.games.towers.domain.Field;
import eu.amadeos.games.towers.domain.Game;
import eu.amadeos.games.towers.domain.Ground;
import eu.amadeos.games.towers.domain.Level;
import eu.amadeos.games.towers.domain.Shot;
import eu.amadeos.games.towers.domain.Tower;
import eu.amadeos.games.towers.domain.TowerType;
import eu.amadeos.games.towers.domain.Trail;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/**
 * Draws a level as the camera sees it, from the ground up: the terrain, scorch marks, the shadows, the
 * enemies, the towers, the trees over all of them, the shots, and the effects. Only what lies in the view
 * is drawn, and nearly all of it is a copy of a sprite.
 */
final class TowersPainter {
    /** What the player is doing, for the marks on the map: all of it may be {@code null}. */
    record Marks(Cell hover, boolean hoverFree, Tower selected, Cell building, TowerType ghost, boolean flow) {
        static final Marks NONE = new Marks(null, false, null, null, null, false);
    }

    static final Color HOVER = new Color(0xFFF2B0);
    static final Color BLOCKED = new Color(0xFF6050);
    static final Color RANGE_FILL = new Color(255, 255, 255, 34);
    static final Color RANGE_LINE = new Color(255, 255, 255, 150);
    static final Color BAR_BACK = new Color(0, 0, 0, 170);
    static final Color BAR_EMPTY = new Color(0x5A1010);
    static final Color BAR_GOOD = new Color(0x7FD84A);
    static final Color BAR_HALF = new Color(0xF0C83A);
    static final Color BAR_BAD = new Color(0xF04A2A);
    /** Seconds the flash at a cannon's muzzle shows; and how far the barrel kicks back, and for how long. */
    static final double FLASH_SECONDS = 0.07;
    static final double RECOIL = 2;
    static final double RECOIL_SECONDS = 0.18;
    /** How high a cannon ball flies at the top of its arc. */
    static final double ARC = 14;
    /** How fast the crystal of a frost tower turns, in radians per second. */
    static final double CRYSTAL_TURN = 0.8;

    private final Pictures pictures = new Pictures();
    private final Rectangle2D.Double rectangle = new Rectangle2D.Double();
    private final Ellipse2D.Double ellipse = new Ellipse2D.Double();
    private final BasicStroke dashed = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1,
            new float[]{3, 3}, 0);
    private final BasicStroke outline = new BasicStroke(1.2f);

    Pictures pictures() {
        return pictures;
    }

    // --8<-- [start:paint]
    void paint(Graphics2D g, Game game, Terrain terrain, Camera camera, Effects effects, Marks marks, double alpha) {
        Shape clip = g.getClip();
        AffineTransform screen = g.getTransform();
        g.clip(new Rectangle2D.Double(camera.screenLeft(), camera.screenTop(), camera.viewWidth(), camera.viewHeight()));
        g.translate(camera.screenLeft() - camera.x(alpha), camera.screenTop() - camera.y(alpha));
        Level level = game.level();
        // The tiles in view, and one more all round for what reaches over the edge of its tile.
        int fromCol = Math.max(0, (int) Math.floor(camera.x(alpha) / Field.TILE) - 1);
        int fromRow = Math.max(0, (int) Math.floor(camera.y(alpha) / Field.TILE) - 1);
        int toCol = Math.min(level.width() - 1, (int) ((camera.x(alpha) + camera.viewWidth()) / Field.TILE) + 1);
        int toRow = Math.min(level.height() - 1, (int) ((camera.y(alpha) + camera.viewHeight()) / Field.TILE) + 1);

        terrain.render(g, camera, alpha);
        effects.renderGround(g);
        if (marks.flow()) flow(g, game.flow(), fromCol, fromRow, toCol, toRow);
        for (Tower tower : game.towers()) {
            pictures.towerShadow.draw(g, tower.cell().col() * Field.TILE, tower.cell().row() * Field.TILE);
        }
        for (Creep creep : game.creeps()) {
            Sprite shadow = pictures.creepShadow(creep.type().id());
            shadow.draw(g, creep.x(alpha) - shadow.width() / 2, creep.y(alpha) - shadow.height() / 2);
        }
        for (Creep creep : game.creeps()) creep(g, creep, alpha);
        for (Tower tower : game.towers()) tower(g, tower, effects.time(), alpha);
        trees(g, level, fromCol, fromRow, toCol, toRow);
        for (Creep creep : game.creeps()) healthBar(g, creep, alpha);
        for (Shot shot : game.shots()) shot(g, shot, alpha);
        effects.render(g, alpha);
        marks(g, game, marks);
        g.setTransform(screen);
        g.setClip(clip);
    }
    // --8<-- [end:paint]

    private void creep(Graphics2D g, Creep creep, double alpha) {
        String unit = creep.type().id();
        double x = creep.x(alpha);
        double y = creep.y(alpha);
        pictures.creep(unit).draw(g, x, y, creep.angle(alpha));
        if (creep.cold()) {
            Sprite rime = pictures.rime(unit);
            rime.draw(g, x - rime.width() / 2, y - rime.height() / 2);
        }
    }

    // --8<-- [start:tower]
    /** A tower: its base, and the turret turned towards its target, kicked back for a moment after a shot. */
    private void tower(Graphics2D g, Tower tower, double time, double alpha) {
        TowerType type = tower.type();
        double x = tower.x();
        double y = tower.y();
        pictures.base(type.kind(), type.stage()).draw(g, x - 10, y - 10);
        double angle = type.shot() == TowerType.Shot.FROST ? time * CRYSTAL_TURN : tower.angle(alpha);
        double since = tower.sinceShot();
        if (type.shot() == TowerType.Shot.BALL && since < RECOIL_SECONDS) {
            double back = RECOIL * (1 - since / RECOIL_SECONDS);
            x -= Math.cos(angle) * back;
            y -= Math.sin(angle) * back;
        }
        pictures.turret(type.kind()).draw(g, x, y, angle);
        if (type.shot() == TowerType.Shot.BALL && since < FLASH_SECONDS) pictures.flash.draw(g, x, y, angle);
    }
    // --8<-- [end:tower]

    private void trees(Graphics2D g, Level level, int fromCol, int fromRow, int toCol, int toRow) {
        for (int row = fromRow; row <= toRow; row++) {
            for (int col = fromCol; col <= toCol; col++) {
                Ground ground = level.ground(col, row);
                if (ground != Ground.TREE && ground != Ground.PINE) continue;
                Sprite tree = pictures.tree(col, row, ground == Ground.PINE);
                tree.draw(g, col * Field.TILE + 10 - tree.width() / 2, row * Field.TILE + 10 - tree.height() / 2);
            }
        }
    }

    /** Over a hurt enemy only: green, yellow at half, red at a quarter. */
    private void healthBar(Graphics2D g, Creep creep, double alpha) {
        double part = creep.health() / creep.maxHealth();
        if (part >= 1) return;
        double width = barWidth(creep.type().id());
        double x = creep.x(alpha) - width / 2;
        double y = creep.y(alpha) - width * 0.72 - 3;
        g.setColor(BAR_BACK);
        rectangle.setRect(x - 0.4, y - 0.4, width + 0.8, 2.0);
        g.fill(rectangle);
        g.setColor(BAR_EMPTY);
        rectangle.setRect(x, y, width, 1.2);
        g.fill(rectangle);
        g.setColor(part > 0.5 ? BAR_GOOD : part > 0.25 ? BAR_HALF : BAR_BAD);
        rectangle.setRect(x, y, width * Math.max(0, part), 1.2);
        g.fill(rectangle);
    }

    static double barWidth(String unit) {
        return switch (unit) {
            case "wolf" -> 12;
            case "troll" -> 15;
            default -> 10;
        } * 1.45 * 0.7;
    }

    // --8<-- [start:shot]
    /** An arrow flies low and straight; a cannon ball rises and falls, its shadow on the ground below it. */
    private void shot(Graphics2D g, Shot shot, double alpha) {
        double x = shot.x(alpha);
        double y = shot.y(alpha);
        if (shot.kind() == TowerType.Shot.ARROW) {
            double angle = shot.angle();
            pictures.arrowShadow.draw(g, x + 2, y + 3, angle);
            pictures.arrow.draw(g, x, y, angle);
            return;
        }
        double along = shot.along(alpha);
        double height = ARC * 4 * along * (1 - along);
        pictures.ballShadow.draw(g, x - 3 + height * 0.3, y - 3 + height * 0.35);
        pictures.ball.draw(g, x - 2, y - 2 - height);
    }
    // --8<-- [end:shot]

    // --8<-- [start:flow]
    /** F3: an arrow on every tile that leads to the castle, white near it and orange far away. */
    private void flow(Graphics2D g, FlowField field, int fromCol, int fromRow, int toCol, int toRow) {
        int far = 1;
        for (int row = 0; row < field.height(); row++) {
            for (int col = 0; col < field.width(); col++) {
                if (field.reaches(col, row)) far = Math.max(far, field.distance(col, row));
            }
        }
        for (int row = fromRow; row <= toRow; row++) {
            for (int col = fromCol; col <= toCol; col++) {
                Cell next = field.next(col, row);
                if (next == null) continue;
                int d = Integer.numberOfTrailingZeros(Trail.bit(next.col() - col, next.row() - row));
                int shade = (int) Math.round((double) field.distance(col, row) / far * (Pictures.SHADES - 1));
                pictures.flow[d][shade].draw(g, col * Field.TILE, row * Field.TILE);
            }
        }
    }
    // --8<-- [end:flow]

    /** The tile under the mouse, the range of the tower chosen, and the tower about to be built. */
    private void marks(Graphics2D g, Game game, Marks marks) {
        Cell at = marks.building() != null ? marks.building() : marks.hover();
        if (marks.selected() != null) {
            Tower tower = marks.selected();
            range(g, tower.x(), tower.y(), tower.type().range());
            at = tower.cell();
        }
        if (marks.building() != null && marks.ghost() != null) {
            double x = (marks.building().col() + 0.5) * Field.TILE;
            double y = (marks.building().row() + 0.5) * Field.TILE;
            range(g, x, y, marks.ghost().range());
            Composite before = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
            pictures.base(marks.ghost().kind(), 1).draw(g, x - 10, y - 10);
            pictures.turret(marks.ghost().kind()).draw(g, x, y, Math.PI * 0.85);
            g.setComposite(before);
        }
        if (at == null) return;
        boolean free = marks.selected() != null || marks.building() != null || marks.hoverFree();
        g.setStroke(outline);
        g.setColor(free ? HOVER : BLOCKED);
        rectangle.setRect(at.col() * Field.TILE + 0.5, at.row() * Field.TILE + 0.5, Field.TILE - 1, Field.TILE - 1);
        g.draw(rectangle);
    }

    private void range(Graphics2D g, double x, double y, double tiles) {
        double r = tiles * Field.TILE;
        ellipse.setFrame(x - r, y - r, 2 * r, 2 * r);
        g.setColor(RANGE_FILL);
        g.fill(ellipse);
        g.setColor(RANGE_LINE);
        g.setStroke(dashed);
        g.draw(ellipse);
    }
}
