package eu.amadeos.games.towers.repository;

import com.google.inject.Inject;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.gameengine.save.SaveFile;

import java.util.HashMap;
import java.util.Map;

/**
 * How far the player has come, in {@code ~/.local/share/amadeos/games/towers/progress.save}: the
 * battlefields that are open, and the most stars won on each. Written as a {@link SaveFile}, so a crash in
 * the middle of saving loses nothing.
 *
 * <pre>
 * amade-save 1
 * level.1.stars=3
 * open=2
 * crc32 5e1f0c2a
 * </pre>
 */
public final class Progress {
    static final int VERSION = 1;

    /** What a won battle brought: more stars than before, the next battlefield opened. */
    public record Record(boolean moreStars, boolean opened) {
    }

    private final SaveFile file;
    private final Map<String, String> values = new HashMap<>();

    @Inject
    public Progress(XdgDirectories xdg) {
        this(SaveFile.of(xdg, "towers", "progress"));
    }

    public Progress(SaveFile file) {
        this.file = file;
        file.load().ifPresent(saved -> values.putAll(saved.values()));
    }

    /** The highest battlefield that may be played; at least the first. */
    public int open() {
        return Math.max(1, number("open", 1));
    }

    /** The most stars won on a battlefield, 0 to 3. */
    public int stars(int level) {
        return Math.clamp(number("level." + level + ".stars", 0), 0, 3);
    }

    /** Enters a won battle, opens the next of {@code levels}, and saves at once. */
    public Record won(int level, int stars, int levels) {
        boolean more = stars > stars(level);
        boolean opened = level < levels && open() <= level;
        if (more) values.put("level." + level + ".stars", Integer.toString(stars));
        if (opened) values.put("open", Integer.toString(level + 1));
        if (more || opened) file.store(VERSION, values);
        return new Record(more, opened);
    }

    private int number(String key, int fallback) {
        try {
            return Integer.parseInt(values.getOrDefault(key, Integer.toString(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
