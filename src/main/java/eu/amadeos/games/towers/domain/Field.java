package eu.amadeos.games.towers.domain;

/**
 * The numbers of the game that are not in the data files: sizes in logical units (a tile is 20), times in
 * seconds. What the towers and the units can do is in {@code towers.txt} and {@code units.txt}; how
 * many come, and when, in {@code waves.txt}.
 */
public final class Field {
    /** The logical size of the screen. */
    public static final double WIDTH = 640;
    public static final double HEIGHT = 400;
    /** The bar at the top with gold, lives and the wave; the map is seen below it. */
    public static final double BAR = 22;
    public static final int TILE = 20;
    /** The part of the screen that shows the map: 32 × 18.9 tiles. */
    public static final double VIEW_WIDTH = WIDTH;
    public static final double VIEW_HEIGHT = HEIGHT - BAR;

    /** A tower sold brings back this part of all that was paid for it. */
    public static final double REFUND = 0.7;
    /** The pause between the end of a wave's arrival and the next wave. */
    public static final double BREAK_SECONDS = 15;
    /** Units walk this far beside the middle of the way, at most, in tiles: a crowd, not a queue. */
    public static final double ASIDE = 0.24;
    /** How fast a unit turns to where it walks, in radians per second: only for the eye. */
    public static final double UNIT_TURN = 9;
    /** A turret fires when it points within this angle of its target. */
    public static final double AIMED = 0.2;
    /** Stars for a level won with this part of the lives left, or more: three, two, else one. */
    public static final double THREE_STARS = 0.9;
    public static final double TWO_STARS = 0.5;

    private Field() {
    }
}
