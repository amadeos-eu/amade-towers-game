package eu.amadeos.games.towers.ui.game;

import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.towers.domain.Game;
import eu.amadeos.games.towers.domain.Level;
import eu.amadeos.games.towers.domain.TowerType;

/**
 * The player of the title screen: it spends its gold on towers beside the trail, from the castle back
 * towards the lair, an archer, an archer, a cannon, now and then a frost tower. Enough to show a battle.
 */
final class Autobuild {
    private Autobuild() {
    }

    static void spend(Game game) {
        Level level = game.level();
        int n = game.towers().size();
        TowerType type = game.rules().tower(n % 6 == 5 ? "frost" : n % 3 == 2 ? "cannon" : "archer");
        if (game.gold() < type.cost()) return;
        Cell best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int row = 0; row < level.height(); row++) {
            for (int col = 0; col < level.width(); col++) {
                if (!besideTrail(game, col, row) || game.placement(col, row) != Game.Placement.OK) continue;
                int distance = nearestTrail(game, col, row);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new Cell(col, row);
                }
            }
        }
        if (best != null) game.build(best.col(), best.row(), type);
    }

    private static boolean besideTrail(Game game, int col, int row) {
        return game.trail(col, row) == 0 && nearestTrail(game, col, row) < Integer.MAX_VALUE;
    }

    /** The way left to the castle from the trail tile next to this one, the shortest. */
    private static int nearestTrail(Game game, int col, int row) {
        int best = Integer.MAX_VALUE;
        for (int r = row - 1; r <= row + 1; r++) {
            for (int c = col - 1; c <= col + 1; c++) {
                if (game.level().contains(c, r) && game.trail(c, r) != 0) {
                    best = Math.min(best, game.flow().distance(c, r) + 10 * Math.abs(r - row + c - col));
                }
            }
        }
        return best;
    }
}
