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
                assertTrue(l.contentH() >= 40, l.toString());
                assertTrue(l.contentY() + l.contentH() <= l.panelY() + l.panelH(), l.toString());
                assertTrue(l.buttonX() >= l.contentX(), l.toString());
                assertTrue(l.buttonX() + l.buttonW() <= l.contentX() + l.contentW(), l.toString());
                assertTrue(l.sbX() + l.sbW() <= l.panelX() + l.panelW());
            }
        }
    }

    @Test
    void confirmButtonOnlyReachableWhenScrolledToBottom() {
        assertTrue(GameDisclaimerLayout.isButtonReachable(0, 0), "内容不足一屏时可直接点击");
        assertFalse(GameDisclaimerLayout.isButtonReachable(0, 100), "未滚动时不可点击");
        assertFalse(GameDisclaimerLayout.isButtonReachable(99, 100), "未滚到底时不可点击");
        assertTrue(GameDisclaimerLayout.isButtonReachable(100, 100), "滚到底后可点击");
    }

    @Test
    void contentDefinesFourDisclaimerSectionsWithDistinctColors() {
        var sections = GameDisclaimerContent.sections();
        assertEquals(4, sections.size());
        assertEquals(GameDisclaimerContent.HEALTH_TITLE, sections.get(0).titleKey());
        assertEquals(GameDisclaimerContent.EPILEPSY_TITLE, sections.get(1).titleKey());
        assertEquals(GameDisclaimerContent.OPENSOURCE_TITLE, sections.get(2).titleKey());
        assertEquals(GameDisclaimerContent.FANWORK_TITLE, sections.get(3).titleKey());
        assertEquals(GameDisclaimerContent.HEALTH_COLOR, sections.get(0).titleColor());
        assertEquals(GameDisclaimerContent.EPILEPSY_COLOR, sections.get(1).titleColor());
        assertEquals(GameDisclaimerContent.OPENSOURCE_COLOR, sections.get(2).titleColor());
        assertEquals(GameDisclaimerContent.FANWORK_COLOR, sections.get(3).titleColor());
        assertEquals(4, sections.stream().map(GameDisclaimerContent.Section::titleColor).distinct().count());
    }

    @Test
    void wrappedLinesStayCenteredOnThePanelAxis() {
        assertEquals(100, GameDisclaimerContent.centerX(120, 40));
        assertEquals(0, GameDisclaimerContent.centerX(10, 20));
        int axis = 350;
        for (int textW : new int[] {8, 41, 120, 333}) {
            int x = GameDisclaimerContent.centerX(axis, textW);
            assertEquals(axis, x + textW / 2);
        }
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
            assertTrue(json.get(GameDisclaimerContent.FANWORK_TITLE).getAsString().contains("二创")
                    || json.get(GameDisclaimerContent.FANWORK_TITLE).getAsString().contains("二創")
                    || json.get(GameDisclaimerContent.FANWORK_TITLE).getAsString().toLowerCase().contains("fan"));
            assertTrue(json.get(GameDisclaimerContent.FANWORK_BODY).getAsString().contains("非商业")
                    || json.get(GameDisclaimerContent.FANWORK_BODY).getAsString().contains("非商業")
                    || json.get(GameDisclaimerContent.FANWORK_BODY).getAsString().contains("non-commercial"));
        }
    }
}
