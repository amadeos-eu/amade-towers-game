package eu.amadeos.games.towers.ui.game;

import com.google.inject.Inject;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.input.Controls;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.gameengine.loop.GameLoop;
import eu.amadeos.gameengine.menu.OverlayMenu;
import eu.amadeos.gameengine.scene.Scene;
import eu.amadeos.gameengine.scene.SceneStack;
import eu.amadeos.gameengine.swing.GameCanvas;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.towers.domain.Event;
import eu.amadeos.games.towers.domain.Field;
import eu.amadeos.games.towers.domain.Game;
import eu.amadeos.games.towers.domain.Level;
import eu.amadeos.games.towers.domain.Mode;
import eu.amadeos.games.towers.domain.Rules;
import eu.amadeos.games.towers.domain.Tower;
import eu.amadeos.games.towers.domain.TowerType;
import eu.amadeos.games.towers.repository.Progress;
import eu.amadeos.games.towers.repository.TowersSettings;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The running game: the loop, the mouse and the keys, the effects and the scenes (the title with a battle
 * going on, the choice of a battlefield, a battle, the pause and its end). Everything here runs on the
 * game loop thread, except the {@link Arena} methods, which only post commands to it.
 */
public final class TowersGame implements Arena {
    enum Action { LEFT, RIGHT, UP, DOWN, NEXT, FAST, FLOW, MODE, PAUSE, BACK, OK, ONE, TWO, THREE }

    static final int UPDATES_PER_SECOND = 120;
    static final int FRAMES_PER_SECOND = 60;
    static final double STEP = 1.0 / UPDATES_PER_SECOND;
    /** How fast the keys and the edge of the screen move the map, in units per second. */
    static final double PAN_SPEED = 360;
    /** The mouse this close to the edge of the screen moves the map. */
    static final double EDGE = 3;
    /** How long a battle goes on behind its end before the result shows. */
    static final double END_SECONDS = 1.5;
    static final double TITLE_SPEED = 12;
    /** The baseline of the line on the choice screen that says how battles are fought. */
    static final double MODE_LINE = 296;
    static final double MENU_WIDTH = 132;
    static final double MENU_ROW = 26;
    static final double MENU_TITLE = 19;

    /** What a menu on the map does. */
    sealed interface Order {
    }

    record Build(TowerType type) implements Order {
    }

    record Upgrade() implements Order {
    }

    record Sell() implements Order {
    }

    private final Keyboard keyboard;
    private final Mouse mouse;
    private final Messages msg;
    private final Progress progress;
    private final TowersSettings settings;
    private final Mixer mixer;
    private final Rules rules;
    private final TowersPainter painter = new TowersPainter();
    private final Hud hud;
    private final Effects effects;
    private final SceneStack scenes = new SceneStack();
    private final Controls<Action> controls;
    private final List<Level> levels = new ArrayList<>();
    /** The ground of a level, on the road and on open ground, is made once: it takes a moment. */
    private final Map<String, Terrain> terrains = new HashMap<>();
    private final OverlayMenu.Style menuStyle;
    private int chosen = 1;
    /** On the road or on open ground, as the choice screen last set it. */
    private Mode mode;
    private GameLoop loop;
    private GameCanvas canvas;
    /** The logical width of the screen: {@link Field#WIDTH}, or more on a wide screen. */
    private double width = Field.WIDTH;

