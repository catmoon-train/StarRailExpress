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

package org.agmas.harpymodloader.config;

import io.wifi.ConfigCompact.ConfigClassHandler;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.Tooltip;

import java.util.ArrayList;
import java.util.HashMap;

@Config(name = "harpymodloader")
public class HarpyModLoaderConfig implements ConfigData {
    public static final ConfigClassHandler<HarpyModLoaderConfig> HANDLER = new ConfigClassHandler<>(
            HarpyModLoaderConfig.class);
    // Disables roles from being in the role pool. use /listRoles to get role names,
    // use /setEnabledRole to ban/unban them in-game (saves here).
    public ArrayList<String> disabled = new ArrayList<>();

    // Which Modifiers should be disabled. Modifiers also show up in /listRoles and
    // /setEnabledModifier.
    public ArrayList<String> disabledModifiers = new ArrayList<>();

    // Maximum amount of modifiers a player can have.")
    public int modifierMaximum = 4;

    // How many modifiers should be given relative to the Player Count
    // (Multiplier)")
    @Tooltip
    public double modifierMultiplier = 0.5;

    // Custom weights for roles - maps role identifiers to their custom weight
    // values")
    public HashMap<String, Float> roleWeights = new HashMap<>();

    // Whether to use custom role weights instead of default round-based weights")
    public boolean useCustomRoleWeights = true;

    // Minimum player count required to spawn neutral roles
    public int neutralMinPlayerCount = 10;

    // Enable sre:civilian to be included in normal role assignment pool (max 1)
    public boolean enableCivilianInPool = false;

    public ArrayList<String> getDisabled() {
        if (disabled != null)
            return new ArrayList<>(disabled);
        return new ArrayList<>();
    }

    public void setDisabled(ArrayList<String> disabled) {
        this.disabled = disabled;
    }

    public ArrayList<String> getDisabledModifiers() {
        if (disabledModifiers != null)
            return new ArrayList<>(disabledModifiers);
        return new ArrayList<>();
    }
    
    public static HarpyModLoaderConfig instance() {
        return HANDLER.instance();
    }
}
