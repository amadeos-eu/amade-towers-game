package eu.amadeos.games.towers.repository;

import com.google.inject.Inject;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.gameengine.swing.RenderMode;
import eu.amadeos.games.towers.domain.Mode;

import javax.swing.KeyStroke;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * The settings in {@code ~/.config/amadeos/towers/towers.properties}: the graphics mode, the sound, whether
 * battles are fought on the road or on open ground, and the keys. Keys are written as Java names them, several separated by spaces, e.g. {@code keys.next=N SPACE}.
 */
public final class TowersSettings {
    private static final String GRAPHICS = "graphics";
    private static final String SOUND = "sound";
    private static final String BATTLEFIELD = "battlefield";

    private final Path file;

    @Inject
    public TowersSettings(AppDirectories directories) {
        this(directories.configFile("towers.properties"));
    }

    public TowersSettings(Path file) {
        this.file = file;
    }

    public RenderMode renderMode() {
        return RenderMode.ofId(PropertiesFile.load(file).getProperty(GRAPHICS, "")).orElse(RenderMode.DIRECT);
    }

    public void setRenderMode(RenderMode mode) {
        set(GRAPHICS, mode.id());
    }

    public boolean sound() {
        return !"off".equals(PropertiesFile.load(file).getProperty(SOUND, "on"));
    }

    public void setSound(boolean on) {
        set(SOUND, on ? "on" : "off");
    }

    /** On the road unless the file says {@code battlefield=open}. */
    public Mode mode() {
        return Mode.ofId(PropertiesFile.load(file).getProperty(BATTLEFIELD, ""));
    }

    public void setMode(Mode mode) {
        set(BATTLEFIELD, mode.id());
    }

    private void set(String key, String value) {
        Properties properties = PropertiesFile.load(file);
        properties.setProperty(key, value);
        PropertiesFile.store(file, properties, "amade-towers-game settings");
    }

    /**
     * The keys of an action, e.g. {@code keys("next", KeyEvent.VK_N)}: those in the file if it names any it
     * understands, else the defaults.
     */
    public int[] keys(String action, int... defaults) {
        String names = PropertiesFile.load(file).getProperty("keys." + action, "").strip();
        List<Integer> codes = new ArrayList<>();
        for (String name : names.split("\\s+")) {
            if (name.isEmpty()) continue;
            KeyStroke stroke = KeyStroke.getKeyStroke(name.toUpperCase(Locale.ROOT));
            if (stroke != null && stroke.getKeyCode() != 0) codes.add(stroke.getKeyCode());
        }
        return codes.isEmpty() ? defaults : codes.stream().mapToInt(Integer::intValue).toArray();
    }
}
