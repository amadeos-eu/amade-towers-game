package eu.amadeos.games.towers.domain;

/**
 * An arrow or a cannon ball in flight. An arrow follows its target and hits it, or falls where the target
 * was if it is gone first. A ball flies straight to the place its target will be when it lands, in an arc
 * that only the picture shows, and bursts there.
 */
public final class Shot {
    private final TowerType.Shot kind;
    private final double damage;
    private final double splash;
    private final double speed;
    private final double fromX;
    private final double fromY;
    double x;
    double y;
    double previousX;
    double previousY;
    double toX;
    double toY;
    Creep target;
    boolean done;

    Shot(TowerType tower, double x, double y, double toX, double toY, Creep target) {
        this.kind = tower.shot();
        this.damage = tower.damage();
        this.splash = tower.splash();
        this.speed = tower.shotSpeed() * Field.TILE;
        this.fromX = x;
        this.fromY = y;
        this.x = previousX = x;
        this.y = previousY = y;
        this.toX = toX;
        this.toY = toY;
        this.target = target;
    }

    public TowerType.Shot kind() {
        return kind;
    }

    double damage() {
        return damage;
    }

    double splash() {
        return splash;
    }

    double speed() {
        return speed;
    }

    public double x(double alpha) {
        return previousX + (x - previousX) * alpha;
    }

    public double y(double alpha) {
        return previousY + (y - previousY) * alpha;
    }

    /** Where it flies to now. */
    public double toX() {
        return toX;
    }

    public double toY() {
        return toY;
    }

    /** How far along its way a ball is, 0 at the muzzle and 1 where it lands: the height of its arc. */
    public double along(double alpha) {
        double whole = Math.hypot(toX - fromX, toY - fromY);
        if (whole < 1e-9) return 1;
        return Math.clamp(Math.hypot(x(alpha) - fromX, y(alpha) - fromY) / whole, 0, 1);
    }

    /** The way it flies, for drawing an arrow. */
    public double angle() {
        return Math.atan2(toY - y, toX - x);
    }
}
