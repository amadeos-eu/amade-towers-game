package eu.amadeos.games.towers.domain;

import eu.amadeos.gameengine.path.FlowField;
import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.tile.Cell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The rules of a level: the gold, the lives, the waves and their spawning, the units following the flow
 * field, the towers aiming and shooting, and building, upgrading and selling. No drawing and no input
 * here: the scenes call {@link #build}, {@link #upgrade}, {@link #sell} and {@link #callWave}, and
 * {@link #update} advances by one step and reports what happened as {@link Event}s.
 */
public final class Game {
    public enum Phase { PLAYING, WON, LOST }

    /** Whether a tower may stand on a tile. */
    public enum Placement {
        OK,
        /** Not grass, or a tower is there. */
        NOT_HERE,
        /** A unit is on the tile. */
        OCCUPIED,
        /** It would close the last way from a lair, or from a unit, to the castle. */
        BLOCKS,
        TOO_DEAR
    }

    /** Seconds before the first wave comes on its own: time to build. */
    public static final double FIRST_SECONDS = 30;

    private final Level level;
    private final Rules rules;
    private final Rules.Plan plan;
    private final SeededRandom random;
    private final Tower[] towerAt;
    private final List<Tower> towers = new ArrayList<>();
    private final List<Creep> creeps = new ArrayList<>();
    private final List<Shot> shots = new ArrayList<>();
    private final List<Spawner> spawners = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    /** What the player's commands did since the last step; the next step reports it with its own events. */
    private final List<Event> commanded = new ArrayList<>();
    private FlowField flow;
    /** Per tile, which neighbours the way of the units leads to and comes from: the trail on the ground. */
    private int[] trail;
    /** Counts the changes of the map, so a placement asked twice need not search twice. */
    private int version;
    private int askedVersion = -1;
    private double askedTime = -1;
    private Cell askedCell;
    private Placement askedPlacement;
    private int gold;
    private int lives;
    private int wavesStarted;
    private boolean countingDown = true;
    private double countdown = FIRST_SECONDS;
    private int nextId = 1;
    private int nextLair;
    private double time;
    private Phase phase = Phase.PLAYING;
    private int stars;

    /** A wave on its way out of the lairs. */
    private static final class Spawner {
        final Wave wave;
        final int number;
        final double toughness;
        int group;
        int spawned;
        double wait;

        Spawner(Wave wave, int number, double toughness) {
            this.wave = wave;
            this.number = number;
            this.toughness = toughness;
        }

        boolean done() {
            return group >= wave.groups().size();
        }
    }

    public Game(Level level, Rules rules) {
        this(level, rules, 1);
    }

    public Game(Level level, Rules rules, long seed) {
        this.level = level;
        this.rules = rules;
        this.plan = level.plan();
        this.random = new SeededRandom(seed);
        this.towerAt = new Tower[level.width() * level.height()];
        this.gold = plan.gold();
        this.lives = plan.lives();
        rebuildWay();
    }

    // ---- the way --------------------------------------------------------------------------------

    // --8<-- [start:way]
    /** Units walk where the ground lets them and no tower stands. */
    private boolean open(int col, int row) {
        return level.ground(col, row).walkable() && towerAt[row * level.width() + col] == null;
    }

    /** One search from the castle for all units, after every change of the map. */
    private void rebuildWay() {
        flow = FlowField.toward(level.width(), level.height(), this::open, level.castle().toArray(Cell[]::new));
        trail = trail(flow);
        version++;
    }
    // --8<-- [end:way]

    /** The ways from the lairs to the castle, as connections between neighbouring tiles. */
    private int[] trail(FlowField field) {
        int[] connections = new int[level.width() * level.height()];
        for (Cell lair : level.lairs()) {
            Cell at = lair;
            for (int guard = 0; guard < connections.length && at != null; guard++) {
                Cell next = field.next(at.col(), at.row());
                if (next == null) break;
                connections[at.row() * level.width() + at.col()] |= Trail.bit(next.col() - at.col(), next.row() - at.row());
                connections[next.row() * level.width() + next.col()] |= Trail.bit(at.col() - next.col(), at.row() - next.row());
                at = next;
            }
        }
        return connections;
    }

    // --8<-- [start:placement]
    /**
     * Whether a tower may stand on a tile, gold aside. A tower must never close the way: every lair, and
     * every unit already on the map, must still reach the castle. The flow field answers that with one
     * search, with the tile closed as if the tower stood there.
     */
    public Placement placement(int col, int row) {
        if (!level.contains(col, row) || !level.ground(col, row).buildable() || tower(col, row) != null) {
            return Placement.NOT_HERE;
        }
        Cell cell = new Cell(col, row);
        if (askedVersion == version && askedTime == time && cell.equals(askedCell)) return askedPlacement;
        Placement answer = Placement.OK;
        for (Creep creep : creeps) {
            if (creep.col() == col && creep.row() == row) answer = Placement.OCCUPIED;
        }
        if (answer == Placement.OK) {
            FlowField trial = FlowField.toward(level.width(), level.height(),
                    (c, r) -> (c != col || r != row) && open(c, r), level.castle().toArray(Cell[]::new));
            for (Cell lair : level.lairs()) if (!trial.reaches(lair)) answer = Placement.BLOCKS;
            for (Creep creep : creeps) if (!trial.reaches(creep.col(), creep.row())) answer = Placement.BLOCKS;
        }
        askedVersion = version;
        askedTime = time;
        askedCell = cell;
        askedPlacement = answer;
        return answer;
    }
    // --8<-- [end:placement]

    // ---- the player's commands -----------------------------------------------------------------

    /** Builds the first stage of a tower; the reason if it cannot stand there or is too dear. */
    public Placement build(int col, int row, TowerType type) {
        if (phase != Phase.PLAYING) return Placement.NOT_HERE;
        Placement placement = placement(col, row);
        if (placement != Placement.OK) return placement;
        if (gold < type.cost()) return Placement.TOO_DEAR;
        gold -= type.cost();
        // A new turret looks along the way, towards the lairs.
        Tower tower = new Tower(type, new Cell(col, row), Math.PI);
        towerAt[row * level.width() + col] = tower;
        towers.add(tower);
        rebuildWay();
        commanded.add(new Event.Built(tower));
        return Placement.OK;
    }

    /** True if the tower was upgraded to its next stage; false at the last stage or without the gold. */
    public boolean upgrade(Tower tower) {
        TowerType next = rules.upgrade(tower.type);
        if (phase != Phase.PLAYING || next == null || gold < next.cost()) return false;
        gold -= next.cost();
        tower.spent += next.cost();
        tower.type = next;
        commanded.add(new Event.Upgraded(tower));
        return true;
    }

    public void sell(Tower tower) {
        if (phase != Phase.PLAYING || !towers.remove(tower)) return;
        towerAt[tower.cell().row() * level.width() + tower.cell().col()] = null;
        int refund = tower.refund();
        gold += refund;
        rebuildWay();
        commanded.add(new Event.Sold(tower, refund));
    }

    // --8<-- [start:call]
    /**
     * The next wave now. Called while the pause before it runs, it brings a gold piece for each second
     * left; called while the wave before is still coming, nothing but more enemies at once.
     */
    public void callWave() {
        if (phase != Phase.PLAYING || wavesStarted >= plan.waves().size()) return;
        int bonus = countingDown ? (int) Math.ceil(countdown) : 0;
        gold += bonus;
        startWave(bonus, commanded);
    }
    // --8<-- [end:call]

    private void startWave(int bonus, List<Event> report) {
        int number = ++wavesStarted;
        spawners.add(new Spawner(plan.waves().get(number - 1), number, plan.toughness(number)));
        countingDown = false;
        report.add(new Event.WaveStarted(number, bonus));
    }

    // ---- a step -----------------------------------------------------------------------------------

    // --8<-- [start:update]
    public void update(double seconds) {
        events.clear();
        events.addAll(commanded);
        commanded.clear();
        if (phase != Phase.PLAYING) return;
        time += seconds;
        if (countingDown) {
            countdown -= seconds;
            if (countdown <= 0) startWave(0, events);
        }
        spawn(seconds);
        for (Creep creep : creeps) walk(creep, seconds);
        for (Tower tower : towers) act(tower, seconds);
        for (Shot shot : shots) fly(shot, seconds);
        creeps.removeIf(Creep::gone);
        shots.removeIf(shot -> shot.done);
        end();
    }
    // --8<-- [end:update]

    private void spawn(double seconds) {
        for (Spawner spawner : spawners) {
            spawner.wait -= seconds;
            while (!spawner.done() && spawner.wait <= 0) {
                Wave.Group group = spawner.wave.groups().get(spawner.group);
                enter(group.unit(), spawner.toughness);
                spawner.wait += group.gap();
                if (++spawner.spawned >= group.count()) {
                    spawner.group++;
                    spawner.spawned = 0;
                }
            }
        }
        boolean latestDone = !spawners.isEmpty() && spawners.getLast().done();
        spawners.removeIf(Spawner::done);
        if (latestDone && spawners.isEmpty() && wavesStarted < plan.waves().size() && !countingDown) {
            countingDown = true;
            countdown = Field.BREAK_SECONDS;
        }
    }

    /** A unit steps out of the next lair, in turn. */
    private void enter(UnitType type, double toughness) {
        Cell lair = level.lairs().get(nextLair++ % level.lairs().size());
        double x = (lair.col() + 0.5) * Field.TILE;
        double y = (lair.row() + 0.5) * Field.TILE;
        double asideX = (random.nextDouble() * 2 - 1) * Field.ASIDE;
        double asideY = (random.nextDouble() * 2 - 1) * Field.ASIDE;
        Cell next = flow.next(lair.col(), lair.row());
        double angle = next == null ? 0 : Math.atan2(next.row() - lair.row(), next.col() - lair.col());
        creeps.add(new Creep(nextId++, type, type.health() * toughness, x, y, asideX, asideY, angle));
    }

    // --8<-- [start:walk]
    /** A unit walks towards the next tile of the flow field, beside its middle; in the castle it is gone. */
    private void walk(Creep creep, double seconds) {
        creep.previousX = creep.x;
        creep.previousY = creep.y;
        creep.previousAngle = creep.angle;
        if (creep.cold > 0) {
            creep.cold -= seconds;
            if (creep.cold <= 0) creep.slow = 1;
        }
        int col = creep.col();
        int row = creep.row();
        if (flow.isGoal(col, row)) {
            leak(creep);
            return;
        }
        Cell next = flow.next(col, row);
        if (next == null) return;       // walled in: cannot happen, placement() sees to that
        double toX = (next.col() + 0.5 + creep.asideX()) * Field.TILE;
        double toY = (next.row() + 0.5 + creep.asideY()) * Field.TILE;
        double dx = toX - creep.x;
        double dy = toY - creep.y;
        double way = Math.hypot(dx, dy);
        double step = Math.min(way, creep.type().speed() * Field.TILE * creep.slow * seconds);
        if (way > 1e-9) {
            creep.x += dx / way * step;
            creep.y += dy / way * step;
            creep.angle = turn(creep.angle, Math.atan2(dy, dx), Field.UNIT_TURN * seconds);
        }
        creep.vx = (creep.x - creep.previousX) / seconds;
        creep.vy = (creep.y - creep.previousY) / seconds;
        creep.walked += step;
    }
    // --8<-- [end:walk]

    private void leak(Creep creep) {
        creep.gone = true;
        lives = Math.max(0, lives - creep.type().harm());
        events.add(new Event.Leaked(creep, creep.type().harm()));
    }

    // --8<-- [start:act]
    /** A tower turns to the unit nearest to the castle within its range and fires when it points at it. */
    private void act(Tower tower, double seconds) {
        TowerType type = tower.type;
        tower.cooldown -= seconds;
        tower.sinceShot += seconds;
        tower.previousAngle = tower.angle;
        double range = type.range() * Field.TILE;
        if (type.shot() == TowerType.Shot.FROST) {
            if (tower.cooldown > 0 || first(tower, range) == null) return;
            for (Creep creep : creeps) {
                if (!creep.gone && distance(tower, creep) <= range) {
                    creep.slow = Math.min(creep.slow, type.slow());
                    creep.cold = Math.max(creep.cold, type.slowSeconds());
                    hurt(creep, type.damage());
                }
            }
            tower.cooldown = type.reload();
            tower.sinceShot = 0;
            events.add(new Event.Frosted(tower));
            return;
        }
        tower.target = first(tower, range);
        if (tower.target == null) return;
        double aim = Math.atan2(tower.target.y - tower.y(), tower.target.x - tower.x());
        tower.angle = turn(tower.angle, aim, type.turn() * seconds);
        if (tower.cooldown <= 0 && Math.abs(Math.IEEEremainder(aim - tower.angle, 2 * Math.PI)) < Field.AIMED) {
            fire(tower, tower.target);
            tower.cooldown = type.reload();
            tower.sinceShot = 0;
        }
    }

    /** The unit in range with the shortest way left to the castle: the one to stop first. */
    private Creep first(Tower tower, double range) {
        Creep best = null;
        int bestWay = Integer.MAX_VALUE;
        for (Creep creep : creeps) {
            if (creep.gone || distance(tower, creep) > range) continue;
            int way = flow.distance(creep.col(), creep.row());
            if (way < bestWay) {
                best = creep;
                bestWay = way;
            }
        }
        return best;
    }
    // --8<-- [end:act]

    // --8<-- [start:fire]
    private void fire(Tower tower, Creep target) {
        TowerType type = tower.type;
        // From the muzzle, a little in front of the middle of the turret.
        double x = tower.x() + Math.cos(tower.angle) * 8;
        double y = tower.y() + Math.sin(tower.angle) * 8;
        double toX = target.x;
        double toY = target.y;
        if (type.shot() == TowerType.Shot.BALL) {
            // Where the target will be when the ball lands: it walks on while the ball flies. Two guesses
            // are enough; the second already uses the flight time to the first.
            for (int i = 0; i < 2; i++) {
                double flight = Math.hypot(toX - x, toY - y) / (type.shotSpeed() * Field.TILE);
                toX = target.x + target.vx * flight;
                toY = target.y + target.vy * flight;
            }
        }
        shots.add(new Shot(type, x, y, toX, toY, target));
        events.add(new Event.Fired(tower));
    }
    // --8<-- [end:fire]

    private void fly(Shot shot, double seconds) {
        shot.previousX = shot.x;
        shot.previousY = shot.y;
        if (shot.kind() == TowerType.Shot.ARROW && shot.target != null) {
            if (shot.target.gone) {
                shot.target = null;      // falls where the target was
            } else {
                shot.toX = shot.target.x;
                shot.toY = shot.target.y;
            }
        }
        double dx = shot.toX - shot.x;
        double dy = shot.toY - shot.y;
        double way = Math.hypot(dx, dy);
        double step = shot.speed() * seconds;
        if (way > step) {
            shot.x += dx / way * step;
            shot.y += dy / way * step;
            return;
        }
        shot.x = shot.toX;
        shot.y = shot.toY;
        shot.done = true;
        if (shot.kind() == TowerType.Shot.ARROW) {
            if (shot.target != null) {
                hurt(shot.target, shot.damage());
                events.add(new Event.ArrowHit(shot.x, shot.y));
            }
            return;
        }
        // --8<-- [start:burst]
        // A ball bursts: everything whose body the burst reaches is hit, the target or not.
        double radius = shot.splash() * Field.TILE;
        for (Creep creep : creeps) {
            if (!creep.gone && Math.hypot(creep.x - shot.x, creep.y - shot.y) <= radius + creep.type().size() * Field.TILE) {
                hurt(creep, shot.damage());
            }
        }
        events.add(new Event.Burst(shot.x, shot.y, radius));
        // --8<-- [end:burst]
    }

    /** Damage less armour, at least 1; at no health left the unit is beaten and brings its bounty. */
    private void hurt(Creep creep, double damage) {
        if (creep.gone) return;
        creep.health -= Math.max(1, damage - creep.type().armour());
        if (creep.health <= 0) {
            creep.gone = true;
            gold += creep.type().bounty();
            events.add(new Event.Killed(creep, creep.type().bounty()));
        }
    }

    private void end() {
        if (lives <= 0) {
            phase = Phase.LOST;
            events.add(new Event.Lost());
        } else if (wavesStarted == plan.waves().size() && spawners.isEmpty() && creeps.isEmpty()) {
            phase = Phase.WON;
            double left = (double) lives / plan.lives();
            stars = left >= Field.THREE_STARS ? 3 : left >= Field.TWO_STARS ? 2 : 1;
            events.add(new Event.Won(stars));
        }
    }

    private static double distance(Tower tower, Creep creep) {
        return Math.hypot(creep.x - tower.x(), creep.y - tower.y());
    }

    /** From {@code angle} towards {@code wanted} by at most {@code most}, the short way round. */
    static double turn(double angle, double wanted, double most) {
        double difference = Math.IEEEremainder(wanted - angle, 2 * Math.PI);
        if (Math.abs(difference) <= most) return wanted;
        return angle + Math.signum(difference) * most;
    }

    // ---- what the scenes and the painter ask ------------------------------------------------------

    public Level level() {
        return level;
    }

    public Rules rules() {
        return rules;
    }

    public Phase phase() {
        return phase;
    }

    public int stars() {
        return stars;
    }

    public int gold() {
        return gold;
    }

    public int lives() {
        return lives;
    }

    public double time() {
        return time;
    }

    /** Waves that have begun, from 0 to all. */
    public int wavesStarted() {
        return wavesStarted;
    }

    public int waves() {
        return plan.waves().size();
    }

    /** Seconds until the next wave comes on its own; empty while none is waiting for its time. */
    public java.util.OptionalDouble countdown() {
        return countingDown ? java.util.OptionalDouble.of(Math.max(0, countdown)) : java.util.OptionalDouble.empty();
    }

    public Tower tower(int col, int row) {
        return level.contains(col, row) ? towerAt[row * level.width() + col] : null;
    }

    public List<Tower> towers() {
        return Collections.unmodifiableList(towers);
    }

    public List<Creep> creeps() {
        return Collections.unmodifiableList(creeps);
    }

    public List<Shot> shots() {
        return Collections.unmodifiableList(shots);
    }

    public FlowField flow() {
        return flow;
    }

    /** The connections of the trail on a tile, as {@link Trail} bits. */
    public int trail(int col, int row) {
        return trail[row * level.width() + col];
    }

    /** Changes with every tower built or sold: the painter redraws the trail when it does. */
    public int version() {
        return version;
    }

    /** What happened in the last step. */
    public List<Event> events() {
        return Collections.unmodifiableList(events);
    }
}
