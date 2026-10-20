package eu.amadeos.games.towers.domain;

/**
 * An enemy on its way to the castle. It knows nothing of the way: each step the {@link Game} tells it the
 * next tile from the flow field, and it walks there, a little to the side of the middle, as a crowd walks.
 * Positions are in world units, the middle of the unit.
 */
public final class Creep {
    private final int id;
    private final UnitType type;
    private final double maxHealth;
    /** Where the unit walks beside the middle of each tile, in tiles; its own all the way. */
    private final double asideX;
    private final double asideY;
    double x;
    double y;
    double previousX;
    double previousY;
    /** The last step's speed, in units per second: where a cannon ball must aim. */
    double vx;
    double vy;
    double health;
    double angle;
    double previousAngle;
    double walked;
    /** The part of its speed the unit keeps in the cold, and for how long still. */
    double slow = 1;
    double cold;
    boolean gone;

    Creep(int id, UnitType type, double health, double x, double y, double asideX, double asideY, double angle) {
        this.id = id;
        this.type = type;
        this.maxHealth = health;
        this.health = health;
        this.x = previousX = x;
        this.y = previousY = y;
        this.asideX = asideX;
        this.asideY = asideY;
        this.angle = previousAngle = angle;
    }

    public int id() {
        return id;
    }

    public UnitType type() {
        return type;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    /** Between the last two steps, for drawing. */
    public double x(double alpha) {
        return previousX + (x - previousX) * alpha;
    }

    public double y(double alpha) {
        return previousY + (y - previousY) * alpha;
    }

    public double asideX() {
        return asideX;
    }

    public double asideY() {
        return asideY;
    }

    public double health() {
        return health;
    }

    public double maxHealth() {
        return maxHealth;
    }

    /** Where it faces, the way it walks. */
    public double angle() {
        return angle;
    }

    /** Where it faces between the last two steps, turned the short way. */
    public double angle(double alpha) {
        return previousAngle + Math.IEEEremainder(angle - previousAngle, 2 * Math.PI) * alpha;
    }

    /** How far it has walked, in units: its feet move with it. */
    public double walked() {
        return walked;
    }

    public boolean cold() {
        return cold > 0;
    }

    public double slow() {
        return slow;
    }

    /** Beaten, or in the castle. */
    public boolean gone() {
        return gone;
    }

    public int col() {
        return (int) Math.floor(x / Field.TILE);
    }

    public int row() {
        return (int) Math.floor(y / Field.TILE);
    }
}
