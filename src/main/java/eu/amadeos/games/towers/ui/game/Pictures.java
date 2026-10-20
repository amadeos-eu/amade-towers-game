package eu.amadeos.games.towers.ui.game;

import eu.amadeos.gameengine.sprite.Rotations;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.games.towers.domain.Trail;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The pictures of {@link Art} as sprites, each drawn once at its size on the screen and then only copied.
 * What turns, the turrets, the enemies, the arrows, is drawn in {@value #TURNS} directions
 * ({@link Rotations}): a sprite turned by the graphics would be drawn anew in every frame.
 */
// --8<-- [start:pictures]
final class Pictures {
    static final int TURNS = 32;
    static final List<String> KINDS = List.of("archer", "cannon", "frost");
    static final List<String> UNITS = List.of("orc", "wolf", "troll");
    static final int STAGES = 3;
    static final int TREES = 12;
    static final int PINES = 8;
    /** The colours of the flow field's arrows, from near the castle to far away. */
    static final int SHADES = 8;

    private final Map<String, Sprite> bases = new HashMap<>();
    private final Map<String, Sprite> icons = new HashMap<>();
    private final Map<String, Rotations> turrets = new HashMap<>();
    private final Map<String, Rotations> creeps = new HashMap<>();
    private final Map<String, Sprite> creepShadows = new HashMap<>();
    private final Map<String, Sprite> rimes = new HashMap<>();
    final Sprite towerShadow = Sprite.drawn(30, 30, (g, w, h) -> Art.towerShadow(g, 10, 10));
    final Rotations flash = Rotations.of(TURNS, 40, (g, w, h) -> Art.flash(g, w / 2 + 9.6, h / 2));
    final Rotations arrow = Rotations.of(TURNS, 14, (g, w, h) -> Art.arrow(g, w / 2, h / 2));
    final Rotations arrowShadow = Rotations.of(TURNS, 14, (g, w, h) -> Art.arrowShadow(g, w / 2, h / 2));
    final Sprite ball = Sprite.drawn(4, 4, (g, w, h) -> Art.ball(g, 2, 2, 3.4));
    final Sprite ballShadow = Sprite.drawn(6, 6, (g, w, h) -> Art.spot(g, 3, 3, 5, new Color(0, 0, 0, 90)));
    final Sprite coin = Sprite.drawn(7, 7, (g, w, h) -> Art.coin(g, 0, 0, 7));
    final Sprite[] trees = new Sprite[TREES];
    final Sprite[] pines = new Sprite[PINES];
    /** By direction, in the order of {@link Trail}, and by shade. */
    final Sprite[][] flow = new Sprite[8][SHADES];
    // --8<-- [end:pictures]

    Pictures() {
        for (String kind : KINDS) {
            for (int stage = 1; stage <= STAGES; stage++) {
                int s = stage;
                bases.put(kind + s, Sprite.drawn(20, 20, (g, w, h) -> Art.towerBase(g, 10, 10, kind, s)));
                icons.put(kind + s, Sprite.drawn(22, 22, (g, w, h) -> Art.tower(g, 11, 11, kind, s)));
            }
        }
        turrets.put("archer", Rotations.of(TURNS, 24, (g, w, h) -> Art.archer(g, w / 2, h / 2)));
        turrets.put("cannon", Rotations.of(TURNS, 24, (g, w, h) -> Art.cannon(g, w / 2, h / 2)));
        turrets.put("frost", Rotations.of(TURNS, 24, (g, w, h) -> Art.frost(g, w / 2, h / 2)));
        for (String unit : UNITS) {
            double size = Art.creepSize(unit);
            creeps.put(unit, Rotations.of(TURNS, size, (g, w, h) -> Art.creep(g, unit, w / 2, h / 2)));
            creepShadows.put(unit, Sprite.drawn(size, size,
                    (g, w, h) -> Art.creepShadow(g, unit, w / 2 + Art.SHADOW_X * 0.8, h / 2 + Art.SHADOW_Y * 0.8)));
            double r = rimeRadius(unit);
            rimes.put(unit, Sprite.drawn(2 * r, 2 * r, (g, w, h) -> Art.rime(g, r, r, r)));
        }
        // Each drawing makes its own random numbers: it runs again whenever the window changes its size.
        for (int i = 0; i < TREES; i++) {
            long seed = 100 + i;
            trees[i] = Sprite.drawn(30, 30, (g, w, h) -> {
                Random random = new Random(seed);
                Art.tree(g, 15 + (random.nextDouble() - 0.5) * 3, 15 + (random.nextDouble() - 0.5) * 3, random);
            });
        }
        for (int i = 0; i < PINES; i++) {
            long seed = 200 + i;
            pines[i] = Sprite.drawn(22, 22, (g, w, h) -> {
                Random random = new Random(seed);
                Art.pine(g, 11 + (random.nextDouble() - 0.5) * 3, 11 + (random.nextDouble() - 0.5) * 3, random);
            });
        }
        for (int d = 0; d < 8; d++) {
            for (int shade = 0; shade < SHADES; shade++) {
                int dx = Trail.DX[d];
                int dy = Trail.DY[d];
                float t = (float) shade / (SHADES - 1);
                Color colour = new Color(1f, 1f - 0.45f * t, 1f - 0.85f * t, 0.8f);
                flow[d][shade] = Sprite.drawn(20, 20, (g, w, h) -> Art.flowArrow(g, 10, 10, dx, dy, colour));
            }
        }
    }

    /** The stone tower of a stage of {@code kind}, a tile large. */
    Sprite base(String kind, int stage) {
        return bases.get(kind + Math.clamp(stage, 1, STAGES));
    }

    /** A whole tower, base and turret, 22 units large: for the menus. */
    Sprite icon(String kind, int stage) {
        return icons.get(kind + Math.clamp(stage, 1, STAGES));
    }

    Rotations turret(String kind) {
        return turrets.get(kind);
    }

    Rotations creep(String unit) {
        return creeps.getOrDefault(unit, creeps.get("orc"));
    }

    Sprite creepShadow(String unit) {
        return creepShadows.getOrDefault(unit, creepShadows.get("orc"));
    }

    Sprite rime(String unit) {
        return rimes.getOrDefault(unit, rimes.get("orc"));
    }

    static double rimeRadius(String unit) {
        return switch (unit) {
            case "wolf" -> 7;
            case "troll" -> 9;
            default -> 6;
        };
    }

    /** A tree for the tile {@code col}, {@code row}: always the same one there. */
    Sprite tree(int col, int row, boolean pine) {
        int pick = (int) (Art.hash(col, row, 3) * 1_000_000);
        return pine ? pines[pick % PINES] : trees[pick % TREES];
    }
}