    @Inject
    public TowersGame(Keyboard keyboard, Mouse mouse, Messages msg, TowersSettings settings, Progress progress,
                      Mixer mixer) {
        this.keyboard = keyboard;
        this.mouse = mouse;
        this.msg = msg;
        this.progress = progress;
        this.settings = settings;
        this.mode = settings.mode();
        this.mixer = mixer;
        this.hud = new Hud(msg);
        this.effects = new Effects(mixer);
        this.rules = Rules.load();
        for (int i = 1; i <= Level.FILES.size(); i++) levels.add(Level.load(i, rules));
        this.menuStyle = OverlayMenu.Style.DEFAULT.withDetailIcon(painter.pictures().coin);
        // --8<-- [start:controls]
        controls = new Controls<>(Action.class, keyboard)
                .bind(Action.LEFT, settings.keys("left", KeyEvent.VK_LEFT, KeyEvent.VK_A))
                .bind(Action.RIGHT, settings.keys("right", KeyEvent.VK_RIGHT, KeyEvent.VK_D))
                .bind(Action.UP, settings.keys("up", KeyEvent.VK_UP, KeyEvent.VK_W))
                .bind(Action.DOWN, settings.keys("down", KeyEvent.VK_DOWN, KeyEvent.VK_S))
                .bind(Action.NEXT, settings.keys("next", KeyEvent.VK_N))
                .bind(Action.FAST, settings.keys("fast", KeyEvent.VK_F))
                .bind(Action.FLOW, settings.keys("flow", KeyEvent.VK_F3))
                .bind(Action.MODE, settings.keys("mode", KeyEvent.VK_M))
                .bind(Action.PAUSE, settings.keys("pause", KeyEvent.VK_P))
                .bind(Action.BACK, KeyEvent.VK_ESCAPE)
                .bind(Action.OK, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)
                .bind(Action.ONE, KeyEvent.VK_1, KeyEvent.VK_NUMPAD1)
                .bind(Action.TWO, KeyEvent.VK_2, KeyEvent.VK_NUMPAD2)
                .bind(Action.THREE, KeyEvent.VK_3, KeyEvent.VK_NUMPAD3);
        // --8<-- [end:controls]
        scenes.push(new TitleScene());
    }

    /** Starts the loop; the canvas draws the scenes' worlds and then their overlays. */
    public void start(GameCanvas canvas) {
        this.canvas = canvas;
        canvas.setMaxLogicalWidth(Field.MAX_WIDTH);
        loop = new GameLoop(UPDATES_PER_SECOND, FRAMES_PER_SECOND, this::update, canvas::render);
        loop.start();
    }

    public void stop() {
        if (loop != null) loop.stop();
    }

    void update(double seconds) {
        keyboard.poll();
        mouse.poll();
        if (canvas != null) resize(canvas.logicalWidth());
        scenes.update(seconds);
    }

    /** The world of all scenes, in logical coordinates. */
    public void render(Graphics2D g, double alpha) {
        scenes.render(g, alpha);
    }

    /** The bar, the menus and the texts of all scenes, drawn after the world at the screen's full size. */
    public void renderOverlay(Graphics2D g, double alpha) {
        scenes.renderOverlay(g, alpha);
    }

    // ---- Arena: called on the Swing thread, carried out on the loop thread -----------------------

    @Override
    public void showTitle() {
        run(() -> scenes.replace(new TitleScene()));
    }

    @Override
    public void chooseLevel() {
        run(() -> scenes.replace(new ChoiceScene()));
    }

    @Override
    public void pause() {
        run(() -> {
            if (scenes.top() instanceof PlayScene) scenes.push(new PauseScene());
        });
    }

    @Override
    public void setSound(boolean on) {
        mixer.setMuted(!on);
    }

    /** On the loop thread when it runs; directly before the loop starts and in tests. */
    private void run(Runnable command) {
        if (loop != null && loop.running()) loop.post(command);
        else command.run();
    }

    // ---- for tests and screenshots ---------------------------------------------------------------

    Scene top() {
        return scenes.top();
    }

    int sceneCount() {
        return scenes.size();
    }

    Effects effects() {
        return effects;
    }

    Rules rules() {
        return rules;
    }

    /** Plays battlefield {@code number} at once, without the title and the choice. */
    PlayScene play(int number) {
        return play(new Game(levels.get(number - 1), rules, mode));
    }

    /** Plays a battle prepared by a test, on one of the battlefields or a map of its own. */
    PlayScene play(Game battle) {
        PlayScene play = new PlayScene(battle);
        scenes.replace(play);
        return play;
    }

