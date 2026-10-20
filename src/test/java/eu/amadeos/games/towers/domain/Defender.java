package eu.amadeos.games.towers.domain;

import eu.amadeos.gameengine.path.FlowField;
import eu.amadeos.gameengine.tile.Cell;

import java.util.Comparator;
import java.util.List;

/**
 * A simple player for the tests: once a second it spends its gold, on the tile beside the trail where a
 * tower covers most of the way and makes it longest, or on an upgrade; it never calls a wave early. Not
 * clever, and that is the point: if it wins a level, a person can.
 */
// --8<-- [start:defender]
public final class Defender {
    static final double STEP = 1.0 / 120;

    private final Game game;

    public Defender(Game game) {
        this.game = game;
    }

    /** Plays the level to its end; at most an hour of game time. */
    Game play() {
        for (int step = 0; step < 3600 * 120 && game.phase() == Game.Phase.PLAYING; step++) {
            if (step % 120 == 0) spend();
            game.update(STEP);
        }
        return game;
    }

    /** Spends the gold, as the test player does once a second: for screenshots of a battle. */
    public void spend() {
        while (true) {
            TowerType kind = nextKind();
            if (game.gold() >= kind.cost() && buildBest(kind)) continue;
            if (game.towers().size() >= 10 && upgradeCheapest()) continue;
            return;
        }
    }

    /** Mostly archers, a cannon in every third tower from the third wave on, a frost tower in every seventh. */
    private TowerType nextKind() {
        int n = game.towers().size();
        if (n % 7 == 6) return game.rules().tower("frost");
        if (game.wavesStarted() >= 3 && n % 3 == 2) return game.rules().tower("cannon");
        return game.rules().tower("archer");
    }
    // --8<-- [end:defender]

    private boolean buildBest(TowerType kind) {
        Level level = game.level();
        Cell best = null;
        double bestScore = -1;
        for (int row = 0; row < level.height(); row++) {
            for (int col = 0; col < level.width(); col++) {
                if (!nearTrail(col, row) || game.placement(col, row) != Game.Placement.OK) continue;
                double score = score(col, row, kind.range());
                if (score > bestScore) {
                    bestScore = score;
                    best = new Cell(col, row);
                }
            }
        }
        return best != null && game.build(best.col(), best.row(), kind) == Game.Placement.OK;
    }

    private boolean nearTrail(int col, int row) {
        Level level = game.level();
        for (int r = row - 2; r <= row + 2; r++) {
            for (int c = col - 2; c <= col + 2; c++) {
                if (level.contains(c, r) && game.trail(c, r) != 0) return true;
            }
        }
        return false;
    }

    /**
     * With a tower there: how many tiles of the new way all towers reach, the new one with
     * {@code range}, counted once per tower, and how many tiles longer the way gets.
     */
    private double score(int col, int row, double range) {
        Level level = game.level();
        FlowField trial = FlowField.toward(level.width(), level.height(),
                (c, r) -> (c != col || r != row) && level.ground(c, r).walkable() && game.tower(c, r) == null,
                level.castle().toArray(Cell[]::new));
        double covered = 0;
        double longer = 0;
        for (Cell lair : level.lairs()) {
            longer += (trial.distance(lair.col(), lair.row()) - game.flow().distance(lair.col(), lair.row())) / 10.0;
            for (Cell at = lair; at != null; at = trial.next(at.col(), at.row())) {
                if (Math.hypot(at.col() - col, at.row() - row) <= range) covered++;
                for (Tower tower : game.towers()) {
                    if (Math.hypot(at.col() - tower.cell().col(), at.row() - tower.cell().row()) <= tower.type().range()) {
                        covered++;
                    }
                }
            }
        }
        return covered * 3 + longer;
    }

    private boolean upgradeCheapest() {
        List<Tower> towers = game.towers().stream().filter(t -> !t.type().last())
                .sorted(Comparator.comparingInt(t -> game.rules().upgrade(t.type()).cost())).toList();
        return !towers.isEmpty() && game.upgrade(towers.getFirst());
    }
}
