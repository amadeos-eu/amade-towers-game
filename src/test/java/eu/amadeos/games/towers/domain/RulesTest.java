package eu.amadeos.games.towers.domain;

import eu.amadeos.gameengine.data.TextData;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RulesTest {
    // --8<-- [start:test-files]
    @Test
    void theDataFilesDescribeThreeTowersInThreeStagesAndThreeUnits() {
        Rules rules = Rules.load();
        assertEquals(List.of("archer", "cannon", "frost"), rules.buildable().stream().map(TowerType::id).toList());
        TowerType archer3 = rules.upgrade(rules.upgrade(rules.tower("archer")));
        assertEquals("archer-3", archer3.id());
        assertEquals("archer", archer3.kind());
        assertEquals(3, archer3.stage());
        assertEquals(14, archer3.shotSpeed(), "taken over from the first stage with like");
        assertNull(rules.upgrade(archer3), "upgrade none: the last stage");
        assertEquals(TowerType.Shot.FROST, rules.tower("frost-2").shot());
        assertEquals(4, rules.unit("troll").armour());
        assertEquals(1, rules.unit("orc").harm(), "not given: 1");
        assertEquals(10, rules.plan(1).waves().size());
        assertEquals(1.1 * 1.1, rules.plan(1).toughness(3), 1e-9, "10 % more health per wave");
        assertEquals(1.5, rules.plan(1).road(), "half as much health again on the road");
    }
    // --8<-- [end:test-files]

    // --8<-- [start:test-typo]
    @Test
    void aMistakeInADataFileNamesItsLine() {
        List<TextData.Line> units = TextData.load(Rules.class, Rules.FOLDER + "units.txt");
        List<TextData.Line> waves = TextData.load(Rules.class, Rules.FOLDER + "waves.txt");
        List<TextData.Line> towers = TextData.parse("towers.txt", new StringReader("""
                tower archer
                  shot arrow
                  cost 40
                  rnage 3
                  range 3
                  reload 0.75
                  speed 14
                """));
        assertEquals("towers.txt:4: \"rnage\" is not a property of a tower",
                assertThrows(TextData.DataException.class, () -> Rules.read(units, towers, waves)).getMessage());
        List<TextData.Line> badWave = TextData.parse("waves.txt", new StringReader("""
                level 1
                  gold 100
                  lives 20
                  wave orc 8 1.2 goblin 4 0.5
                """));
        assertEquals("waves.txt:4: \"goblin\" is not a unit",
                assertThrows(TextData.DataException.class, () -> Rules.read(units,
                        TextData.load(Rules.class, Rules.FOLDER + "towers.txt"), badWave)).getMessage());
    }
    // --8<-- [end:test-typo]
}
