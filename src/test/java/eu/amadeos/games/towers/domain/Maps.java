package eu.amadeos.games.towers.domain;

import eu.amadeos.gameengine.data.TextData;
import eu.amadeos.gameengine.tiled.TiledMap;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

/**
 * Small maps for tests, drawn as text and turned into the TMX that Tiled would save: {@code .} grass,
 * {@code T} a tree, {@code P} a pine, {@code R} a rock, {@code ~} water, {@code L} a lair, {@code C} the
 * castle (its tiles, as one box). The plan is given as the lines of a level in {@code waves.txt}.
 */
final class Maps {
    /** The rules of the game, with the units and towers of its data files and the waves given here. */
    static Rules rules(String waves) {
        return Rules.read(TextData.load(Rules.class, Rules.FOLDER + "units.txt"),
                TextData.load(Rules.class, Rules.FOLDER + "towers.txt"),
                TextData.parse("waves.txt", new StringReader(waves)));
    }

    static Level of(Rules rules, String... rows) {
        int width = rows[0].length();
        int height = rows.length;
        StringJoiner data = new StringJoiner(",");
        StringBuilder objects = new StringBuilder();
        int id = 1;
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = -1, bottom = -1;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                char c = rows[row].charAt(col);
                data.add(switch (c) {
                    case 'T' -> "2";
                    case 'P' -> "3";
                    case 'R' -> "4";
                    case '~' -> "5";
                    default -> "1";
                });
                if (c == 'L') {
                    objects.append("<object id=\"%d\" type=\"lair\" x=\"%d\" y=\"%d\" width=\"20\" height=\"20\"/>\n"
                            .formatted(id++, col * 20, row * 20));
                }
                if (c == 'C') {
                    left = Math.min(left, col);
                    top = Math.min(top, row);
                    right = Math.max(right, col);
                    bottom = Math.max(bottom, row);
                }
            }
        }
        objects.append("<object id=\"%d\" type=\"castle\" x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"/>\n"
                .formatted(id, left * 20, top * 20, (right - left + 1) * 20, (bottom - top + 1) * 20));
        String tmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <map orientation="orthogonal" width="%d" height="%d" tilewidth="20" tileheight="20">
                 <tileset firstgid="1" name="test" tilewidth="20" tileheight="20" tilecount="5" columns="5">
                  <tile id="1" type="tree"/><tile id="2" type="pine"/><tile id="3" type="rock"/><tile id="4" type="water"/>
                 </tileset>
                 <layer name="ground" width="%d" height="%d"><data encoding="csv">%s</data></layer>
                 <objectgroup name="things">
                %s </objectgroup>
                </map>
                """.formatted(width, height, width, height, data, objects);
        TiledMap map = TiledMap.parse("test.tmx", new ByteArrayInputStream(tmx.getBytes(StandardCharsets.UTF_8)),
                file -> null);
        return Level.read(1, map, rules.plan(1));
    }
}
