package org.agmas.harpymodloader.config;

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.Harpymodloader;

import java.util.ArrayList;
import java.util.HashMap;

public class HarpyModLoaderConfig {
    public static ConfigClassHandler<HarpyModLoaderConfig> HANDLER = ConfigClassHandler.createBuilder(HarpyModLoaderConfig.class)
            .id(ResourceLocation.fromNamespaceAndPath(Harpymodloader.MOD_ID, "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve( Harpymodloader.MOD_ID + ".json5"))
                    .setJson5(true)
                    .build())
            .build();

    @SerialEntry(comment = "Disables roles from being in the role pool. use /listRoles to get role names, use /setEnabledRole to ban/unban them in-game (saves here).")
    public ArrayList<String> disabled = new ArrayList<>();

    @SerialEntry(comment = "Which Modifiers should be disabled. Modifiers also show up in /listRoles and /setEnabledModifier.")
    public ArrayList<String> disabledModifiers = new ArrayList<>();

    @SerialEntry(comment = "Maximum amount of modifiers a player can have.")
    public int modifierMaximum = 1;

    @SerialEntry(comment = "How many modifiers should be given relative to the Player Count (Multiplier)")
    public double modifierMultiplier = 0.5;

    @SerialEntry(comment = "Custom weights for roles - maps role identifiers to their custom weight values")
    public HashMap<ResourceLocation, Float> roleWeights = new HashMap<>();

    @SerialEntry(comment = "Whether to use custom role weights instead of default round-based weights")
    public boolean useCustomRoleWeights = true;

    @SerialEntry(comment = "Companion roles that appear together - maps a role to another role that should appear together")
    public HashMap<ResourceLocation, ResourceLocation> companionRoles = new HashMap<>();

}