package eu.amadeos.games.towers.domain;

import eu.amadeos.gameengine.data.TextData;
import eu.amadeos.gameengine.path.FlowField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTest {
    static final double STEP = 1.0 / 120;

    /** One orc, after the 30 seconds before the first wave, or at once when called. */
    static final String ONE_ORC = """
            level 1
              gold 200
              lives 5
              wave orc 1 1
            """;

    static Game game(String waves, String... rows) {
        Rules rules = Maps.rules(waves);
        return new Game(Maps.of(rules, rows), rules);
    }

    static void run(Game game, double seconds) {
        for (int i = 0; i < Math.round(seconds / STEP); i++) game.update(STEP);
    }

    // --8<-- [start:test-walk]
    @Test
    void unitsWalkTheShortestWayToTheCastleAndCostLivesThere() {
        Game game = game(ONE_ORC,
                "..........",
                "L.......CC",
                "..........");
        game.callWave();
        game.update(STEP);
        assertEquals(1, game.creeps().size());
        Creep orc = game.creeps().getFirst();
        assertEquals(0, orc.col(), "out of the lair, its first step taken");
        // From the middle of the lair to the castle's first tile: 7.5 tiles at one tile a second.
        run(game, 7.3);
        assertEquals(1, game.creeps().size(), "still on its way");
        run(game, 0.4);
        assertTrue(game.creeps().isEmpty(), "in the castle");
        assertEquals(4, game.lives(), "an orc costs a life");
    }
    // --8<-- [end:test-walk]

    // --8<-- [start:test-block]
    @Test
    void towersMakeTheMazeButNeverCloseTheWay() {
        Game game = game(ONE_ORC,
                ".....T....",
                "L.......CC",
                "..........");
        TowerType archer = game.rules().tower("archer");
        assertEquals(80, game.flow().distance(0, 1), "eight tiles straight to the castle");
        assertEquals(Game.Placement.OK, game.build(3, 1, archer), "in the way, but there is a way round");
        assertTrue(game.flow().distance(0, 1) > 80, "round it: " + game.flow().distance(0, 1));
        assertEquals(Game.Placement.OK, game.build(3, 0, archer));
        assertEquals(Game.Placement.BLOCKS, game.build(3, 2, archer), "the last gap");
        assertEquals(Game.Placement.NOT_HERE, game.build(5, 0, archer), "a tree stands there");
        assertEquals(Game.Placement.NOT_HERE, game.build(3, 1, archer), "a tower stands there");
        assertEquals(Game.Placement.NOT_HERE, game.build(8, 1, archer), "the castle");
        assertTrue(game.flow().reaches(0, 1));
    }

    @Test
    void noTowerIsBuiltOnAUnitOrWhereItWouldWallOneIn() {
        Game game = game(ONE_ORC,
                "L.........CC",
                "TTTT.TTTTTTT");
        game.callWave();
        run(game, 2.0);         // the orc is on (2, 0)
        Creep orc = game.creeps().getFirst();
        assertEquals(2, orc.col());
        TowerType archer = game.rules().tower("archer");
        assertEquals(Game.Placement.OCCUPIED, game.build(2, 0, archer));
        assertEquals(Game.Placement.BLOCKS, game.build(4, 0, archer), "the orc could not go on");
        assertEquals(Game.Placement.BLOCKS, game.build(1, 0, archer), "the lair would be walled in");
    }
    // --8<-- [end:test-block]

    // --8<-- [start:test-archer]
    @Test
    void anArcherTurnsShootsAndWinsTheBounty() {
        Game game = game(ONE_ORC,
                "..........",
                "L.......CC",
                "..........");
        TowerType archer = game.rules().tower("archer");
        game.build(4, 0, archer);
        game.callWave();
        game.update(STEP);
        int gold = game.gold();
        int fired = 0;
        for (int i = 0; i < 8 * 120 && !game.creeps().isEmpty(); i++) {
            game.update(STEP);
            fired += (int) game.events().stream().filter(e -> e instanceof Event.Fired).count();
        }
        assertTrue(game.creeps().isEmpty());
        assertEquals(5, game.lives(), "the orc never reached the castle");
        // 38 hit points, 9 per arrow: five arrows.
        assertEquals(5, fired);
        assertEquals(gold + 5, game.gold(), "the bounty of an orc");
    }
    // --8<-- [end:test-archer]

    // --8<-- [start:test-cannon]
    @Test
    void aCannonBallHitsEverythingWhereItBursts() {
        Game game = game("""
                level 1
                  gold 200
                  lives 20
                  wave orc 3 0.05
                """,
                "..........",
                "L.......CC",
                "..........");
        game.build(5, 0, game.rules().tower("cannon"));
        game.callWave();
        Event.Burst burst = null;
        for (int i = 0; i < 6 * 120 && burst == null; i++) {
            game.update(STEP);
            for (Event e : game.events()) if (e instanceof Event.Burst b) burst = b;
        }
        assertTrue(burst != null);
        long hurt = game.creeps().stream().filter(c -> c.health() < c.maxHealth()).count();
        assertEquals(3, hurt, "three orcs close together, one ball");
    }
    // --8<-- [end:test-cannon]

    // --8<-- [start:test-frost]
    @Test
    void frostSlowsWhatIsInRange() {
        Game game = game(ONE_ORC,
                "..........",
                "L.......CC",
                "..........");
        game.build(3, 0, game.rules().tower("frost"));
        game.callWave();
        run(game, 2.0);
        Creep orc = game.creeps().getFirst();
        assertTrue(orc.cold());
        assertEquals(0.5, orc.slow(), "it keeps half its speed");
        double x = orc.x();
        double y = orc.y();
        game.update(STEP);
        assertEquals(0.5 * 20 / 120, Math.hypot(orc.x() - x, orc.y() - y), 1e-9, "half a tile a second");
    }
    // --8<-- [end:test-frost]

    // --8<-- [start:test-gold]
    @Test
    void upgradesCostAndSellingBringsBackPartOfAllThatWasPaid() {
        Game game = game(ONE_ORC,
                "..........",
                "L.......CC",
                "..........");
        game.build(4, 0, game.rules().tower("cannon"));
        Tower cannon = game.tower(4, 0);
        assertEquals(140, game.gold());
        assertTrue(game.upgrade(cannon));
        assertEquals("cannon-2", cannon.type().id());
        assertEquals(2, cannon.type().stage());
        assertEquals(85, game.gold(), "55 for the second stage");
        assertFalse(game.upgrade(cannon), "the third costs 95");
        game.sell(cannon);
        assertEquals(85 + 80, game.gold(), "70 % of 60 + 55");
        assertEquals(null, game.tower(4, 0));
    }

    @Test
    void callingTheNextWaveEarlyBringsGold() {
        Game game = game("""
                level 1
                  gold 0
                  lives 20
                  wave orc 1 1
                  wave orc 1 1
                """,
                "L.......CC");
        run(game, 10.5);
        game.callWave();
        assertEquals(20, game.gold(), "19.5 of the first 30 seconds were left: 20 gold");
        run(game, 1);
        assertEquals(Field.BREAK_SECONDS - 1, game.countdown().orElseThrow(), 0.02, "the pause before the second");
    }
    // --8<-- [end:test-gold]

    // --8<-- [start:test-end]
    @Test
    void theLevelIsWonWhenAllWavesAreBeatenAndLostWithTheLastLife() {
        Game won = game(ONE_ORC,
                "L.......CC",
                "..........");
        won.build(3, 1, won.rules().tower("archer"));
        won.build(5, 1, won.rules().tower("archer"));
        won.callWave();
        run(won, 10);
        assertEquals(Game.Phase.WON, won.phase());
        assertEquals(3, won.stars(), "no life lost");

        Game lost = game("""
                level 1
                  gold 0
                  lives 2
                  wave troll 1 1
                """, "L...CC");
        lost.callWave();
        run(lost, 10);
        assertEquals(Game.Phase.LOST, lost.phase(), "a troll costs three lives");
        assertEquals(0, lost.lives());
    }
    // --8<-- [end:test-end]

    @Test
    void theFlowFieldGoesRoundRocksAndWater() {
        Game game = game(ONE_ORC,
                "...~......",
                "L..R....CC",
                "..........");
        FlowField flow = game.flow();
        assertFalse(flow.reaches(3, 1), "nobody walks on a rock");
        assertTrue(flow.distance(0, 1) > 80, "around it: more than eight tiles straight");
    }

    // --8<-- [start:test-road]
    @Test
    void onTheRoadUnitsKeepToItAndTowersStandBesideIt() {
        String[] rows = {
                "..........",
                "L###....CC",
                "...######.",
        };
        Rules rules = Maps.rules(ONE_ORC);
        Game road = new Game(Maps.of(rules, rows), rules, Mode.ROAD);
        TowerType archer = road.rules().tower("archer");
        assertEquals(Game.Placement.NOT_HERE, road.build(2, 1, archer), "not on the road");
        assertFalse(road.flow().reaches(5, 1), "the grass is not walked on");
        assertEquals(30 + 10 + 50 + 10, road.flow().distance(0, 1), "along the road, round its corners");
        assertEquals(Game.Placement.OK, road.build(5, 1, archer), "beside it");
        assertEquals(Game.Placement.OK, road.build(5, 0, archer), "a tower beside the road never blocks it");

        Game open = new Game(Maps.of(rules, rows), rules, Mode.OPEN);
        assertEquals(80, open.flow().distance(0, 1), "on open ground the road is grass: straight on");
        assertEquals(Game.Placement.OK, open.build(2, 1, open.rules().tower("archer")));
    }

    @Test
    void onTheRoadTheUnitsAreTougher() {
        Rules rules = Maps.rules("""
                level 1
                  gold 0
                  lives 5
                  road 2
                  wave orc 1 1
                """);
        String[] rows = {"L#######CC"};
        for (Mode mode : Mode.values()) {
            Game game = new Game(Maps.of(rules, rows), rules, mode);
            game.callWave();
            game.update(STEP);
            assertEquals(mode == Mode.ROAD ? 76 : 38, game.creeps().getFirst().maxHealth(), 1e-9, mode.toString());
        }
    }

    @Test
    void aMapWithoutARoadIsPlayedOnOpenGround() {
        Rules rules = Maps.rules(ONE_ORC);
        Game game = new Game(Maps.of(rules, "L.......CC"), rules, Mode.ROAD);
        assertEquals(Mode.OPEN, game.mode());
    }

    @Test
    void aRoadThatDoesNotReachTheCastleIsAnError() {
        TextData.DataException error = assertThrows(TextData.DataException.class,
                () -> Maps.of(Maps.rules(ONE_ORC), "L###......CC"));
        assertEquals("test.tmx: the road does not lead from the lair at (0, 0) to the castle", error.getMessage());
    }
    // --8<-- [end:test-road]
}
