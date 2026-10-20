package eu.amadeos.games.towers.ui.game;

import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.fx.Particles;
import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.games.towers.domain.Creep;
import eu.amadeos.games.towers.domain.Event;
import eu.amadeos.games.towers.domain.Field;
import eu.amadeos.games.towers.domain.Tower;
import eu.amadeos.games.towers.domain.TowerType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the events of a step look and sound like: smoke at a cannon's muzzle, bursts with fire and dirt,
 * scorch marks that fade away, rings of frost, the gold an enemy brings, and the sounds. The rules know
 * nothing of this. Everything lives in world units and is drawn by the painter, with the camera's shift.
 */
final class Effects {
    /** Smoke, dark smoke, dirt, fire, ice, blood, gold, stone. */
    static final Color[] PALETTE = {new Color(0xD8D6D0), new Color(0x6A6660), new Color(0x5A4228),
            new Color(0xFFB040), new Color(0xD8F4FF), new Color(0x8A1A14), new Color(0xFFD84A), new Color(0xA8A090)};
    static final int SMOKE = 0;
    static final int DARK_SMOKE = 1;
    static final int DIRT = 2;
    static final int FIRE = 3;
    static final int ICE = 4;
    static final int BLOOD = 5;
    static final int GOLD = 6;
    static final int STONE = 7;
    static final double BURST_SECONDS = 0.4;
    static final double FROST_SECONDS = 0.5;
    static final double SCORCH_SECONDS = 20;
    static final double TEXT_SECONDS = 1.0;
    /** The same sound is not started again within this time: twenty archers make one volley, not noise. */
    static final double SOUND_GAP = 0.06;
    static final int MOST_SCORCHES = 60;

    private record Burst(double x, double y, double radius, double born) {
    }

    private record Ring(double x, double y, double radius, double born) {
    }

    private record Scorch(double x, double y, double size, double born) {
    }

    private record Text(double x, double y, String text, Color colour, double born) {
    }

    private final SoundBank sounds = new SoundBank();
    private final Music music = new Music();
    private final Mixer mixer;
    private final SeededRandom random = new SeededRandom(1);
    private final Particles particles = new Particles(800, PALETTE);
    private final List<Burst> bursts = new ArrayList<>();
    private final List<Ring> rings = new ArrayList<>();
    private final List<Scorch> scorches = new ArrayList<>();
    private final List<Text> texts = new ArrayList<>();
    private final Map<Sound, Double> played = new HashMap<>();
    private final Font font = new Font(Font.SANS_SERIF, Font.BOLD, 8);
    private final Ellipse2D.Double ellipse = new Ellipse2D.Double();
    private double time;

    Effects(Mixer mixer) {
        this.mixer = mixer;
    }

    double time() {
        return time;
    }

    /** A new level, or the title: nothing left from before, no music. */
    void clear() {
        particles.clear();
        bursts.clear();
        rings.clear();
        scorches.clear();
        texts.clear();
        mixer.stopMusic();
    }

    void music() {
        mixer.playMusic(music.march, true);
    }

    void tick() {
        play(sounds.tick);
    }

    void no() {
        play(sounds.no);
    }

    void update(double seconds) {
        time += seconds;
        particles.update(seconds, 0);
        bursts.removeIf(burst -> time - burst.born() > BURST_SECONDS);
        rings.removeIf(ring -> time - ring.born() > FROST_SECONDS);
        scorches.removeIf(scorch -> time - scorch.born() > SCORCH_SECONDS);
        texts.removeIf(text -> time - text.born() > TEXT_SECONDS);
    }

    // --8<-- [start:react]
    /** Shows and, if {@code audible}, plays what happened in the last step. */
    void react(List<Event> events, boolean audible) {
        for (Event event : events) {
            switch (event) {
                case Event.Built built -> {
                    dust(built.tower().x(), built.tower().y(), 14, STONE);
                    if (audible) play(sounds.build);
                }
                case Event.Upgraded upgraded -> {
                    sparkle(upgraded.tower().x(), upgraded.tower().y(), 14, GOLD);
                    if (audible) play(sounds.upgrade);
                }
                case Event.Sold sold -> {
                    dust(sold.tower().x(), sold.tower().y(), 18, STONE);
                    say(sold.tower().x(), sold.tower().y() - 6, "+" + sold.refund(), Hud.GOLD);
                    if (audible) play(sounds.coins);
                }
                case Event.Fired fired -> {
                    Tower tower = fired.tower();
                    if (tower.type().shot() == TowerType.Shot.BALL) {
                        smoke(tower.x() + Math.cos(tower.angle()) * 11, tower.y() + Math.sin(tower.angle()) * 11,
                                tower.angle());
                        if (audible) play(sounds.cannon);
                    } else if (audible) {
                        play(sounds.bow);
                    }
                }
                case Event.Frosted frosted -> {
                    Tower tower = frosted.tower();
                    rings.add(new Ring(tower.x(), tower.y(), tower.type().range() * Field.TILE, time));
                    if (audible) play(sounds.frost);
                }
                case Event.ArrowHit(double x, double y) -> {
                    sparkle(x, y, 2, DIRT);
                    if (audible) play(sounds.hit);
                }
                case Event.Burst(double x, double y, double radius) -> {
                    bursts.add(new Burst(x, y, radius, time));
                    scorches.add(new Scorch(x, y, radius * 0.9, time));
                    if (scorches.size() > MOST_SCORCHES) scorches.removeFirst();
                    sparkle(x, y, 10, DIRT);
                    sparkle(x, y, 6, FIRE);
                    dust(x, y, 8, DARK_SMOKE);
                    if (audible) play(sounds.burst);
                }
                case Event.Killed killed -> {
                    Creep creep = killed.creep();
                    sparkle(creep.x(), creep.y(), 8, BLOOD);
                    say(creep.x(), creep.y() - 8, "+" + killed.bounty(), Hud.GOLD);
                    if (audible) play(sounds.fall);
                }
                case Event.Leaked leaked -> {
                    sparkle(leaked.creep().x(), leaked.creep().y(), 12, BLOOD);
                    say(leaked.creep().x(), leaked.creep().y() - 8, "-" + leaked.harm(), Hud.RED);
                    if (audible) play(sounds.leak);
                }
                case Event.WaveStarted started -> {
                    if (audible) play(sounds.horn);
                }
                case Event.Won won -> {
                    if (audible) mixer.playMusic(music.won, false);
                }
                case Event.Lost lost -> {
                    if (audible) mixer.playMusic(music.lost, false);
                }
            }
        }
    }
    // --8<-- [end:react]