    // ---- the scenes ------------------------------------------------------------------------------

    /** A new screen width: the map shows more or less, the bar's buttons stay at the right edge. */
    void resize(double width) {
        this.width = width;
        hud.resize(width);
    }

    /** The ground of a battle: its level, with the road or without. */
    private Terrain terrain(Game game) {
        Level level = game.level();
        return terrains.computeIfAbsent(level.number() + " " + game.mode(), key -> new Terrain(level, game.mode()));
    }

    /** The view of a battle, below the bar. */
    private Camera camera(Level level) {
        return new Camera(Field.VIEW_WIDTH + width - Field.WIDTH, Field.VIEW_HEIGHT, level.width() * Field.TILE,
                level.height() * Field.TILE)
                .placedAt(0, Field.BAR);
    }

    /** The view of the title and the choice: the whole screen, there is no bar. */
    private Camera wideCamera(Level level) {
        return new Camera(width, Field.HEIGHT, level.width() * Field.TILE, level.height() * Field.TILE);
    }

    /** The first battlefield with a battle going on, a few towers built along the way; the view drifts across. */
    final class TitleScene implements Scene {
        private Game demo;
        private final Camera camera = wideCamera(levels.getFirst());
        private Terrain terrain;
        private double time;
        private double spent;

        TitleScene() {
            effects.clear();
            restart();
        }

        private void restart() {
            demo = new Game(levels.getFirst(), rules, mode, 7);
            terrain = terrain(demo);
            demo.callWave();
            camera.jumpTo(camera.worldWidth() / 2, camera.worldHeight() / 2);
        }

        @Override
        public void update(double seconds) {
            if (controls.wasPressed(Action.OK) || mouse.wasPressed(MouseEvent.BUTTON1)) {
                scenes.replace(new ChoiceScene());
                return;
            }
            time += seconds;
            spent += seconds;
            if (spent >= 1) {
                spent = 0;
                Autobuild.spend(demo);
            }
            demo.update(seconds);
            effects.react(demo.events(), false);
            effects.update(seconds);
            if (demo.phase() != Game.Phase.PLAYING) restart();
            camera.resize(width, Field.HEIGHT);
            double way = camera.worldWidth() - camera.viewWidth();
            double along = way <= 0 ? 0 : (time * TITLE_SPEED) % (2 * way);
            camera.moveBy((along < way ? along : 2 * way - along) - camera.x(), 0);
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            painter.paint(g, demo, terrain, camera, effects, TowersPainter.Marks.NONE, alpha);
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.veil(g);
            hud.text(g, msg.get("window.title"), 120, 72, true, Hud.GOLD);
            hud.text(g, msg.get("title.start"), 200, 18, false, Hud.CHALK);
            hud.text(g, msg.get("title.build"), 262, 12, false, Hud.CHALK);
            hud.text(g, msg.get("title.keys"), 282, 12, false, Hud.CHALK);
            hud.text(g, msg.get("title.screen"), 312, 11, false, Hud.GREY);
        }

        Game demo() {
            return demo;
        }
    }

    // --8<-- [start:choice]
    /** Three cards, one for each battlefield; one opens when the one before it is won. */
    final class ChoiceScene implements Scene {
        private final Map<String, Game> fields = new HashMap<>();
        private final Map<Integer, Camera> cameras = new HashMap<>();
        private double time;

        ChoiceScene() {
            effects.clear();
            chosen = Math.min(chosen, progress.open());
        }

