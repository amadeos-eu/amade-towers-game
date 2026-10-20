package eu.amadeos.games.towers.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.gameengine.save.SaveFile;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.gameengine.tiled.TiledMap;
import eu.amadeos.games.towers.bootstrap.TowersModule;
import eu.amadeos.games.towers.domain.Defender;
import eu.amadeos.games.towers.domain.Field;
import eu.amadeos.games.towers.domain.Game;
import eu.amadeos.games.towers.domain.Level;
import eu.amadeos.games.towers.domain.Mode;
import eu.amadeos.games.towers.domain.Rules;
import eu.amadeos.games.towers.domain.Tower;
import eu.amadeos.games.towers.repository.Progress;
import eu.amadeos.games.towers.repository.TowersSettings;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The screenshots of chapter 30, drawn without a screen: the title, the choice of a battlefield, a battle
 * with the build menu open, the same battle with its flow field, a tower's menu, the other two
 * battlefields and a won battle. {@code ./gradlew screenshots -Pshots=<folder>}.
 */
public final class Screenshots {
    private final long[] now = {0};
    private final Keyboard keyboard = new Keyboard(() -> now[0]);
    private final Mouse mouse = new Mouse();
    private final TowersGame game;

    private Screenshots() throws Exception {
        Messages msg = Messages.load(TowersModule.class, TowersModule.MESSAGES, Locale.ENGLISH);
        Path dir = Files.createTempDirectory("towers");
        Progress progress = new Progress(new SaveFile(dir.resolve("progress.save")));
        progress.won(1, 3, 3);       // one battlefield won, so the choice shows stars and a lock
        game = new TowersGame(keyboard, mouse, msg, new TowersSettings(dir.resolve("towers.properties")), progress,
                new Mixer());
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "screenshots");
        dir.mkdirs();
        Screenshots shots = new Screenshots();
        shots.steps(6 * 120);
        shots.save(new File(dir, "title.png"), false);
        shots.tap(KeyEvent.VK_ENTER);
        shots.steps(60);
        shots.save(new File(dir, "choice.png"), false);

        // The meadow in the seventh wave, with a maze of towers built with more gold than a battle brings.
        TowersGame.PlayScene meadow = shots.maze();
        Cell free = shots.freeTile(meadow, 21, 12);
        shots.point(meadow, free);
        shots.click();
        shots.steps(20);
        shots.save(new File(dir, "battle.png"), true);
        shots.tap(KeyEvent.VK_ESCAPE);
        shots.tap(KeyEvent.VK_F3);
        shots.save(new File(dir, "flowfield.png"), false);
        shots.tap(KeyEvent.VK_F3);
        Tower cannon = meadow.game().towers().stream()
                .filter(t -> t.type().kind().equals("cannon") && inMiddle(meadow, t.x(), t.y())).findFirst()
                .orElseThrow();
        shots.point(meadow, cannon.cell());
        shots.click();
        shots.steps(20);
        shots.save(new File(dir, "tower-menu.png"), true);
        shots.tap(KeyEvent.VK_ESCAPE);

        // The meadow on the road, the ford and the two caves, as the test player defends them.
        TowersGame.PlayScene road = shots.battle(1, 6);
        road.camera().jumpTo(road.camera().worldWidth() / 2, road.camera().worldHeight() / 2);
        shots.steps(2);
        shots.save(new File(dir, "road.png"), false);
        TowersGame.PlayScene ford = shots.battle(2, 8);
        ford.camera().jumpTo(ford.camera().worldWidth() / 2, ford.camera().worldHeight() / 2);
        shots.steps(2);
        shots.save(new File(dir, "ford.png"), false);
        TowersGame.PlayScene caves = shots.battle(3, 9);
        caves.camera().jumpTo(0, caves.camera().worldHeight() / 2);
        shots.steps(2);
        shots.save(new File(dir, "caves.png"), false);

