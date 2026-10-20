package eu.amadeos.games.towers.domain;

/** What happened in a step, for the sounds and the pictures; the rules have already dealt with it. */
// --8<-- [start:event]
public sealed interface Event {
    record Built(Tower tower) implements Event {
    }

    record Upgraded(Tower tower) implements Event {
    }

    record Sold(Tower tower, int refund) implements Event {
    }

    /** An arrow or a ball leaves a turret. */
    record Fired(Tower tower) implements Event {
    }

    /** A wave of cold around a frost tower. */
    record Frosted(Tower tower) implements Event {
    }

    record ArrowHit(double x, double y) implements Event {
    }

    /** A cannon ball bursts; {@code radius} in units. */
    record Burst(double x, double y, double radius) implements Event {
    }

    record Killed(Creep creep, int bounty) implements Event {
    }

    /** A unit reached the castle. */
    record Leaked(Creep creep, int harm) implements Event {
    }

    /** {@code number} counts from 1; {@code bonus} is the gold for calling it before its time. */
    record WaveStarted(int number, int bonus) implements Event {
    }

    record Won(int stars) implements Event {
    }

    record Lost() implements Event {
    }
}
// --8<-- [end:event]