        @Override
        public void update(double seconds) {
            time += seconds;
            if (controls.wasPressed(Action.BACK)) {
                scenes.replace(new TitleScene());
                return;
            }
            int before = chosen;
            int open = Math.min(progress.open(), levels.size());
            if (controls.wasPressed(Action.LEFT)) chosen = Math.max(1, chosen - 1);
            if (controls.wasPressed(Action.RIGHT)) chosen = Math.min(open, chosen + 1);
            int card = cardAt(mouse.x(), mouse.y());
            if (mouse.wasMoved() && card > 0 && card <= open) chosen = card;
            if (chosen != before) effects.tick();
            boolean clicked = mouse.wasPressed(MouseEvent.BUTTON1) && card > 0 && card <= open;
            if (controls.wasPressed(Action.MODE) || mouse.wasPressed(MouseEvent.BUTTON1) && onMode(mouse.y())) {
                mode = mode == Mode.ROAD ? Mode.OPEN : Mode.ROAD;
                settings.setMode(mode);
                effects.tick();
            }
            if (controls.wasPressed(Action.OK) || clicked) scenes.replace(new PlayScene(levels.get(chosen - 1)));
        }

        /** Whether a point's height is that of the line that says how battles are fought. */
        boolean onMode(double y) {
            return Math.abs(y - (MODE_LINE - 4)) <= 10;
        }
        // --8<-- [end:choice]

        /** The card under a point, 1 for the first, or 0. */
        int cardAt(double x, double y) {
            if (Double.isNaN(x) || y < Hud.CARD_TOP || y > Hud.CARD_TOP + Hud.CARD_HEIGHT) return 0;
            for (int i = 0; i < levels.size(); i++) {
                double left = hud.cardX(i, levels.size());
                if (x >= left && x < left + Hud.CARD_WIDTH) return i + 1;
            }
            return 0;
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            Level level = levels.get(chosen - 1);
            Game field = fields.computeIfAbsent(chosen + " " + mode, key -> new Game(level, rules, mode));
            Camera camera = cameras.computeIfAbsent(chosen, n -> wideCamera(level));
            camera.resize(width, Field.HEIGHT);
            camera.jumpTo(camera.viewWidth() / 2 + time * TITLE_SPEED, camera.worldHeight() / 2);
            Terrain terrain = terrain(field);
            painter.paint(g, field, terrain, camera, effects, TowersPainter.Marks.NONE, alpha);
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.veil(g);
            hud.text(g, msg.get("select.title"), 70, 30, true, Hud.GOLD);
            for (int i = 0; i < levels.size(); i++) {
                int number = i + 1;
                hud.card(g, hud.cardX(i, levels.size()), Hud.CARD_TOP, number, number <= progress.open(),
                        number == chosen, progress);
            }
            hud.text(g, msg.get(mode == Mode.ROAD ? "select.road" : "select.open"), MODE_LINE, 13, true, Hud.GOLD);
            hud.text(g, msg.get("select.keys"), 330, 12, false, Hud.CHALK);
        }

        int chosen() {
            return chosen;
        }
    }

    // --8<-- [start:play]
    /**
     * A battle. The mouse builds: a click on grass opens the build menu, a click on a tower its menu. The
     * map moves with the keys, the right mouse button held down, or the mouse at the edge of the screen.
     */
    final class PlayScene implements Scene {
        private final Game game;
        private final Camera camera;
        private final Terrain terrain;
        private final OverlayMenu<Order> menu = new OverlayMenu<>(MENU_WIDTH, MENU_ROW, MENU_TITLE,
                4, Field.BAR + 4, Field.WIDTH - 4, Field.HEIGHT - 4);
        /** The tile the build menu is open for, or the tower whose menu is open. */
        private Cell building;
        private Tower selected;
        private Cell hover;
        private boolean fast;
        private boolean flow;
        private double dragX = Double.NaN;
        private double dragY = Double.NaN;
        private String banner;
        private double bannerShown = Double.MAX_VALUE;
        private String toast;
        private double toastShown = Double.MAX_VALUE;
        private double ended;
        private Progress.Record record;

        PlayScene(Level level) {
            this(new Game(level, rules, mode));
        }

        PlayScene(Game battle) {
            game = battle;
            Level level = battle.level();
            camera = TowersGame.this.camera(level);
            terrain = TowersGame.this.terrain(battle);
            Level.Box castle = level.castleBox();
            camera.jumpTo(camera.worldWidth() / 2, castle.y() + castle.height() / 2);
            effects.clear();
            effects.music();
            banner(msg.format("play.level", level.number(), hud.name(level.number())));
        }

