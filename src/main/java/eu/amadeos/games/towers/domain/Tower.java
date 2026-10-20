package eu.amadeos.games.towers.domain;

import eu.amadeos.gameengine.tile.Cell;

/** A tower on its tile: what it is now, what it cost all together, and where its turret points. */
public final class Tower {
    private final Cell cell;
    TowerType type;
    int spent;
    double cooldown;
    double angle;
    double previousAngle;
    /** Seconds since the last shot, for the flash at the muzzle; large at the start. */
    double sinceShot = 99;
    Creep target;

    Tower(TowerType type, Cell cell, double angle) {
        this.type = type;
        this.cell = cell;
        this.spent = type.cost();
        this.angle = previousAngle = angle;
    }

    public TowerType type() {
        return type;
    }

    public Cell cell() {
        return cell;
    }

    /** The middle of its tile, in world units. */
    public double x() {
        return (cell.col() + 0.5) * Field.TILE;
    }

    public double y() {
        return (cell.row() + 0.5) * Field.TILE;
    }

    public int spent() {
        return spent;
    }

    /** What selling it brings back. */
    public int refund() {
        return (int) Math.floor(spent * Field.REFUND);
    }

    public double angle() {
        return angle;
    }

    /** The turret's angle between the last two steps, the short way round. */
    public double angle(double alpha) {
        double turn = Math.IEEEremainder(angle - previousAngle, 2 * Math.PI);
        return previousAngle + turn * alpha;
    }

    public double sinceShot() {
        return sinceShot;
    }

    public Creep target() {
        return target;
    }
}
