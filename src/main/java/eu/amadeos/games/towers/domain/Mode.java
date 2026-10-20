package eu.amadeos.games.towers.domain;

/**
 * How a battle is fought. On the {@link #ROAD} the enemies keep to the road of the map and the towers
 * stand beside it, as in most tower defence games. On {@link #OPEN} ground the road is grass like any
 * other, the enemies take the shortest way over it, and the towers make the maze.
 */
public enum Mode {
    ROAD, OPEN;

    /** The name in the settings file. */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Mode ofId(String id) {
        for (Mode mode : values()) if (mode.id().equals(id)) return mode;
        return ROAD;
    }
}