        @Override
        public void update(double seconds) {
            if (game.phase() != Game.Phase.PLAYING) {
                ended += seconds;
                if (ended >= END_SECONDS) {
                    scenes.push(new ResultScene(this));
                    return;
                }
            }
            if (controls.wasPressed(Action.PAUSE) || controls.wasPressed(Action.BACK) && !menu.isOpen()) {
                scenes.push(new PauseScene());
                return;
            }
            camera.resize(Field.VIEW_WIDTH + width - Field.WIDTH, Field.VIEW_HEIGHT);
            menu.setBounds(4, Field.BAR + 4, width - 4, Field.HEIGHT - 4);
            input(seconds);
            int times = fast ? 2 : 1;
            for (int i = 0; i < times; i++) step(seconds);
            menu.update(seconds);
            bannerShown += seconds;
            toastShown += seconds;
        }

        /** One step of the rules, what they did, and the ground under it. */
        void step(double seconds) {
            game.update(seconds);
            effects.react(game.events(), true);
            effects.update(seconds);
            for (Event event : game.events()) {
                switch (event) {
                    case Event.WaveStarted started -> banner(started.bonus() > 0
                            ? msg.format("play.wave.early", started.number(), started.bonus())
                            : msg.format("play.wave", started.number()));
                    case Event.Won won -> {
                        record = progress.won(game.level().number(), won.stars(), levels.size());
                        chosen = Math.min(levels.size(), game.level().number() + 1);
                    }
                    default -> {
                    }
                }
            }
            if (selected != null && !game.towers().contains(selected)) selected = null;
        }
        // --8<-- [end:play]

        // --8<-- [start:input]
        private void input(double seconds) {
            double mx = mouse.x();
            double my = mouse.y();
            boolean onMap = !Double.isNaN(mx) && camera.shows(mx, my) && !menu.contains(mx, my);
            hover = onMap ? cellAt(mx, my) : null;
            if (menu.isOpen() && mouse.wasMoved()) menu.hover(mx, my);

            if (controls.wasPressed(Action.BACK)) closeMenu();
            if (controls.wasPressed(Action.NEXT)) game.callWave();
            if (controls.wasPressed(Action.FAST)) fast = !fast;
            if (controls.wasPressed(Action.FLOW)) flow = !flow;
            if (menu.isOpen()) {
                if (controls.wasPressed(Action.UP)) menu.move(-1);
                if (controls.wasPressed(Action.DOWN)) menu.move(1);
                if (controls.wasPressed(Action.OK)) carryOut(menu.chooseHighlighted());
                if (controls.wasPressed(Action.ONE)) carryOut(menu.key(1));
                if (controls.wasPressed(Action.TWO)) carryOut(menu.key(2));
                if (controls.wasPressed(Action.THREE)) carryOut(menu.key(3));
            }

            if (mouse.wasPressed(MouseEvent.BUTTON1)) click(mx, my);
            boolean dragging = mouse.isDown(MouseEvent.BUTTON3) || mouse.isDown(MouseEvent.BUTTON2);
            if (mouse.wasPressed(MouseEvent.BUTTON3) || mouse.wasPressed(MouseEvent.BUTTON2)) closeMenu();

            // The map moves by the keys, by the edge of the screen, and by dragging it along.
            double dx = 0;
            double dy = 0;
            if (!menu.isOpen()) {
                dx += controls.axis(Action.LEFT, Action.RIGHT) * PAN_SPEED * seconds;
                dy += controls.axis(Action.UP, Action.DOWN) * PAN_SPEED * seconds;
            }
            if (mouse.isInside() && !dragging && !Double.isNaN(mx)) {
                if (mx < EDGE) dx -= PAN_SPEED * seconds;
                if (mx > width - EDGE) dx += PAN_SPEED * seconds;
                if (my > Field.HEIGHT - EDGE) dy += PAN_SPEED * seconds;
            }
            if (dragging && !Double.isNaN(dragX)) {
                dx += dragX - mx;
                dy += dragY - my;
            }
            dragX = dragging ? mx : Double.NaN;
            dragY = dragging ? my : Double.NaN;
            double x = camera.x();
            double y = camera.y();
            camera.moveBy(dx, dy);
            if (camera.x() != x || camera.y() != y) closeMenu();
        }

