package eu.amadeos.games.towers.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.gameengine.save.SaveFile;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.gameengine.tiled.TiledMap;
import eu.amadeos.games.towers.bootstrap.TowersModule;
import eu.amadeos.games.towers.domain.Field;
import eu.amadeos.games.towers.domain.Game;
import eu.amadeos.games.towers.domain.Level;
import eu.amadeos.games.towers.domain.Mode;
import eu.amadeos.games.towers.domain.Rules;
import eu.amadeos.games.towers.domain.Tower;
import eu.amadeos.games.towers.domain.Wave;
import eu.amadeos.games.towers.repository.Progress;
import eu.amadeos.games.towers.repository.TowersSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The game without a window and without the loop thread: the test moves the mouse, clicks, presses keys and
 * calls the steps itself, so everything happens in order on the test thread.
 */
class TowersGameTest {
    private static final double STEP = TowersGame.STEP;

    @TempDir
    Path dir;

    private final long[] now = {0};
    private final Keyboard keyboard = new Keyboard(() -> now[0]);
    private final Mouse mouse = new Mouse();
    private Progress progress;
    private TowersGame game;

    @BeforeEach
    void create() {
        Messages msg = Messages.load(TowersModule.class, TowersModule.MESSAGES, Locale.ENGLISH);
        progress = new Progress(new SaveFile(dir.resolve("progress.save")));
        game = new TowersGame(keyboard, mouse, msg, new TowersSettings(dir.resolve("towers.properties")), progress,
                new Mixer());
    }

    private void tap(int key) {
        keyboard.pressed(key, now[0]);
        keyboard.released(key, now[0] + 1_000_000);
        steps(5);
    }

    private void steps(int n) {
        for (int i = 0; i < n; i++) {
            now[0] += (long) (STEP * 1e9);
            game.update(STEP);
        }
    }

    private void seconds(double seconds) {
        steps((int) Math.round(seconds * TowersGame.UPDATES_PER_SECOND));
    }

    /** The mouse over the middle of a tile, and a click there. */
    private void clickOn(TowersGame.PlayScene play, Cell cell) {
        mouse.moved(play.camera().screenX((cell.col() + 0.5) * Field.TILE, 1),
                play.camera().screenY((cell.row() + 0.5) * Field.TILE, 1));
        steps(1);
        click();
    }

    private void click() {
        mouse.pressed(MouseEvent.BUTTON1);
        steps(1);
        mouse.released(MouseEvent.BUTTON1);
        steps(1);
    }

    /** A grass tile beside the trail, in the middle of the view, where a tower may stand. */
    private static Cell free(TowersGame.PlayScene play) {
        Game g = play.game();
        int col = (int) ((play.camera().x() + Field.VIEW_WIDTH / 2) / Field.TILE);
        for (int row = 3; row < g.level().height() - 3; row++) {
            for (int c = col; c < col + 6; c++) {
                if (g.trail(c, row) == 0 && g.placement(c, row) == Game.Placement.OK) return new Cell(c, row);
            }
        }
        throw new AssertionError("no free tile");
    }

    /** Battlefield 1 with a battle of its own: the gold, the lives and the waves of a test. */
    private Game battle(int gold, int lives, String unit, int count) {
        Rules rules = game.rules();
        Level level = Level.read(1, TiledMap.load(Level.class, Level.FOLDER + Level.FILES.getFirst()),
                new Rules.Plan(1, gold, lives, 0, List.of(new Wave(List.of(new Wave.Group(rules.unit(unit), count, 0.5))))));
        return new Game(level, rules, Mode.OPEN);
    }

    @Test
    void fromTheTitleToTheFirstBattle() {
        assertInstanceOf(TowersGame.TitleScene.class, game.top());
        click();
        TowersGame.ChoiceScene choice = assertInstanceOf(TowersGame.ChoiceScene.class, game.top());
        tap(KeyEvent.VK_RIGHT);
        assertEquals(1, choice.chosen(), "battlefield 2 is still locked");
        tap(KeyEvent.VK_ENTER);
        TowersGame.PlayScene play = assertInstanceOf(TowersGame.PlayScene.class, game.top());
        assertEquals(1, play.game().level().number());
        tap(KeyEvent.VK_ESCAPE);
        assertInstanceOf(TowersGame.PauseScene.class, game.top());
        tap(KeyEvent.VK_ESCAPE);
        assertInstanceOf(TowersGame.ChoiceScene.class, game.top());
    }

