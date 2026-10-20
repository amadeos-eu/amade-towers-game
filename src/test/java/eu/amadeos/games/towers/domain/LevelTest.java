package eu.amadeos.games.towers.domain;

import eu.amadeos.gameengine.tile.Cell;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelTest {
    private final Rules rules = Rules.load();

    @Test
    void everyMapHasItsLairsCastleAndAWay() {
        for (int number = 1; number <= Level.FILES.size(); number++) {
            Level level = Level.load(number, rules);
            Game game = new Game(level, rules);
            assertTrue(level.castle().size() >= 4, "map " + number + ": a castle of a few tiles");
            for (Cell lair : level.lairs()) {
                assertTrue(game.flow().reaches(lair), "map " + number + ": a way from " + lair);
            }
        }
    }

    // --8<-- [start:test-won]
    @Test
    void aSimplePlayerWinsEveryLevel() {
        for (int number = 1; number <= Level.FILES.size(); number++) {
            Game game = new Defender(new Game(Level.load(number, rules), rules)).play();
            System.out.printf("level %d: %s with %d of %d lives, %d towers, %.0f s%n", number, game.phase(),
                    game.lives(), game.level().plan().lives(), game.towers().size(), game.time());
            assertEquals(Game.Phase.WON, game.phase(), "level " + number + ": " + game.lives() + " lives");
        }
    }
    // --8<-- [end:test-won]
}
