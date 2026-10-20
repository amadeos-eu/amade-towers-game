package eu.amadeos.games.towers.domain;

import eu.amadeos.gameengine.data.TextData.DataException;
import eu.amadeos.gameengine.tiled.TiledMap;

/**
 * What a tile of a map is to the rules, by the class its tile has in Tiled's tileset: {@code tree},
 * {@code pine}, {@code rock} or {@code water}; a tile without a class is grass. The lair and the castle are
 * objects of the map and are entered by {@link Level}.
 */
// --8<-- [start:ground]
public enum Ground {
    GRASS, TREE, PINE, ROCK, WATER, LAIR, CASTLE;

    /** Towers stand on grass only. */
    public boolean buildable() {
        return this == GRASS;
    }

    /** Units walk on grass, out of their lair and into the castle; trees, rocks and water are in the way. */
    public boolean walkable() {
        return this == GRASS || this == LAIR || this == CASTLE;
    }

    /** The ground a gid of the map stands for; an unknown class is an error naming the map. */
    static Ground of(TiledMap map, int gid) {
        return switch (map.type(gid)) {
            case "" -> GRASS;
            case "tree" -> TREE;
            case "pine" -> PINE;
            case "rock" -> ROCK;
            case "water" -> WATER;
            default -> throw new DataException(map.source() + ": the tile class \"" + map.type(gid)
                    + "\" means nothing to the game; use tree, pine, rock or water");
        };
    }
}
// --8<-- [end:ground]
