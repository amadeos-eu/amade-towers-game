package eu.amadeos.games.towers.repository;

import eu.amadeos.gameengine.swing.RenderMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowersSettingsTest {
    @TempDir
    Path dir;

    @Test
    void settingsAreStoredAndReadBack() {
        TowersSettings settings = new TowersSettings(dir.resolve("towers.properties"));
        assertEquals(RenderMode.DIRECT, settings.renderMode());
        assertTrue(settings.sound());
        settings.setRenderMode(RenderMode.PIXEL);
        settings.setSound(false);
        TowersSettings again = new TowersSettings(dir.resolve("towers.properties"));
        assertEquals(RenderMode.PIXEL, again.renderMode());
        assertFalse(again.sound());
    }

    @Test
    void keysComeFromTheFileOrTheDefaults() throws Exception {
        Path file = dir.resolve("towers.properties");
        Files.writeString(file, "keys.next=X SPACE\nkeys.fast=NOT_A_KEY\n");
        TowersSettings settings = new TowersSettings(file);
        assertArrayEquals(new int[]{KeyEvent.VK_X, KeyEvent.VK_SPACE}, settings.keys("next", KeyEvent.VK_N));
        assertArrayEquals(new int[]{KeyEvent.VK_F}, settings.keys("fast", KeyEvent.VK_F));
    }
}