    private void play(Sound sound) {
        Double last = played.get(sound);
        if (last != null && time - last < SOUND_GAP) return;
        played.put(sound, time);
        mixer.play(sound);
    }

    /** Powder smoke blown out of the muzzle, along {@code angle}. */
    private void smoke(double x, double y, double angle) {
        for (int i = 0; i < 7; i++) {
            double a = angle + (random.nextDouble() - 0.5) * 0.8;
            double v = 10 + random.nextDouble() * 25;
            particles.spawn(x, y, Math.cos(a) * v, Math.sin(a) * v, 0.5 + random.nextDouble() * 0.5,
                    2 + random.nextDouble() * 2.5, i % 3 == 0 ? DARK_SMOKE : SMOKE);
        }
    }

    /** A slow cloud around a point. */
    private void dust(double x, double y, int count, int colour) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * 2 * Math.PI;
            double v = 6 + random.nextDouble() * 18;
            particles.spawn(x, y, Math.cos(a) * v, Math.sin(a) * v, 0.5 + random.nextDouble() * 0.4,
                    1.5 + random.nextDouble() * 2.5, colour);
        }
    }

    /** Quick bits flying out in all directions. */
    private void sparkle(double x, double y, int count, int colour) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * 2 * Math.PI;
            double v = 25 + random.nextDouble() * 50;
            particles.spawn(x, y, Math.cos(a) * v, Math.sin(a) * v, 0.25 + random.nextDouble() * 0.25,
                    0.8 + random.nextDouble() * 1.2, colour);
        }
    }

    private void say(double x, double y, String text, Color colour) {
        texts.add(new Text(x, y, text, colour, time));
    }

    /** On the ground, under the enemies and the towers: the scorch marks. */
    void renderGround(Graphics2D g) {
        for (Scorch scorch : scorches) {
            double left = 1 - (time - scorch.born()) / SCORCH_SECONDS;
            int a = (int) Math.round(110 * Math.min(1, left * 3));
            g.setColor(new Color(20, 14, 8, a));
            ellipse.setFrame(scorch.x() - scorch.size() / 2, scorch.y() - scorch.size() * 0.4, scorch.size(),
                    scorch.size() * 0.8);
            g.fill(ellipse);
        }
    }

    /** Above everything else in the world. */
    void render(Graphics2D g, double alpha) {
        for (Ring ring : rings) {
            double t = (time - ring.born()) / FROST_SECONDS;
            double r = ring.radius() * (0.3 + 0.7 * t);
            int a = (int) Math.round(150 * (1 - t));
            g.setColor(new Color(200, 245, 255, a / 3));
            ellipse.setFrame(ring.x() - r, ring.y() - r, 2 * r, 2 * r);
            g.fill(ellipse);
            g.setColor(new Color(225, 250, 255, a));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(ellipse);
        }
        for (Burst burst : bursts) {
            double t = (time - burst.born()) / BURST_SECONDS;
            Art.fireball(g, burst.x(), burst.y(), burst.radius() * (0.5 + 0.6 * t), 1 - t * t);
        }
        particles.render(g, alpha);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        for (Text text : texts) {
            double t = (time - text.born()) / TEXT_SECONDS;
            int a = (int) Math.round(255 * Math.min(1, (1 - t) * 3));
            float x = (float) (text.x() - metrics.stringWidth(text.text()) / 2.0);
            float y = (float) (text.y() - 10 * t);
            g.setColor(new Color(0, 0, 0, a / 2));
            g.drawString(text.text(), x + 0.6f, y + 0.6f);
            Color c = text.colour();
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), a));
            g.drawString(text.text(), x, y);
        }
    }

    /** How many particles fly: for the tests. */
    int particles() {
        return particles.count();
    }

    int scorches() {
        return scorches.size();
    }
}
