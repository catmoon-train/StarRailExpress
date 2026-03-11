package org.agmas.noellesroles.config;

import io.wifi.starrailexpress.game.GameConstants;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
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

    @SerialEntry(comment = "Whether insane players will randomly see people as morphed.")
    public boolean insanePlayersSeeMorphs = true;
    @SerialEntry(comment = "Allows the shitpost roles to retain their disable/enable state after a server restart")
    public boolean shitpostRoles = false;

    @SerialEntry(comment = "Areas that will spawn Swast. Use | to split maps.")
    public String swastMaps = "areas1|areas3|areas4|areas7|areas10";

    @SerialEntry(comment = "Role - The chance of egg roles")
    public int chanceOfEggRoles = 15;
    
    @SerialEntry(comment = "Modifier - The chance of Refugee")
    public int chanceOfModifierRefugee = 10;

    @SerialEntry(comment = "Modifier - The chance of Split Personality")
    public int chanceOfModifierSplitPersonality = 10;

    @SerialEntry(comment = "Starting cooldown (in ticks)")
    public int generalCooldownTicks = GameConstants.getInTicks(0, 30);

    @SerialEntry(comment = "Enable client blood render")
    public boolean enableClientBlood = true;

    @SerialEntry(comment = "Punishment for a civilian's accidental killing of another civilian")
    public boolean accidentalKillPunishment = true;

    @SerialEntry(comment = "Allow Natural deaths to trigger voodoo (deaths without an assigned killer)")
    public boolean voodooNonKillerDeaths = false;

    @SerialEntry(comment = "Makes voodoos act like Evil players when shot by a revolver (no backfire, no gun lost)")
    public boolean voodooShotLikeEvil = true;

    @SerialEntry(comment = "How many players must be online for the Master Key to look like a master key and not a lockpick. (0 = key always looks like a lockpick, 1-6 = key always looks normal)")
    public int playerCountToMakeConducterKeyVisible = 10;

    @SerialEntry(comment = "Maximum number of Conductors allowed")
    public int conductorMax = 1;
    @SerialEntry(comment = "Maximum number of Executioners allowed")
    public int executionerMax = 1;
    @SerialEntry(comment = "Maximum number of Vultures allowed")
    public int vultureMax = 1;
    @SerialEntry(comment = "Maximum number of Jesters allowed")
    public int jesterMax = 1;
    @SerialEntry(comment = "Maximum number of Morphlings allowed")
    public int morphlingMax = 1;
    @SerialEntry(comment = "Maximum number of Bartenders allowed")
    public int bartenderMax = 1;
    @SerialEntry(comment = "Maximum number of Noisemakers allowed")
    public int noisemakerMax = 1;
    @SerialEntry(comment = "Maximum number of Phantoms allowed")
    public int phantomMax = 1;
    @SerialEntry(comment = "Maximum number of Awesome Bingluses allowed")
    public int awesomeBinglusMax = 1;
    @SerialEntry(comment = "Maximum number of Swappers allowed")
    public int swapperMax = 1;
    @SerialEntry(comment = "Maximum number of Voodoos allowed")
    public int voodooMax = 1;
    @SerialEntry(comment = "Maximum number of Coroners allowed")
    public int coronerMax = 1;
    @SerialEntry(comment = "Maximum number of Recallers allowed")
    public int recallerMax = 1;
    @SerialEntry(comment = "Maximum number of Broadcasters allowed")
    public int broadcasterMax = 1;
    @SerialEntry(comment = "Maximum number of Gamblers allowed")
    public int gamblerMax = 1;
    @SerialEntry(comment = "Maximum number of Glitch Robots allowed")
    public int glitchRobotMax = 1;
    @SerialEntry(comment = "Maximum number of Ghosts allowed")
    public int ghostMax = 1;
    @SerialEntry(comment = "Maximum number of Thieves allowed")
    public int thiefMax = 1;
    @SerialEntry(comment = "Maximum number of Sheriffs allowed")
    public int sheriffMax = 1;

    @SerialEntry(comment = "Whether Executioners can manually select their targets. If disabled, targets will be assigned randomly.")
    public boolean executionerCanSelectTarget = false;

    // Skills configuration
    @SerialEntry(comment = "Broadcaster - Broadcast message display duration in seconds")
    public int broadcasterMessageDuration = 10;

    @SerialEntry(comment = "Morphling - Morph duration in seconds")
    public int morphlingMorphDuration = 35;
    @SerialEntry(comment = "Morphling - Morph cooldown in seconds")
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
    public int recallerMarkCooldown = 10;

    @SerialEntry(comment = "Recaller - Teleport cooldown in seconds")
    public int recallerTeleportCooldown = 30;

    @SerialEntry(comment = "Phantom - Invisibility duration in seconds")
    public int phantomInvisibilityDuration = 30;

    @SerialEntry(comment = "Phantom - Invisibility cooldown in seconds")
    public int phantomInvisibilityCooldown = 90;

    @SerialEntry(comment = "Voodoo - Voodoo ritual cooldown in seconds")
    public int voodooCooldown = 30;

    @SerialEntry(comment = "Vulture - Eat body cooldown in seconds")
    public int vultureEatCooldown = 20;

    @SerialEntry(comment = "Executioner - Knife cooldown in seconds")
    public int executionerKnifeCooldown = 10;

    @SerialEntry(comment = "Swapper - Swap cooldown in seconds")
    public int swapperSwapCooldown = 60;

    @SerialEntry(comment = "Thief - Steal cooldown in seconds")
    public int thiefStealCooldown = 60;

    @SerialEntry(comment = "Thief - Blackout invisibility duration in seconds")
    public int thiefBlackoutDuration = 20;

    @SerialEntry(comment = "Thief - Blackout cooldown in seconds (time before can steal again after using blackout)")
    public int thiefBlackoutCooldown = 30;

    @SerialEntry(comment = "Manipulator - Control target cooldown in seconds")
    public int manipulatorCooldown = 60;

    @SerialEntry(comment = "(Client Side) Welcome Voice - Play welcome voice")
    public boolean welcome_voice = false;

    @SerialEntry(comment = "Credit info - If you wish to use this mod on your server you must change it.")
    public String credit = "";
}