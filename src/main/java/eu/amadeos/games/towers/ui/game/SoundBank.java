package eu.amadeos.games.towers.ui.game;

import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.audio.Sound.Wave;

/** The sound effects of Towers, made once at start from tones: no recordings. */
final class SoundBank {
    /** A bow string: a short knock of noise and a falling twang. */
    final Sound bow = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.03, 0.05),
            Sound.tone(Wave.TRIANGLE, 420, 260, 0.08, 0.12));
    /** A cannon: a deep thump under a burst of noise. */
    final Sound cannon = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.22, 0.13),
            Sound.tone(Wave.TRIANGLE, 120, 40, 0.25, 0.35));
    /** Frost: a high glassy shimmer sliding down. */
    final Sound frost = Sound.mix(Sound.tone(Wave.TRIANGLE, 2400, 1600, 0.25, 0.06),
            Sound.tone(Wave.TRIANGLE, 3100, 2200, 0.2, 0.04));
    final Sound hit = Sound.tone(Wave.NOISE, 0, 0, 0.03, 0.04);
    /** The ball bursts. */
    final Sound burst = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.35, 0.15),
            Sound.tone(Wave.TRIANGLE, 90, 30, 0.35, 0.3));
    /** An enemy falls: a short grunt down. */
    final Sound fall = Sound.tone(Wave.SQUARE, 220, 90, 0.12, 0.05);
    /** One gets through to the castle: a low horn. */
    final Sound leak = Sound.mix(Sound.tone(Wave.SQUARE, 110, 104, 0.4, 0.06),
            Sound.tone(Wave.TRIANGLE, 220, 208, 0.4, 0.12));
    /** Stone on stone: a tower stands. */
    final Sound build = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.08, 0.12),
            Sound.tone(Wave.TRIANGLE, 180, 120, 0.1, 0.3))
            .then(Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.06, 0.1), Sound.tone(Wave.TRIANGLE, 160, 110, 0.08, 0.25)));
    final Sound upgrade = Sound.tone(Wave.SQUARE, 523, 523, 0.06, 0.05)
            .then(Sound.tone(Wave.SQUARE, 784, 784, 0.06, 0.05))
            .then(Sound.tone(Wave.SQUARE, 1046, 1046, 0.12, 0.05));
    /** Coins: two quick high notes. */
    final Sound coins = Sound.tone(Wave.SQUARE, 1319, 1319, 0.04, 0.05)
            .then(Sound.tone(Wave.SQUARE, 1760, 1760, 0.1, 0.05));
    /** A wave comes: two notes of a war horn. */
    final Sound horn = Sound.mix(Sound.tone(Wave.SQUARE, 147, 147, 0.25, 0.05)
                    .then(Sound.tone(Wave.SQUARE, 196, 196, 0.5, 0.05)),
            Sound.tone(Wave.TRIANGLE, 294, 294, 0.25, 0.12).then(Sound.tone(Wave.TRIANGLE, 392, 392, 0.5, 0.12)));
    /** Not possible: a low buzz. */
    final Sound no = Sound.tone(Wave.SQUARE, 150, 140, 0.15, 0.06);
    /** A menu choice moves, or a menu opens. */
    final Sound tick = Sound.tone(Wave.SQUARE, 880, 880, 0.03, 0.04);
}
