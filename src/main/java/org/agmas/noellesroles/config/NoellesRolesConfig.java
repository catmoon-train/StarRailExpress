package org.agmas.noellesroles.config;

import io.wifi.starrailexpress.game.GameConstants;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.Boolean;
import dev.isxander.yacl3.config.v2.api.autogen.IntField;
import dev.isxander.yacl3.config.v2.api.autogen.StringField;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public class NoellesRolesConfig {
    public static ConfigClassHandler<NoellesRolesConfig> HANDLER = ConfigClassHandler
            .createBuilder(NoellesRolesConfig.class)
            .id(ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir()
                            .resolve(Noellesroles.MOD_ID + ".json5"))
                    .setJson5(true)
                    .build())
            .build();
    @AutoGen(category = "General")
    @SerialEntry(comment = "Whether insane players will randomly see people as morphed.")
    @Boolean
    public boolean insanePlayersSeeMorphs = true;
    @SerialEntry(comment = "Allows the shitpost roles to retain their disable/enable state after a server restart")
    @AutoGen(category = "General")
    @Boolean
    public boolean shitpostRoles = false;

    
    @SerialEntry(comment = "Areas that will spawn Swast. Use | to split maps.")
    @AutoGen(category = "General")
    @StringField
    public String maChenXuMaps = "areas1";

    @SerialEntry(comment = "Areas that will spawn Swast. Use | to split maps.")
    @AutoGen(category = "General")
    @StringField
    public String swastMaps = "areas1|areas3|areas4|areas7|areas10";

    @SerialEntry(comment = "Role - The chance of egg roles")
    @AutoGen(category = "General")
    @IntField
    public int chanceOfEggRoles = 15;

    @SerialEntry(comment = "Modifier - The chance of Refugee")
    @AutoGen(category = "General")
    @IntField
    public int chanceOfModifierRefugee = 10;

    @SerialEntry(comment = "Modifier - The chance of Split Personality")
    @AutoGen(category = "General")
    @IntField
    public int chanceOfModifierSplitPersonality = 10;

    @SerialEntry(comment = "Starting cooldown (in ticks)")
    @AutoGen(category = "General")
    @IntField
    public int generalCooldownTicks = GameConstants.getInTicks(0, 30);

    @SerialEntry(comment = "Enable client blood render")
    @Boolean
    @AutoGen(category = "General")
    public boolean enableClientBlood = true;

    @SerialEntry(comment = "Punishment for a civilian's accidental killing of another civilian")
    @Boolean
    @AutoGen(category = "General")
    public boolean accidentalKillPunishment = true;

    @SerialEntry(comment = "Allow Natural deaths to trigger voodoo (deaths without an assigned killer)")
    @Boolean
    @AutoGen(category = "General")
    public boolean voodooNonKillerDeaths = false;

    @SerialEntry(comment = "Makes voodoos act like Evil players when shot by a revolver (no backfire, no gun lost)")
    @Boolean
    @AutoGen(category = "General")
    public boolean voodooShotLikeEvil = true;

    @SerialEntry(comment = "How many players must be online for the Master Key to look like a master key and not a lockpick. (0 = key always looks like a lockpick, 1-6 = key always looks normal)")
    @IntField
    @AutoGen(category = "General")
    public int playerCountToMakeConducterKeyVisible = 10;

    @SerialEntry(comment = "Maximum number of Conductors allowed")
    @IntField
    @AutoGen(category = "General")
    public int conductorMax = 1;
    @SerialEntry(comment = "Maximum number of Executioners allowed")
    @IntField
    @AutoGen(category = "General")
    public int executionerMax = 1;
    @SerialEntry(comment = "Maximum number of Vultures allowed")
    @IntField
    @AutoGen(category = "General")
    public int vultureMax = 1;
    @SerialEntry(comment = "Maximum number of Jesters allowed")
    @IntField
    @AutoGen(category = "General")
    public int jesterMax = 1;
    @SerialEntry(comment = "Maximum number of Morphlings allowed")
    @IntField
    @AutoGen(category = "General")
    public int morphlingMax = 1;
    @SerialEntry(comment = "Maximum number of Bartenders allowed")
    @IntField
    @AutoGen(category = "General")
    public int bartenderMax = 1;
    @SerialEntry(comment = "Maximum number of Noisemakers allowed")
    @IntField
    @AutoGen(category = "General")
    public int noisemakerMax = 1;
    @SerialEntry(comment = "Maximum number of Phantoms allowed")
    @IntField
    @AutoGen(category = "General")
    public int phantomMax = 1;
    @SerialEntry(comment = "Maximum number of Awesome Bingluses allowed")
    @IntField
    @AutoGen(category = "General")
    public int awesomeBinglusMax = 1;
    @SerialEntry(comment = "Maximum number of Swappers allowed")
    @IntField
    @AutoGen(category = "General")
    public int swapperMax = 1;
    @SerialEntry(comment = "Maximum number of Voodoos allowed")
    @IntField
    @AutoGen(category = "General")
    public int voodooMax = 1;
    @SerialEntry(comment = "Maximum number of Coroners allowed")
    @IntField
    @AutoGen(category = "General")
    public int coronerMax = 1;
    @SerialEntry(comment = "Maximum number of Recallers allowed")
    @IntField
    @AutoGen(category = "General")
    public int recallerMax = 1;
    @SerialEntry(comment = "Maximum number of Broadcasters allowed")
    @IntField
    @AutoGen(category = "General")
    public int broadcasterMax = 1;
    @SerialEntry(comment = "Maximum number of Gamblers allowed")
    @IntField
    @AutoGen(category = "General")
    public int gamblerMax = 1;
    @SerialEntry(comment = "Maximum number of Glitch Robots allowed")
    @IntField
    @AutoGen(category = "General")
    public int glitchRobotMax = 1;
    @SerialEntry(comment = "Maximum number of Ghosts allowed")
    @IntField
    @AutoGen(category = "General")
    public int ghostMax = 1;
    @SerialEntry(comment = "Maximum number of Thieves allowed")
    @IntField
    @AutoGen(category = "General")
    public int thiefMax = 1;
    @SerialEntry(comment = "Maximum number of Sheriffs allowed")
    @IntField
    @AutoGen(category = "General")
    public int sheriffMax = 1;

    @SerialEntry(comment = "Whether Executioners can manually select their targets. If disabled, targets will be assigned randomly.")
    @Boolean
    @AutoGen(category = "General")
    public boolean executionerCanSelectTarget = false;

    // Skills configuration
    @SerialEntry(comment = "Broadcaster - Broadcast message display duration in seconds")
    @IntField
    @AutoGen(category = "General")
    public int broadcasterMessageDuration = 10;

    @SerialEntry(comment = "Morphling - Morph duration in seconds")
    @IntField
    @AutoGen(category = "General")
    public int morphlingMorphDuration = 35;
    @SerialEntry(comment = "Morphling - Morph cooldown in seconds")
    @IntField
    @AutoGen(category = "General")
    public int morphlingMorphCooldown = 20;

    // @SerialEntry(comment = "Recaller - Maximum recall distance in blocks")
    // public int recallerMaxDistance = 50;
    //
    // @SerialEntry(comment = "Vulture - Bodies required to win")
    // public int vultureBodiesRequired = 2;
    //
    // @SerialEntry(comment = "Jester - Time to complete jest in seconds")
    // public int jesterJestTime = 60;
    //
    // @SerialEntry(comment = "Jester - Maximum psycho ticks before death")
    // public int jesterMaxPsychoTicks = 44;

    @SerialEntry(comment = "Recaller - Recall mark cooldown in seconds")
    @IntField
    @AutoGen(category = "General")
    public int recallerMarkCooldown = 10;

    @SerialEntry(comment = "Recaller - Teleport cooldown in seconds")
    @IntField
    @AutoGen(category = "General")
    public int recallerTeleportCooldown = 30;

    @SerialEntry(comment = "Phantom - Invisibility duration in seconds")
    @IntField
    @AutoGen(category = "General")
    public int phantomInvisibilityDuration = 30;

    @SerialEntry(comment = "Phantom - Invisibility cooldown in seconds")
    @IntField
    @AutoGen(category = "General")
    public int phantomInvisibilityCooldown = 90;

    @SerialEntry(comment = "Voodoo - Voodoo ritual cooldown in seconds")
    @IntField
    @AutoGen(category = "General")
    public int voodooCooldown = 30;

    @SerialEntry(comment = "Vulture - Eat body cooldown in seconds")
    @IntField
    @AutoGen(category = "General")
    public int vultureEatCooldown = 20;

    @SerialEntry(comment = "Executioner - Knife cooldown in seconds")
    @IntField
    @AutoGen(category = "General")
    public int executionerKnifeCooldown = 10;

    @SerialEntry(comment = "Swapper - Swap cooldown in seconds")
    @IntField
    @AutoGen(category = "General")
    public int swapperSwapCooldown = 60;

    @SerialEntry(comment = "Thief - Steal cooldown in seconds")
    @IntField
    @AutoGen(category = "General")
    public int thiefStealCooldown = 60;

    @SerialEntry(comment = "Thief - Blackout invisibility duration in seconds")
    @IntField
    @AutoGen(category = "General")
    public int thiefBlackoutDuration = 20;

    @SerialEntry(comment = "Thief - Blackout cooldown in seconds (time before can steal again after using blackout)")
    @IntField
    @AutoGen(category = "General")
    public int thiefBlackoutCooldown = 30;

    @SerialEntry(comment = "Manipulator - Control target cooldown in seconds")
    @IntField
    @AutoGen(category = "General")
    public int manipulatorCooldown = 60;

    @SerialEntry(comment = "(Client Side) Welcome Voice - Play welcome voice")
    @Boolean
    @AutoGen(category = "General")
    public boolean welcome_voice = false;

    @SerialEntry(comment = "Credit info - If you wish to use this mod on your server you must change it.")
    @StringField
    @AutoGen(category = "General")
    public String credit = "";
}