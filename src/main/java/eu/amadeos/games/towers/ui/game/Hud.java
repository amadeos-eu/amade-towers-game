package eu.amadeos.games.towers.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.games.towers.domain.Field;
import eu.amadeos.games.towers.domain.Game;
import eu.amadeos.games.towers.repository.Progress;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * The overlay: the bar at the top with the gold, the lives, the wave and the two buttons, the messages over
 * the map, and the screens of the title, the choice, the pause and the end of a battle. The canvas draws
 * it after the world, at the full size of the screen, so the letters are sharp in every render mode.
 */
final class Hud {
    static final Color CHALK = new Color(0xF2E8D4);
    static final Color GOLD = new Color(0xFFE08A);
    static final Color GREEN = new Color(0xB8E08A);
    static final Color RED = new Color(0xFF6B5B);
    static final Color GREY = new Color(0x8A7A60);
    static final Color BRASS = new Color(0x8A6A3A);
    static final Color WOOD_TOP = new Color(0x2A2420);
    static final Color WOOD_BOTTOM = new Color(0x1A1612);
    static final Color BUTTON = new Color(0x4A3C28);
    static final Color BUTTON_HOVER = new Color(0x66543A);
    static final Color VEIL = new Color(0, 0, 0, 150);
    static final Color SHADOW = new Color(0, 0, 0, 140);
    static final double BANNER_SECONDS = 2.5;
    static final double CARD_WIDTH = 176;
    static final double CARD_HEIGHT = 150;
    static final double CARD_GAP = 16;
    static final double CARD_TOP = 110;

    /** The two buttons of the bar, in logical coordinates. */
    static final Rectangle2D.Double NEXT = new Rectangle2D.Double(Field.WIDTH - 184, 4, 136, 14);
    static final Rectangle2D.Double FAST = new Rectangle2D.Double(Field.WIDTH - 42, 4, 34, 14);

    private final Messages msg;
    private final Map<Integer, Font> fonts = new HashMap<>();
    private final Rectangle2D.Double rectangle = new Rectangle2D.Double();

    Hud(Messages msg) {
        this.msg = msg;
    }

