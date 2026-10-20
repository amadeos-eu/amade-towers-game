package eu.amadeos.games.towers.ui.game;

import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.audio.Sound.Wave;
import eu.amadeos.gameengine.audio.Tune;

/**
 * The music of Towers, written in MML: a slow march in D minor that loops while a level runs, and short
 * tunes for a level won and a level lost.
 */
final class Music {
    /** The march: drum-like bass on every beat, a tune like an old soldiers' song above it. */
    final Sound march = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.045, "t96 o4 l8 d4 f a >d4< a4 | g f e f d2 | c4 e g >c4< g4 | a g f e d2"
                    + " | d4 f a >d4< a4 | b- a g a f2 | e4 g b- a g f | e d c+ e d2"),
            Tune.voice(Wave.TRIANGLE, 0.16, "t96 o2 l4 d a d a | g d a d | c g c g | f c a d"
                    + " | d a d a | g d f c | c g a e | a a d d"));

    /** A level won. */
    final Sound won = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.07, "t140 o4 l8 d f a >d4 < a >d f4 d2"),
            Tune.voice(Wave.TRIANGLE, 0.2, "t140 o2 l8 d a >d4 < d4 a4 d2"));

    /** A level lost: the tune falls away. */
    final Sound lost = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.06, "t80 o4 l4 a f e c+ d2"),
            Tune.voice(Wave.TRIANGLE, 0.2, "t80 o2 l2 d a d1"));
}