        /** The tile under a point of the screen. */
        private Cell cellAt(double screenX, double screenY) {
            int col = (int) Math.floor(camera.worldX(screenX) / Field.TILE);
            int row = (int) Math.floor(camera.worldY(screenY) / Field.TILE);
            return game.level().contains(col, row) ? new Cell(col, row) : null;
        }

        /** A left click: on the bar's buttons, on the open menu, on a tower, or on the grass. */
        private void click(double mx, double my) {
            if (my < Field.BAR) {
                if (hud.nextButton.contains(mx, my)) game.callWave();
                if (hud.fastButton.contains(mx, my)) fast = !fast;
                return;
            }
            if (menu.isOpen()) {
                boolean inside = menu.contains(mx, my);
                carryOut(menu.click(mx, my));
                if (inside) return;
                closeMenu();
            }
            if (hover == null) return;
            Tower tower = game.tower(hover.col(), hover.row());
            if (tower != null) openTowerMenu(tower);
            else if (game.placement(hover.col(), hover.row()) == Game.Placement.NOT_HERE) effects.no();
            else openBuildMenu(hover);
        }
        // --8<-- [end:input]

        // --8<-- [start:menus]
        private void openBuildMenu(Cell cell) {
            List<OverlayMenu.Item<Order>> items = new ArrayList<>();
            for (TowerType type : rules.buildable()) {
                items.add(new OverlayMenu.Item<>(new Build(type), painter.pictures().icon(type.kind(), 1),
                        msg.get("tower." + type.kind()), Integer.toString(type.cost()), game.gold() >= type.cost()));
            }
            building = cell;
            selected = null;
            open(msg.get("menu.build"), items, cell);
        }

        private void openTowerMenu(Tower tower) {
            TowerType type = tower.type();
            TowerType next = rules.upgrade(type);
            List<OverlayMenu.Item<Order>> items = new ArrayList<>();
            if (next != null) {
                items.add(new OverlayMenu.Item<>(new Upgrade(), painter.pictures().icon(type.kind(), next.stage()),
                        msg.get("menu.upgrade"), Integer.toString(next.cost()), game.gold() >= next.cost()));
            } else {
                items.add(new OverlayMenu.Item<>(new Upgrade(), painter.pictures().icon(type.kind(), type.stage()),
                        msg.get("menu.top"), "", false));
            }
            items.add(new OverlayMenu.Item<>(new Sell(), null, msg.get("menu.sell"), "+" + tower.refund(), true));
            selected = tower;
            building = null;
            open(msg.format("menu.tower", msg.get("tower." + type.kind()), stage(type.stage())), items, tower.cell());
        }

        private void open(String title, List<OverlayMenu.Item<Order>> items, Cell cell) {
            double x = camera.screenX(cell.col() * Field.TILE, 1);
            double y = camera.screenY(cell.row() * Field.TILE, 1);
            menu.open(title, items, x, y, Field.TILE, Field.TILE);
            effects.tick();
        }

        /** What a menu row does; nothing for {@code null}, a click beside the rows. */
        private void carryOut(Order order) {
            switch (order) {
                case null -> {
                    return;
                }
                case Build(TowerType type) -> {
                    Game.Placement result = game.build(building.col(), building.row(), type);
                    if (result != Game.Placement.OK) refuse(result);
                }
                case Upgrade upgrade -> {
                    if (!game.upgrade(selected)) refuse(Game.Placement.TOO_DEAR);
                }
                case Sell sell -> game.sell(selected);
            }
            closeMenu();
        }
        // --8<-- [end:menus]