    // --8<-- [start:bar]
    /** The bar at the top: gold, lives, the wave, and the buttons for the next wave and for double speed. */
    void bar(Graphics2D g, Game game, boolean fast, double mouseX, double mouseY) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, WOOD_TOP, 0, (float) Field.BAR, WOOD_BOTTOM));
        rectangle.setRect(0, 0, Field.WIDTH, Field.BAR);
        g.fill(rectangle);
        g.setColor(BRASS);
        rectangle.setRect(0, Field.BAR - 1, Field.WIDTH, 1);
        g.fill(rectangle);
        g.setFont(font(12, true));
        Art.coin(g, 10, 5, 12);
        g.setColor(CHALK);
        g.drawString(Integer.toString(game.gold()), 28, 15);
        Art.heart(g, 80, 6, 12);
        g.setColor(game.lives() <= game.level().plan().lives() / 4 ? RED : CHALK);
        g.drawString(Integer.toString(game.lives()), 98, 15);
        g.setColor(CHALK);
        centred(g, msg.format("hud.wave", game.wavesStarted(), game.waves()), Field.WIDTH / 2, 15);

        boolean more = game.wavesStarted() < game.waves() && game.phase() == Game.Phase.PLAYING;
        OptionalDouble countdown = game.countdown();
        String next = countdown.isPresent()
                ? msg.format("hud.next.in", (int) Math.ceil(countdown.getAsDouble()))
                : msg.get("hud.next");
        button(g, NEXT, next, more ? CHALK : GREY, more && NEXT.contains(mouseX, mouseY));
        button(g, FAST, "x2", fast ? GREEN : GREY, FAST.contains(mouseX, mouseY));
    }
    // --8<-- [end:bar]

    private void button(Graphics2D g, Rectangle2D.Double box, String label, Color colour, boolean hover) {
        g.setColor(hover ? BUTTON_HOVER : BUTTON);
        g.fill(new RoundRectangle2D.Double(box.x, box.y, box.width, box.height, 5, 5));
        g.setFont(font(10, true));
        g.setColor(colour);
        centred(g, label, box.getCenterX(), box.y + 10.5);
    }

    /**
     * A message in the middle of the map for a moment, fading out in its last half second: the level's
     * name, a new wave, why a tower cannot stand where it was meant to.
     */
    void banner(Graphics2D g, String line, double y, int size, Color colour, double shown) {
        double left = BANNER_SECONDS - shown;
        if (left <= 0) return;
        int alpha = (int) Math.round(255 * Math.clamp(left / 0.5, 0, 1));
        g.setFont(font(size, true));
        FontMetrics metrics = g.getFontMetrics();
        float x = (float) (Field.WIDTH - metrics.stringWidth(line)) / 2;
        g.setColor(new Color(0, 0, 0, alpha / 2));
        g.drawString(line, x + 1.5f, (float) y + 1.5f);
        g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha));
        g.drawString(line, x, (float) y);
    }

    /** The help line at the bottom, when the flow field shows. */
    void corner(Graphics2D g, String line) {
        g.setFont(font(9, true));
        g.setColor(SHADOW);
        g.drawString(line, 9, (float) Field.HEIGHT - 9);
        g.setColor(Color.WHITE);
        g.drawString(line, 8, (float) Field.HEIGHT - 10);
    }

    // --8<-- [start:card]
    /** One battlefield on the choice screen: its number and name, and the stars won there, or a lock. */
    void card(Graphics2D g, double x, double y, int number, boolean open, boolean chosen, Progress progress) {
        RoundRectangle2D.Double card = new RoundRectangle2D.Double(x, y, CARD_WIDTH, CARD_HEIGHT, 10, 10);
        g.setPaint(new GradientPaint((float) x, (float) y, chosen ? new Color(0x4A3C28) : WOOD_TOP, (float) x,
                (float) (y + CARD_HEIGHT), WOOD_BOTTOM));
        g.fill(card);
        g.setColor(chosen ? GOLD : BRASS);
        g.setStroke(new BasicStroke(chosen ? 1.6f : 1f));
        g.draw(card);
        double cx = x + CARD_WIDTH / 2;
        g.setFont(font(34, true));
        g.setColor(open ? GOLD : GREY);
        centred(g, Integer.toString(number), cx, y + 46);
        g.setFont(font(15, true));
        g.setColor(open ? CHALK : GREY);
        centred(g, name(number), cx, y + 72);
        g.setFont(font(11, false));
        if (!open) {
            lock(g, cx, y + 92);
            g.setColor(GREY);
            centred(g, msg.get("select.locked"), cx, y + 134);
            return;
        }
        int stars = progress.stars(number);
        for (int i = 0; i < 3; i++) Art.star(g, cx + (i - 1) * 26, y + 104, 10, i < stars);
        g.setColor(stars > 0 ? CHALK : GREY);
        centred(g, stars > 0 ? msg.format("select.stars", stars) : msg.get("select.none"), cx, y + 134);
    }
    // --8<-- [end:card]

    /** The left edge of card {@code index} of {@code count}. */
    static double cardX(int index, int count) {
        double left = (Field.WIDTH - count * CARD_WIDTH - (count - 1) * CARD_GAP) / 2;
        return left + index * (CARD_WIDTH + CARD_GAP);
    }

    private void lock(Graphics2D g, double cx, double y) {
        g.setColor(GREY);
        g.setStroke(new BasicStroke(3));
        g.draw(new RoundRectangle2D.Double(cx - 7, y - 2, 14, 16, 12, 12));
        rectangle.setRect(cx - 11, y + 7, 22, 16);
        g.fill(rectangle);
        g.setStroke(new BasicStroke(1));
    }

    /** The end of a battle: won with its stars, or lost. */
    void result(Graphics2D g, Game game, boolean better, boolean last) {
        veil(g);
        boolean won = game.phase() == Game.Phase.WON;
        text(g, msg.get(won ? "won.title" : "lost.title"), 130, 32, true, won ? GOLD : RED);
        if (won) {
            for (int i = 0; i < 3; i++) Art.star(g, Field.WIDTH / 2 + (i - 1) * 44, 180, 17, i < game.stars());
            if (better) text(g, msg.get("won.best"), 222, 13, true, GREEN);
        }
        text(g, msg.format("result.lives", game.lives(), game.level().plan().lives()), 252, 15, false, CHALK);
        String keys = won ? msg.get(last ? "won.last" : "won.next") : msg.get("lost.again");
        text(g, keys, 330, 13, false, CHALK);
    }

    /** The name of battlefield {@code number} in the player's language. */
    String name(int number) {
        return msg.get("level." + number);
    }

    static void centred(Graphics2D g, String text, double x, double y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (x - fm.stringWidth(text) / 2.0), (float) y);
    }

    /** Darkens the world so text over it stays readable. */
    void veil(Graphics2D g) {
        g.setColor(VEIL);
        rectangle.setRect(0, 0, Field.WIDTH, Field.HEIGHT);
        g.fill(rectangle);
    }

    /** A line of text centred horizontally, its baseline at {@code y}, with a shadow. */
    void text(Graphics2D g, String line, double y, int size, boolean bold, Color colour) {
        g.setFont(font(size, bold));
        float x = (float) (Field.WIDTH - g.getFontMetrics().stringWidth(line)) / 2;
        g.setColor(SHADOW);
        g.drawString(line, x + 1, (float) y + 1);
        g.setColor(colour);
        g.drawString(line, x, (float) y);
    }

    Font font(int size, boolean bold) {
        return fonts.computeIfAbsent(2 * size + (bold ? 1 : 0),
                key -> new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
    }
}