        System.exit(0);
    }

    /** Battlefield {@code number}, played by the test player until wave {@code wave} is well on its way. */
    private TowersGame.PlayScene battle(int number, int wave) {
        TowersGame.PlayScene play = game.play(number);
        Defender defender = new Defender(play.game());
        for (int step = 0; play.game().wavesStarted() < wave || step % 120 != 100; step++) {
            if (step % 120 == 0) defender.spend();
            if (play.game().countdown().isPresent() && play.game().wavesStarted() < wave) play.game().callWave();
            steps(1);
        }
        for (int i = 0; i < 6 * 120; i++) {
            if (i % 120 == 0) defender.spend();
            steps(1);
        }
        return play;
    }

    /**
     * The meadow with walls of towers across it, a gap at one end of each, so the enemies walk a long
     * serpentine: what a player builds over many battles, at once.
     */
    private TowersGame.PlayScene maze() {
        Rules rules = game.rules();
        Rules.Plan plan = rules.plan(1);
        Rules.Plan rich = new Rules.Plan(1, 5000, plan.lives(), plan.tougher(), plan.waves());
        Level level = Level.read(1, TiledMap.load(Level.class, Level.FOLDER + Level.FILES.getFirst()), rich);
        Game battle = new Game(level, rules, Mode.OPEN);
        String[] kinds = {"archer", "cannon", "archer", "frost", "archer", "cannon"};
        int n = 0;
        int[] walls = {6, 12, 18, 24, 29};
        for (int w = 0; w < walls.length; w++) {
            boolean gapAtTop = w % 2 == 0;
            for (int i = 0; i < level.height(); i++) {
                int row = gapAtTop ? level.height() - 1 - i : i;
                if (battle.build(walls[w], row, rules.tower(kinds[n % kinds.length])) == Game.Placement.OK) n++;
            }
        }
        for (Tower tower : battle.towers()) {
            if (tower.cell().row() % 4 == 1) battle.upgrade(tower);
            if (tower.cell().row() % 8 == 1) battle.upgrade(tower);
        }
        TowersGame.PlayScene play = game.play(battle);
        for (int step = 0; battle.wavesStarted() < 7 || step % 120 != 100; step++) {
            if (battle.countdown().isPresent() && battle.wavesStarted() < 7) battle.callWave();
            steps(1);
        }
        steps(9 * 120);
        play.camera().jumpTo(play.camera().worldWidth() / 2, play.camera().worldHeight() / 2);
        steps(2);
        return play;
    }

    /** A tile near {@code col}, {@code row} where a tower may stand. */
    private Cell freeTile(TowersGame.PlayScene play, int col, int row) {
        Game g = play.game();
        for (int d = 0; d < 10; d++) {
            for (int r = row - d; r <= row + d; r++) {
                for (int c = col - d; c <= col + d; c++) {
                    if (g.trail(c, r) == 0 && g.placement(c, r) == Game.Placement.OK) return new Cell(c, r);
                }
            }
        }
        throw new IllegalStateException("no free tile");
    }

    /** Away from the bar and the edges, where a click would scroll the map or press a button. */
    private static boolean inMiddle(TowersGame.PlayScene play, double x, double y) {
        double sx = play.camera().screenX(x, 1);
        double sy = play.camera().screenY(y, 1);
        return sx > 60 && sx < Field.WIDTH - 200 && sy > Field.BAR + 50 && sy < Field.HEIGHT - 60;
    }

    private double mouseX;
    private double mouseY;

    /** The mouse over the middle of a tile. */
    private void point(TowersGame.PlayScene play, Cell cell) {
        mouseX = play.camera().screenX((cell.col() + 0.6) * Field.TILE, 1);
        mouseY = play.camera().screenY((cell.row() + 0.6) * Field.TILE, 1);
        mouse.moved(mouseX, mouseY);
        steps(2);
    }

    private void click() {
        mouse.pressed(MouseEvent.BUTTON1);
        steps(1);
        mouse.released(MouseEvent.BUTTON1);
        steps(1);
    }

    private void tap(int key) {
        keyboard.pressed(key, now[0]);
        keyboard.released(key, now[0] + 1_000_000);
        steps(5);
    }

    private void steps(int n) {
        for (int i = 0; i < n; i++) {
            now[0] += (long) (TowersGame.STEP * 1e9);
            game.update(TowersGame.STEP);
        }
    }

    /** World and overlay at twice the logical size, with the mouse pointer if {@code pointer}. */
    private void save(File file, boolean pointer) throws Exception {
        game.settle();
        write(file, g -> {
            game.render(g, 1);
            game.renderOverlay(g, 1);
            if (pointer) pointer(g, mouseX, mouseY);
        });
    }

    private static void pointer(Graphics2D g, double x, double y) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(x, y + 13);
        p.lineTo(x + 3.5, y + 10);
        p.lineTo(x + 6, y + 15);
        p.lineTo(x + 8, y + 14);
        p.lineTo(x + 5.5, y + 9.5);
        p.lineTo(x + 9.5, y + 9.5);
        p.closePath();
        g.setColor(Color.WHITE);
        g.fill(p);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1f));
        g.draw(p);
    }

    /** At twice the logical size. */
    private static void write(File file, Consumer<Graphics2D> draw) throws Exception {
        int scale = 2;
        BufferedImage image = new BufferedImage((int) Field.WIDTH * scale, (int) Field.HEIGHT * scale,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(scale, scale);
        draw.accept(g);
        g.dispose();
        ImageIO.write(image, "png", file);
    }

    static List<String> names() {
        return List.of("title", "choice", "battle", "flowfield", "tower-menu", "road", "ford", "caves");
    }
}
