package eu.amadeos.games.towers.domain;

/**
 * One stage of a tower, as {@code towers.txt} describes it. A tower is built as the first stage of its
 * kind and upgraded along {@code upgrade} to the next; {@code cost} is what the stage costs to build, or
 * to upgrade to. Distances are in tiles, times in seconds, speeds in tiles and radians per second.
 *
 * @param kind     the first stage's id, which names the kind: {@code archer}, {@code cannon}, {@code frost}
 * @param stage    1 for the first stage
 * @param upgrade  the id of the next stage, or {@code null} at the last
 * @param splash   the radius a cannon ball bursts in; 0 for a shot that hits one unit
 * @param slow     the part of their speed units keep in the cold, 1 for no frost
 */
public record TowerType(String id, String kind, int stage, Shot shot, int cost, double range, double reload,
                        double damage, double splash, double slow, double slowSeconds, double shotSpeed,
                        double turn, String upgrade) {
    /** How a tower hurts. */
    public enum Shot {
        /** An arrow flies after its target and hits it. */
        ARROW,
        /** A cannon ball flies to where its target will be and bursts there, hurting all around. */
        BALL,
        /** A wave of cold around the tower, slowing and hurting everything in range. */
        FROST
    }

    public boolean last() {
        return upgrade == null;
    }
}
