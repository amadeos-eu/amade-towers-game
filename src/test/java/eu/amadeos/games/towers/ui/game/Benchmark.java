package eu.amadeos.games.towers.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.gameengine.save.SaveFile;
import eu.amadeos.games.towers.bootstrap.TowersModule;
import eu.amadeos.games.towers.domain.Defender;
import eu.amadeos.games.towers.domain.Field;
import eu.amadeos.games.towers.domain.Level;
import eu.amadeos.games.towers.repository.Progress;
import eu.amadeos.games.towers.repository.TowersSettings;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * How long a frame of each battlefield takes to draw, in the middle of its fifth wave with the test
 * player's towers, at two window sizes: {@code ./gradlew benchmark}. The map scrolls slowly from left to
 * right. The frames are drawn into a compatible image, as the canvas draws, but without waiting for the
 * screen.
 */
public final class Benchmark {
    static final int WARM_UP = 60;
    static final int FRAMES = 300;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("towers");
        Messages msg = Messages.load(TowersModule.class, TowersModule.MESSAGES, Locale.ENGLISH);
        TowersGame game = new TowersGame(new Keyboard(), new Mouse(), msg, new TowersSettings(dir.resolve("towers.properties")),
                new Progress(new SaveFile(dir.resolve("progress.save"))), new Mixer());
        GraphicsConfiguration gc = GraphicsEnvironment.isHeadless() ? null
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        System.out.println("ms per frame              frame   units  shots");
        for (int scale : new int[]{2, 3}) {
            for (int number = 1; number <= Level.FILES.size(); number++) {
                TowersGame.PlayScene play = game.play(number);
                Defender defender = new Defender(play.game());
                for (int step = 0; play.game().wavesStarted() < 5 || step % 600 != 300; step++) {
                    if (step % 120 == 0) defender.spend();
                    if (play.game().countdown().isPresent()) play.game().callWave();
                    play.step(TowersGame.STEP);
                }
                int w = (int) Field.WIDTH * scale;
                int h = (int) Field.HEIGHT * scale;
                BufferedImage image = gc != null ? gc.createCompatibleImage(w, h) : new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                long total = 0;
                int units = 0;
                int shots = 0;
                for (int i = 0; i < WARM_UP + FRAMES; i++) {
                    play.step(TowersGame.STEP);
                    play.step(TowersGame.STEP);
                    play.camera().moveBy(0.5, 0);
                    Graphics2D g = image.createGraphics();
                    g.scale(scale, scale);
                    long start = System.nanoTime();
                    game.render(g, 0.5);
                    game.renderOverlay(g, 0.5);
                    if (i >= WARM_UP) {
                        total += System.nanoTime() - start;
                        units += play.game().creeps().size();
                        shots += play.game().shots().size();
                    }
                    g.dispose();
                }
                System.out.printf(Locale.ROOT, "battle %d at %4d x %4d   %6.2f   %5d  %5d%n", number, w, h,
                        total / 1e6 / FRAMES, units / FRAMES, shots / FRAMES);
            }
        }
        System.exit(0);
    }
}
