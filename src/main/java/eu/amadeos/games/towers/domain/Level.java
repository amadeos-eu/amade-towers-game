package eu.amadeos.games.towers.domain;

import eu.amadeos.gameengine.data.TextData.DataException;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.gameengine.tile.TileMap;
import eu.amadeos.gameengine.tiled.TiledMap;
import eu.amadeos.gameengine.tiled.TiledObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A map as drawn in Tiled, {@code mapN.tmx} in the folder {@code maps}, with the tileset {@code tiles.tsx},
 * and what {@code waves.txt} plans for it. The tile layer {@code ground} holds the trees, pines, rocks and
 * water by their tiles' classes ({@link Ground}); the object layer {@code things} holds one or more
 * {@code lair} (where the enemies come from, a tile each) and one {@code castle} (where they go). There
 * is no road: the way is wherever the towers leave room.
 */
public final class Level {
    /** The maps of the game, in order. */
    public static final List<String> FILES = List.of("map1.tmx", "map2.tmx", "map3.tmx");
    /** Where the maps and their tileset lie among the classes. */
    public static final String FOLDER = "/eu/amadeos/games/towers/maps/";

    /** Where the castle stands, in world units. */
    public record Box(double x, double y, double width, double height) {
    }

    private final int number;
    private final TiledMap map;
    private final TileMap<Ground> ground;
    private final List<Cell> lairs;
    private final List<Cell> castle;
    private final Box castleBox;
    private final Rules.Plan plan;

    private Level(int number, TiledMap map, TileMap<Ground> ground, List<Cell> lairs, List<Cell> castle, Box castleBox,
                  Rules.Plan plan) {
        this.number = number;
        this.map = map;
        this.ground = ground;
        this.lairs = List.copyOf(lairs);
        this.castle = List.copyOf(castle);
        this.castleBox = castleBox;
        this.plan = plan;
    }

    /** Level {@code number}, counted from 1. */
    public static Level load(int number, Rules rules) {
        return read(number, TiledMap.load(Level.class, FOLDER + FILES.get(number - 1)), rules.plan(number));
    }

    // --8<-- [start:read]
    /** The rules' view of a map: its ground by the tiles' classes, the lairs and the castle by their objects. */
    public static Level read(int number, TiledMap map, Rules.Plan plan) {
        if (map.tileWidth() != Field.TILE || map.tileHeight() != Field.TILE) {
            throw new DataException(map.source() + ": the tiles must be " + Field.TILE + " × " + Field.TILE);
        }
        TileMap<Ground> ground = map.tiles("ground", gid -> Ground.of(map, gid));
        List<Cell> lairs = new ArrayList<>();
        List<Cell> castle = new ArrayList<>();
        Box box = null;
        for (TiledObject thing : map.objects("things")) {
            switch (thing.type()) {
                case "lair" -> {
                    Cell cell = cell(map, thing, thing.centerX(), thing.centerY());
                    ground.set(cell.col(), cell.row(), Ground.LAIR);
                    lairs.add(cell);
                }
                case "castle" -> {
                    if (box != null) throw error(map, thing, "a second castle; a map has one");
                    box = new Box(thing.x(), thing.y(), thing.width(), thing.height());
                    // Every tile the castle covers by more than half is the castle's.
                    for (int row = 0; row < ground.height(); row++) {
                        for (int col = 0; col < ground.width(); col++) {
                            double x = (col + 0.5) * Field.TILE;
                            double y = (row + 0.5) * Field.TILE;
                            if (x > thing.x() && x < thing.right() && y > thing.y() && y < thing.bottom()) {
                                ground.set(col, row, Ground.CASTLE);
                                castle.add(new Cell(col, row));
                            }
                        }
                    }
                    if (castle.isEmpty()) throw error(map, thing, "the castle covers no tile");
                }
                default -> throw error(map, thing, "the class \"" + thing.type()
                        + "\" means nothing to the game; use lair or castle");
            }
        }
        if (lairs.isEmpty()) throw new DataException(map.source() + ": no object of the class lair");
        if (box == null) throw new DataException(map.source() + ": no object of the class castle");
        return new Level(number, map, ground, lairs, castle, box, plan);
    }
    // --8<-- [end:read]

    private static Cell cell(TiledMap map, TiledObject thing, double x, double y) {
        int col = (int) Math.floor(x / Field.TILE);
        int row = (int) Math.floor(y / Field.TILE);
        if (col < 0 || row < 0 || col >= map.width() || row >= map.height()) throw error(map, thing, "outside the map");
        return new Cell(col, row);
    }

    private static DataException error(TiledMap map, TiledObject thing, String message) {
        return new DataException(map.source() + ": object " + thing.id() + " at (" + (int) thing.x() + ", "
                + (int) thing.y() + "): " + message);
    }

    public int number() {
        return number;
    }

    /** The map as Tiled saved it. */
    public TiledMap map() {
        return map;
    }

    public Ground ground(int col, int row) {
        return ground.get(col, row);
    }

    public boolean contains(int col, int row) {
        return ground.contains(col, row);
    }

    /** Columns and rows. */
    public int width() {
        return ground.width();
    }

    public int height() {
        return ground.height();
    }

    public List<Cell> lairs() {
        return lairs;
    }

    /** The tiles of the castle: the goals of every way. */
    public List<Cell> castle() {
        return castle;
    }

    public Box castleBox() {
        return castleBox;
    }

    public Rules.Plan plan() {
        return plan;
    }
}
