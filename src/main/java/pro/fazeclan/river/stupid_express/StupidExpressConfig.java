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

package pro.fazeclan.river.stupid_express;

import io.wifi.ConfigCompact.ConfigClassHandler;
import io.wifi.ConfigCompact.annotation.ConfigSync;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.CollapsibleObject;

@Config(name = "stupid_express")
public class StupidExpressConfig implements ConfigData {
    public static ConfigClassHandler<StupidExpressConfig> HANDLER = new ConfigClassHandler<>(
            StupidExpressConfig.class);

    public static StupidExpressConfig getInstance() {
        return HANDLER.instance();
    }

    @CollapsibleObject
    @ConfigSync(shouldSync = true)
    public RolesSection rolesSection = new RolesSection();

    public static class RolesSection {
        @CollapsibleObject
        public ArsonistSection arsonistSection = new ArsonistSection();

        public static class ArsonistSection {
            public boolean arsonistKeepsGameGoing = true;
            // 点燃后，目标在死亡前持续燃烧的时间（秒）。期间目标身上着火，时间结束才死亡。
            public int burnDurationSeconds = 3;
        }

        @CollapsibleObject
        public AmnesiacSection amnesiacSection = new AmnesiacSection();

        public static class AmnesiacSection {
            public boolean amnesiacGlowsDifferently = false;
        }
    }

    @CollapsibleObject
    @ConfigSync(shouldSync = true)
    public ModifiersSection modifiersSection = new ModifiersSection();

    public static class ModifiersSection {
        @CollapsibleObject
        public LoversSection loversSection = new LoversSection();

        public static class LoversSection {
            public boolean loversKnowImmediately = true;
        }
    }
    
    public static StupidExpressConfig instance() {
        return HANDLER.instance();
    }
}
