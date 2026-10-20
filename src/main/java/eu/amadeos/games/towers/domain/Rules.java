package eu.amadeos.games.towers.domain;

import eu.amadeos.gameengine.data.Catalog;
import eu.amadeos.gameengine.data.TextData;
import eu.amadeos.gameengine.data.TextData.DataException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything the data files say: the units of {@code units.txt}, the towers of {@code towers.txt} and,
 * for each level, its gold, lives and waves from {@code waves.txt}. Read once; a mistake in a file stops
 * the game at the start with the file and the line, before anyone plays with a wrong number.
 */
public final class Rules {
    /** Where the data files lie among the classes. */
    public static final String FOLDER = "/eu/amadeos/games/towers/data/";

    /** What a level gives the player and sends against them. */
    public record Plan(int level, int gold, int lives, double tougher, List<Wave> waves) {
        public Plan {
            waves = List.copyOf(waves);
        }

        /** How much more health the units of wave {@code number} (from 1) have than those of the first. */
        public double toughness(int number) {
            return Math.pow(1 + tougher, number - 1);
        }
    }

    private final Map<String, UnitType> units;
    private final Map<String, TowerType> towers;
    private final Map<Integer, Plan> plans;

    private Rules(Map<String, UnitType> units, Map<String, TowerType> towers, Map<Integer, Plan> plans) {
        this.units = units;
        this.towers = towers;
        this.plans = plans;
    }

    /** The rules of the game, from its data files. */
    public static Rules load() {
        return read(TextData.load(Rules.class, FOLDER + "units.txt"), TextData.load(Rules.class, FOLDER + "towers.txt"),
                TextData.load(Rules.class, FOLDER + "waves.txt"));
    }

    public static Rules read(List<TextData.Line> unitLines, List<TextData.Line> towerLines, List<TextData.Line> waveLines) {
        Map<String, UnitType> units = units(unitLines);
        Map<String, TowerType> towers = towers(towerLines);
        return new Rules(units, towers, plans(waveLines, units));
    }

    // --8<-- [start:units]
    private static Map<String, UnitType> units(List<TextData.Line> lines) {
        Map<String, UnitType> units = new LinkedHashMap<>();
        for (Catalog.Entry unit : Catalog.read(lines, "unit")) {
            UnitType type = new UnitType(unit.name(), positive(unit, "health"), positive(unit, "speed"),
                    unit.number("armour", 0), unit.integer("bounty"), unit.integer("harm", 1),
                    unit.number("size", 0.3));
            unit.finish();
            units.put(type.id(), type);
        }
        return units;
    }
    // --8<-- [end:units]

    // --8<-- [start:towers]
    private static Map<String, TowerType> towers(List<TextData.Line> lines) {
        List<Catalog.Entry> entries = Catalog.read(lines, "tower");
        Map<String, Catalog.Entry> byName = new LinkedHashMap<>();
        Map<String, String> before = new LinkedHashMap<>();
        for (Catalog.Entry tower : entries) {
            byName.put(tower.name(), tower);
            String next = upgrade(tower);
            if (next != null && before.put(next, tower.name()) != null) {
                throw tower.error("a second tower upgrades to " + next);
            }
        }
        Map<String, TowerType> towers = new LinkedHashMap<>();
        for (Catalog.Entry tower : entries) {
            // The first stage of the kind: follow the upgrades backwards.
            String kind = tower.name();
            int stage = 1;
            while (before.containsKey(kind)) {
                kind = before.get(kind);
                stage++;
                if (stage > entries.size()) throw tower.error("the upgrades go round in a circle");
            }
            String upgrade = upgrade(tower);
            if (upgrade != null && !byName.containsKey(upgrade)) throw tower.error("there is no tower " + upgrade);
            TowerType.Shot shot = shot(tower);
            TowerType type = new TowerType(tower.name(), kind, stage, shot, tower.integer("cost"),
                    positive(tower, "range"), positive(tower, "reload"), tower.number("damage", 0),
                    tower.number("splash", 0), tower.number("slow", 1), tower.number("cold", 0),
                    shot == TowerType.Shot.FROST ? 0 : positive(tower, "speed"), tower.number("turn", 6), upgrade);
            tower.finish();
            towers.put(type.id(), type);
        }
        return towers;
    }
    // --8<-- [end:towers]

    /** The next stage; "upgrade none" ends a chain that a stage took over with like. */
    private static String upgrade(Catalog.Entry tower) {
        String next = tower.text("upgrade", "none");
        return next.equals("none") ? null : next;
    }

    private static TowerType.Shot shot(Catalog.Entry tower) {
        String word = tower.text("shot");
        try {
            return TowerType.Shot.valueOf(word.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw tower.line("shot").error("\"" + word + "\" is not a shot; use arrow, ball or frost");
        }
    }

    // --8<-- [start:waves]
    private static Map<Integer, Plan> plans(List<TextData.Line> lines, Map<String, UnitType> units) {
        Map<Integer, Plan> plans = new LinkedHashMap<>();
        for (Catalog.Entry level : Catalog.read(lines, "level")) {
            int number;
            try {
                number = Integer.parseInt(level.name());
            } catch (NumberFormatException e) {
                throw level.error("a level is a number");
            }
            List<Wave> waves = new ArrayList<>();
            for (TextData.Line line : level.lines("wave")) {
                // wave orc 8 1.2 wolf 4 0.6: groups of a unit, how many, and the seconds between them
                if (line.size() < 4 || (line.size() - 1) % 3 != 0) {
                    throw line.error("a wave is groups of three words: a unit, how many, the seconds between");
                }
                List<Wave.Group> groups = new ArrayList<>();
                for (int i = 1; i < line.size(); i += 3) {
                    UnitType unit = units.get(line.word(i));
                    if (unit == null) throw line.error('"' + line.word(i) + "\" is not a unit");
                    groups.add(new Wave.Group(unit, line.integer(i + 1), line.number(i + 2)));
                }
                waves.add(new Wave(groups));
            }
            if (waves.isEmpty()) throw level.error("no waves");
            plans.put(number, new Plan(number, level.integer("gold"), level.integer("lives"),
                    level.number("tougher", 0), waves));
            level.finish();
        }
        return plans;
    }
    // --8<-- [end:waves]

    private static double positive(Catalog.Entry entry, String key) {
        double value = entry.number(key);
        if (value <= 0) throw entry.line(key).error(key + " must be more than 0");
        return value;
    }

    public UnitType unit(String id) {
        UnitType unit = units.get(id);
        if (unit == null) throw new IllegalArgumentException("no unit " + id);
        return unit;
    }

    public TowerType tower(String id) {
        TowerType tower = towers.get(id);
        if (tower == null) throw new IllegalArgumentException("no tower " + id);
        return tower;
    }

    /** The towers that can be built, the first stage of each kind, in the order of the file. */
    public List<TowerType> buildable() {
        return towers.values().stream().filter(t -> t.stage() == 1).toList();
    }

    /** The stage after {@code tower}; {@code null} at the last. */
    public TowerType upgrade(TowerType tower) {
        return tower.last() ? null : tower(tower.upgrade());
    }

    public Plan plan(int level) {
        Plan plan = plans.get(level);
        if (plan == null) throw new DataException("waves.txt: no level " + level);
        return plan;
    }
}
