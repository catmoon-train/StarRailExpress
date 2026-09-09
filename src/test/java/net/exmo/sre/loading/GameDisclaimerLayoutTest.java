/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.exmo.sre.loading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GameDisclaimerLayoutTest {

    @Test
    void staysInsideScreenAtCommonWindowSizesAndGuiScales() {
        for (int[] window : new int[][] {{854, 480}, {1280, 720}, {1920, 1080}, {320, 240}}) {
            for (int scale = 1; scale <= 4; scale++) {
                int width = (int) Math.ceil(window[0] / (double) scale);
                int height = (int) Math.ceil(window[1] / (double) scale);
                if (width < 320 || height < 240) {
                    continue;
                }
                GameDisclaimerLayout l = GameDisclaimerLayout.of(width, height);
                assertTrue(l.panelX() >= 0 && l.panelY() >= 0, l.toString());
                assertTrue(l.panelX() + l.panelW() <= width, l.toString());
                assertTrue(l.panelY() + l.panelH() <= height, l.toString());
                assertTrue(l.contentX() >= l.panelX());
                assertTrue(l.contentY() >= l.panelY() + 8);
                assertTrue(l.contentY() + l.contentH() <= l.buttonY() - 12);
                assertTrue(l.buttonY() + l.buttonH() <= l.panelY() + l.panelH());
                assertTrue(l.sbX() + l.sbW() <= l.panelX() + l.panelW());
                assertTrue(l.inButton(l.buttonX() + 1, l.buttonY() + 1));
                assertFalse(l.inButton(l.buttonX() - 2, l.buttonY() + 1));
            }
        }
    }

    @Test
    void contentDefinesThreeDisclaimerSectionsWithDistinctColors() {
        var sections = GameDisclaimerContent.sections();
        assertEquals(3, sections.size());
        assertEquals(GameDisclaimerContent.HEALTH_TITLE, sections.get(0).titleKey());
        assertEquals(GameDisclaimerContent.EPILEPSY_TITLE, sections.get(1).titleKey());
        assertEquals(GameDisclaimerContent.OPENSOURCE_TITLE, sections.get(2).titleKey());
        assertEquals(GameDisclaimerContent.HEALTH_COLOR, sections.get(0).titleColor());
        assertEquals(GameDisclaimerContent.EPILEPSY_COLOR, sections.get(1).titleColor());
        assertEquals(GameDisclaimerContent.OPENSOURCE_COLOR, sections.get(2).titleColor());
        assertTrue(sections.get(0).titleColor() != sections.get(1).titleColor());
        assertTrue(sections.get(1).titleColor() != sections.get(2).titleColor());
    }

    @Test
    void languageFilesContainAllDisclaimerKeys() throws Exception {
        for (String lang : new String[] {"zh_cn", "zh_tw", "en_us"}) {
            Path path = Path.of("src/main/resources/assets/starrailexpress/lang/" + lang + ".json");
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            if (raw.startsWith("\uFEFF")) {
                raw = raw.substring(1);
            }
            JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
            for (String key : GameDisclaimerContent.allKeys()) {
                assertTrue(json.has(key), lang + " missing " + key);
                assertFalse(json.get(key).getAsString().isBlank(), lang + " blank " + key);
            }
            assertTrue(json.get(GameDisclaimerContent.HEALTH_TITLE).getAsString().contains("适度")
                    || json.get(GameDisclaimerContent.HEALTH_TITLE).getAsString().contains("適度")
                    || json.get(GameDisclaimerContent.HEALTH_TITLE).getAsString().contains("moderation"));
            assertTrue(json.get(GameDisclaimerContent.EPILEPSY_TITLE).getAsString().contains("癫痫")
                    || json.get(GameDisclaimerContent.EPILEPSY_TITLE).getAsString().contains("癲癇")
                    || json.get(GameDisclaimerContent.EPILEPSY_TITLE).getAsString().toLowerCase().contains("epilepsy"));
            assertTrue(json.get(GameDisclaimerContent.OPENSOURCE_BODY).getAsString().contains("LGPL-3.0"));
        }
    }
}