    @Test
    void battlesAreOnTheRoadUntilTheChoiceSwitchesToOpenGround() {
        click();
        assertInstanceOf(TowersGame.ChoiceScene.class, game.top());
        tap(KeyEvent.VK_ENTER);
        TowersGame.PlayScene road = assertInstanceOf(TowersGame.PlayScene.class, game.top());
        assertEquals(Mode.ROAD, road.game().mode());
        tap(KeyEvent.VK_ESCAPE);
        tap(KeyEvent.VK_ESCAPE);
        tap(KeyEvent.VK_M);
        tap(KeyEvent.VK_ENTER);
        TowersGame.PlayScene open = assertInstanceOf(TowersGame.PlayScene.class, game.top());
        assertEquals(Mode.OPEN, open.game().mode());
        assertEquals(Mode.OPEN, new TowersSettings(dir.resolve("towers.properties")).mode(), "remembered");
    }

    // --8<-- [start:test-build]
    @Test
    void aClickOnTheGrassOpensTheBuildMenuAndItsFirstRowBuildsAnArcher() {
        TowersGame.PlayScene play = game.play(1);
        Cell cell = free(play);
        clickOn(play, cell);
        assertTrue(play.menu().isOpen());
        assertEquals(3, play.menu().items().size(), "archer, cannon, frost");
        int gold = play.game().gold();
        tap(KeyEvent.VK_1);
        Tower tower = play.game().tower(cell.col(), cell.row());
        assertNotNull(tower);
        assertEquals("archer", tower.type().id());
        assertEquals(gold - 40, play.game().gold());
        assertFalse(play.menu().isOpen());
    }

    @Test
    void aClickOnATowerOpensItsMenuToUpgradeOrSellIt() {
        TowersGame.PlayScene play = game.play(1);
        Cell cell = free(play);
        play.game().build(cell.col(), cell.row(), game.rules().tower("archer"));
        clickOn(play, cell);
        assertTrue(play.menu().isOpen());
        assertInstanceOf(TowersGame.Upgrade.class, play.menu().items().get(0).value());
        tap(KeyEvent.VK_ENTER);
        assertEquals("archer-2", play.game().tower(cell.col(), cell.row()).type().id());
        clickOn(play, cell);
        tap(KeyEvent.VK_2);
        assertNull(play.game().tower(cell.col(), cell.row()), "sold");
    }
    // --8<-- [end:test-build]

    // --8<-- [start:test-refuse]
    @Test
    void aTowerThatWouldCloseTheLastWayIsRefusedWithAReason() {
        Game battle = battle(5000, 20, "orc", 1);
        TowersGame.PlayScene play = game.play(battle);
        // A wall across the meadow; the tile the rules refuse is the last gap.
        Cell gap = null;
        for (int row = 0; row < battle.level().height(); row++) {
            if (battle.build(12, row, game.rules().tower("archer")) == Game.Placement.BLOCKS) gap = new Cell(12, row);
        }
        assertNotNull(gap);
        play.camera().jumpTo(gap.col() * Field.TILE, gap.row() * Field.TILE);
        steps(1);
        clickOn(play, gap);
        tap(KeyEvent.VK_1);
        assertNull(battle.tower(gap.col(), gap.row()));
        assertEquals("That would close the last way to the castle", play.toast());
    }
    // --8<-- [end:test-refuse]

    @Test
    void theMapMovesWithTheKeysAndWithTheRightMouseButtonHeld() {
        TowersGame.PlayScene play = game.play(3);
        play.camera().jumpTo(0, 0);
        steps(1);
        keyboard.pressed(KeyEvent.VK_RIGHT, now[0]);
        seconds(0.5);
        keyboard.released(KeyEvent.VK_RIGHT, now[0]);
        seconds(0.1);
        assertEquals(0.5 * TowersGame.PAN_SPEED, play.camera().x(), 12, "the key counts as held until it is surely up");
        double x = play.camera().x();
        mouse.moved(300, 200);
        steps(1);
        mouse.pressed(MouseEvent.BUTTON3);
        steps(1);
        mouse.moved(250, 170);
        steps(1);
        mouse.released(MouseEvent.BUTTON3);
        steps(1);
        assertEquals(x + 50, play.camera().x(), 1e-9, "dragged 50 to the left: the map follows the mouse");
        assertEquals(30, play.camera().y(), 1e-9);
    }

