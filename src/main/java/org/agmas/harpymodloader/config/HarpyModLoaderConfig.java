package org.agmas.harpymodloader.config;

import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.Tooltip;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.HashMap;

import io.wifi.ConfigCompact.ConfigClassHandler;

@Config(name = "harpymodloader")
public class HarpyModLoaderConfig implements ConfigData {
    public static final ConfigClassHandler<HarpyModLoaderConfig> HANDLER = new ConfigClassHandler<>(HarpyModLoaderConfig.class);

    // Disables roles from being in the role pool. use /listRoles to get role names,
    // use /setEnabledRole to ban/unban them in-game (saves here).
    public ArrayList<String> disabled = new ArrayList<>();

    // Which Modifiers should be disabled. Modifiers also show up in /listRoles and
    // /setEnabledModifier.
    public ArrayList<String> disabledModifiers = new ArrayList<>();

    // Maximum amount of modifiers a player can have.")
    public int modifierMaximum = 1;

    // How many modifiers should be given relative to the Player Count
    // (Multiplier)")
    @Tooltip
    public double modifierMultiplier = 0.5;

    // Custom weights for roles - maps role identifiers to their custom weight
    // values")
    public HashMap<ResourceLocation, Float> roleWeights = new HashMap<>();

    // Whether to use custom role weights instead of default round-based weights")
    public boolean useCustomRoleWeights = true;
}