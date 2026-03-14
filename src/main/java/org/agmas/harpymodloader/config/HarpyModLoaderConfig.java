package org.agmas.harpymodloader.config;

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.Boolean;
import dev.isxander.yacl3.config.v2.api.autogen.DoubleField;
import dev.isxander.yacl3.config.v2.api.autogen.IntField;
import dev.isxander.yacl3.config.v2.api.autogen.Label;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.Harpymodloader;

import java.util.ArrayList;
import java.util.HashMap;

public class HarpyModLoaderConfig {
    public static ConfigClassHandler<HarpyModLoaderConfig> HANDLER = ConfigClassHandler
            .createBuilder(HarpyModLoaderConfig.class)
            .id(ResourceLocation.fromNamespaceAndPath(Harpymodloader.MOD_ID, "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir()
                            .resolve(Harpymodloader.MOD_ID + ".json5"))
                    .setJson5(true)
                    .build())
            .build();

    @SerialEntry(comment = "Disables roles from being in the role pool. use /listRoles to get role names, use /setEnabledRole to ban/unban them in-game (saves here).")
    @Label
    public ArrayList<String> disabled = new ArrayList<>();

    @SerialEntry(comment = "Which Modifiers should be disabled. Modifiers also show up in /listRoles and /setEnabledModifier.")
    @Label
    public ArrayList<String> disabledModifiers = new ArrayList<>();

    @SerialEntry(comment = "Maximum amount of modifiers a player can have.")
    @AutoGen(category = "General")
    @IntField
    public int modifierMaximum = 1;

    @SerialEntry(comment = "How many modifiers should be given relative to the Player Count (Multiplier)")
    @AutoGen(category = "General")
    @DoubleField
    public double modifierMultiplier = 0.5;

    @SerialEntry(comment = "Custom weights for roles - maps role identifiers to their custom weight values")
    @Label
    public HashMap<ResourceLocation, Float> roleWeights = new HashMap<>();

    @SerialEntry(comment = "Whether to use custom role weights instead of default round-based weights")
    @AutoGen(category = "General")
    @Boolean
    public boolean useCustomRoleWeights = true;

    @SerialEntry(comment = "Companion roles that appear together - maps a role to another role that should appear together")
    @Label
    public HashMap<ResourceLocation, ResourceLocation> companionRoles = new HashMap<>();

}