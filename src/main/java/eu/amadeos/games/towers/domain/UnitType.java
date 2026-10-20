package eu.amadeos.games.towers.domain;

/**
 * A kind of enemy, as {@code units.txt} describes it: hit points, speed in tiles per second, the armour
 * taken off every hit, the gold for beating one, the lives it costs at the castle, and its size, a radius
 * in tiles.
 */
public record UnitType(String id, double health, double speed, double armour, int bounty, int harm, double size) {
}