    // --8<-- [start:test-wide]
    @Test
    void aWideScreenShowsMoreMapAndKeepsTheButtonsAtTheRightEdge() {
        game.resize(711);                       // 16:9: 711 × 400
        TowersGame.PlayScene play = game.play(1);
        steps(2);
        assertEquals(711, play.camera().viewWidth(), 1e-9, "the map gets the extra width");
        mouse.moved(711 - 42 - 184 + 60, 10);   // on "next wave", 71 units right of where 640 has it
        steps(1);
        click();
        assertEquals(1, play.game().wavesStarted());
        play.camera().jumpTo(0, 0);
        mouse.moved(710, 200);                  // the real right edge of the screen moves the map
        double x = play.camera().x();
        steps(10);
        assertTrue(play.camera().x() > x);
    }
    // --8<-- [end:test-wide]

    @Test
    void nCallsTheNextWaveForGoldAndFDoublesTheSpeed() {
        TowersGame.PlayScene play = game.play(1);
        int gold = play.game().gold();
        seconds(10.5);
        tap(KeyEvent.VK_N);
        assertEquals(1, play.game().wavesStarted());
        assertEquals(gold + 20, play.game().gold(), "19.5 of the first 30 seconds were left: 20 gold");
        double time = play.game().time();
        tap(KeyEvent.VK_F);
        assertTrue(play.fast());
        seconds(1);
        assertEquals(time + 2 + 10 * STEP, play.game().time(), 1e-6, "two seconds of battle in one, from the tap on");
    }

    // --8<-- [start:test-won]
    @Test
    void aWonBattleIsSavedAndOpensTheNextBattlefield() {
        Game battle = battle(1000, 20, "orc", 2);
        TowersGame.PlayScene play = game.play(battle);
        for (int row = 8; row <= 12; row++) battle.build(3, row, game.rules().tower("archer"));
        tap(KeyEvent.VK_N);
        seconds(30);
        assertEquals(Game.Phase.WON, battle.phase());
        assertInstanceOf(TowersGame.ResultScene.class, game.top());
        assertEquals(3, progress.stars(1));
        assertEquals(2, progress.open());
        seconds(1);
        click();
        TowersGame.PlayScene next = assertInstanceOf(TowersGame.PlayScene.class, game.top());
        assertEquals(2, next.game().level().number());
    }
    // --8<-- [end:test-won]

    @Test
    void aLostBattleCanBeFoughtAgain() {
        Game battle = battle(0, 1, "wolf", 1);
        game.play(battle);
        tap(KeyEvent.VK_N);
        seconds(40);
        assertEquals(Game.Phase.LOST, battle.phase());
        assertInstanceOf(TowersGame.ResultScene.class, game.top());
        assertEquals(1, progress.open());
        tap(KeyEvent.VK_ENTER);
        TowersGame.PlayScene again = assertInstanceOf(TowersGame.PlayScene.class, game.top());
        assertEquals(1, again.game().level().number());
        assertEquals(Game.Phase.PLAYING, again.game().phase());
    }

    @Test
    void everySceneAndBattlefieldCanBeDrawn() {
        BufferedImage image = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.scale(2, 2);
        game.render(g, 0.5);
        game.renderOverlay(g, 0.5);
        for (int level = 1; level <= 3; level++) {
            game.play(level);
            tap(KeyEvent.VK_N);
            tap(KeyEvent.VK_F3);
            seconds(3);
            game.render(g, 0.5);
            game.renderOverlay(g, 0.5);
        }
        game.chooseLevel();
        game.render(g, 0.5);
        game.renderOverlay(g, 0.5);
        g.dispose();
        assertTrue(image.getRGB(640, 400) != 0, "something was drawn");
    }
}
