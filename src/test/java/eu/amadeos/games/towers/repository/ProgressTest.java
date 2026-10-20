package eu.amadeos.games.towers.repository;

import eu.amadeos.gameengine.save.SaveFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressTest {
    @TempDir
    Path dir;

    @Test
    void aWonBattleOpensTheNextAndKeepsTheMostStars() {
        SaveFile file = new SaveFile(dir.resolve("progress.save"));
        Progress progress = new Progress(file);
        assertEquals(1, progress.open());
        assertEquals(0, progress.stars(1));

        Progress.Record first = progress.won(1, 2, 3);
        assertTrue(first.moreStars() && first.opened());

        Progress later = new Progress(file);             // the next start of the game
        assertEquals(2, later.open());
        assertEquals(2, later.stars(1));
        Progress.Record worse = later.won(1, 1, 3);
        assertFalse(worse.moreStars() || worse.opened(), "one star is no record, and level 2 is open");
        assertTrue(later.won(1, 3, 3).moreStars());
        assertEquals(3, new Progress(file).stars(1));
    }

    @Test
    void theLastBattlefieldOpensNothing() {
        Progress progress = new Progress(new SaveFile(dir.resolve("progress.save")));
        progress.won(1, 1, 3);
        progress.won(2, 1, 3);
        assertFalse(progress.won(3, 1, 3).opened());
        assertEquals(3, progress.open());
    }
}