        private void refuse(Game.Placement why) {
            effects.no();
            String key = switch (why) {
                case BLOCKS -> "place.blocks";
                case OCCUPIED -> "place.occupied";
                case TOO_DEAR -> "place.dear";
                default -> null;
            };
            if (key != null) {
                toast = msg.get(key);
                toastShown = 0;
            }
        }

        private void closeMenu() {
            menu.close();
            building = null;
            selected = null;
        }

        private void banner(String text) {
            banner = text;
            bannerShown = 0;
        }

        /** The marks on the map: the tile under the mouse and whether a tower may stand there, the menus' towers. */
        TowersPainter.Marks marks() {
            boolean free = hover != null && game.tower(hover.col(), hover.row()) == null
                    && game.placement(hover.col(), hover.row()) == Game.Placement.OK;
            if (hover != null && game.tower(hover.col(), hover.row()) != null) free = true;
            TowerType ghost = null;
            if (building != null && menu.highlighted() >= 0
                    && menu.items().get(menu.highlighted()).value() instanceof Build(TowerType type)) {
                ghost = type;
            }
            return new TowersPainter.Marks(menu.isOpen() ? null : hover, free, selected, building, ghost, flow);
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            painter.paint(g, game, terrain, camera, effects, marks(), alpha);
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.bar(g, game, fast, mouse.x(), mouse.y());
            if (menu.isOpen()) menu.render(g, menuStyle);
            hud.banner(g, banner, 120, 22, Hud.GOLD, bannerShown);
            hud.banner(g, toast, 360, 14, Hud.RED, toastShown);
            if (flow) hud.corner(g, msg.get("play.flow"));
        }

        Game game() {
            return game;
        }

        Camera camera() {
            return camera;
        }

        OverlayMenu<Order> menu() {
            return menu;
        }

        Terrain terrain() {
            return terrain;
        }

        boolean fast() {
            return fast;
        }

        Progress.Record record() {
            return record;
        }

        String toast() {
            return toastShown < Hud.BANNER_SECONDS ? toast : null;
        }
    }

    /** I, II, III. */
    static String stage(int stage) {
        return switch (stage) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> Integer.toString(stage);
        };
    }

    /** Drawn over the frozen battle until P, Enter or a click; Esc leaves it. */
    final class PauseScene implements Scene {
        @Override
        public void update(double seconds) {
            if (controls.wasPressed(Action.BACK)) scenes.replace(new ChoiceScene());
            else if (controls.wasPressed(Action.PAUSE) || controls.wasPressed(Action.OK)
                    || mouse.wasPressed(MouseEvent.BUTTON1)) {
                scenes.pop();
            }
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.veil(g);
            hud.text(g, msg.get("pause.title"), 190, 40, true, Hud.CHALK);
            hud.text(g, msg.get("pause.continue"), 235, 15, false, Hud.CHALK);
        }
    }

    /** The end of a battle: the battlefield stays behind the result; Enter goes on or tries again. */
    final class ResultScene implements Scene {
        private final PlayScene play;
        private double shown;

        ResultScene(PlayScene play) {
            this.play = play;
        }

        @Override
        public void update(double seconds) {
            effects.update(seconds);
            shown += seconds;
            if (shown < 0.6) return;     // a short wait, so a click meant for the battle does not skip the result
            if (controls.wasPressed(Action.BACK)) {
                scenes.replace(new ChoiceScene());
                return;
            }
            if (controls.wasPressed(Action.OK) || mouse.wasPressed(MouseEvent.BUTTON1)) {
                int number = play.game.level().number();
                boolean won = play.game.phase() == Game.Phase.WON;
                if (won && number == levels.size()) scenes.replace(new ChoiceScene());
                else scenes.replace(new PlayScene(levels.get(won ? number : number - 1)));
            }
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            boolean better = play.record != null && play.record.moreStars();
            hud.result(g, play.game, better, play.game.level().number() == levels.size());
        }
    }
}
