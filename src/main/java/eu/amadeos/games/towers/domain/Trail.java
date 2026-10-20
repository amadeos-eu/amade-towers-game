package eu.amadeos.games.towers.domain;

/**
 * The trail the units wear into the grass: for each tile on the way, the neighbours the way leads to and
 * comes from, one bit each for the eight directions. The painter draws a piece of path from the middle of
 * the tile towards each of them.
 */
public final class Trail {
    /** The directions in the order of their bits: up, left, down, right, then the corners. */
    public static final int[] DX = {0, -1, 0, 1, -1, -1, 1, 1};
    public static final int[] DY = {-1, 0, 1, 0, -1, 1, 1, -1};

    private Trail() {
    }

    /** The bit of the neighbour {@code dx}, {@code dy}, each -1, 0 or 1. */
    public static int bit(int dx, int dy) {
        for (int i = 0; i < 8; i++) if (DX[i] == dx && DY[i] == dy) return 1 << i;
        throw new IllegalArgumentException("not a neighbour: " + dx + ", " + dy);
    }
}
