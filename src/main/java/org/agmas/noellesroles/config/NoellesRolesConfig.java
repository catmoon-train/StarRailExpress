package org.agmas.noellesroles.config;

import java.util.ArrayList;
import java.util.List;

import io.wifi.ConfigCompact.ConfigClassHandler;
import io.wifi.starrailexpress.game.GameConstants;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Category;

@Config(name = "noellesroles")
public class NoellesRolesConfig implements ConfigData {
    public static ConfigClassHandler<NoellesRolesConfig> HANDLER = new ConfigClassHandler<>(
            NoellesRolesConfig.class);

    /**
     * Whether insane players will randomly see people as morphed
     */

    public boolean insanePlayersSeeMorphs = true;

    /**
     * Areas that will spawn Swast. Use | to split maps
     */

    public ArrayList<String> maChenXuMaps = new ArrayList<>(List.of("areas_qiyucun"));

    /**
     * Areas that will spawn Swast. Use | to split maps
     */

    public ArrayList<String> swastMaps = new ArrayList<>(List.of("areas1", "areas3", "areas4", "areas7", "areas10"));

    /**
     * Areas that will spawn underwater roles (Sea King, Diver, Water Ghost)
     */
    public ArrayList<String> underwaterRolesMaps = new ArrayList<>(List.of("areas14"));

    /**
     * Role - The chance of egg roles
     */

    public int chanceOfEggRoles = 15;

    /**
     * Modifier - The chance of Lovers
     */

    public int chanceOfModifierLovers = 10;
    /**
     * Modifier - The chance of Refugee
     */

    public int chanceOfModifierRefugee = 10;

    /**
     * Modifier - The chance of Split Personality
     */

    public int chanceOfModifierSplitPersonality = 10;

    /**
     * Starting cooldown (in ticks)
     */

    public int generalCooldownTicks = GameConstants.getInTicks(0, 30);

    /**
     * Enable client blood render
     */

    public boolean enableClientBlood = true;

    /**
     * Punishment for a civilian's accidental killing of another civilian
     */

    public boolean accidentalKillPunishment = true;

    /**
     * Allow Natural deaths to trigger voodoo (deaths without an assigned killer)
     */

    public boolean voodooNonKillerDeaths = false;

    /**
     * Makes voodoos act like Evil players when shot by a revolver (no backfire, no
     * gun lost)
     */

    public boolean voodooShotLikeEvil = true;

    /**
     * Maximum number of Conductors allowed
     */

    public int conductorMax = 1;
    /**
     * Maximum number of Executioners allowed
     */

    public int executionerMax = 1;
    /**
     * Maximum number of Vultures allowed
     */

    public int vultureMax = 1;
    /**
     * Maximum number of Jesters allowed
     */

    public int jesterMax = 1;
    /**
     * Maximum number of Morphlings allowed
     */

    public int morphlingMax = 1;
    /**
     * Maximum number of Bartenders allowed
     */

    public int bartenderMax = 1;
    /**
     * Maximum number of Noisemakers allowed
     */

    public int noisemakerMax = 1;
    /**
     * Maximum number of Phantoms allowed
     */

    public int phantomMax = 1;
    /**
     * Maximum number of Awesome Bingluses allowed
     */

    public int awesomeBinglusMax = 1;
    /**
     * Maximum number of Swappers allowed
     */

    public int swapperMax = 1;
    /**
     * Maximum number of Voodoos allowed
     */

    public int voodooMax = 1;
    /**
     * Maximum number of Coroners allowed
     */

    public int coronerMax = 1;
    /**
     * Maximum number of Recallers allowed
     */

    public int recallerMax = 1;
    /**
     * Maximum number of Broadcasters allowed
     */
    public int broadcasterMax = 1;
    /**
     * Maximum number of Gamblers allowed
     */

    public int gamblerMax = 1;
    /**
     * Maximum number of Glitch Robots allowed
     */

    public int glitchRobotMax = 1;
    /**
     * Maximum number of Ghosts allowed
     */

    public int ghostMax = 1;

    /**
     * Whether Executioners can manually select their targets. If disabled, targets
     * will be assigned randomly
     */

    public boolean executionerCanSelectTarget = false;

    /**
     * Morphling - Morph duration in seconds
     */

    public int morphlingMorphDuration = 35;
    /**
     * Morphling - Morph cooldown in seconds
     */

    public int morphlingMorphCooldown = 20;

    // // /**
    // *Recaller-
    // Maximum recall
    // distance in blocks*/

    public int recallerMaxDistance = 50;

    /**
     * Recaller - Recall mark cooldown in seconds
     */

    public int recallerMarkCooldown = 10;

    /**
     * Recaller - Teleport cooldown in seconds
     */

    public int recallerTeleportCooldown = 30;

    /**
     * Phantom - Invisibility duration in seconds
     */

    public int phantomInvisibilityDuration = 30;

    /**
     * Phantom - Invisibility cooldown in seconds
     */

    public int phantomInvisibilityCooldown = 90;

    /**
     * Voodoo - Voodoo ritual cooldown in seconds
     */

    public int voodooCooldown = 30;

    /**
     * Vulture - Eat body cooldown in seconds
     */

    public int vultureEatCooldown = 3;

    /**
     * Swapper - Swap cooldown in seconds
     */

    public int swapperSwapCooldown = 60;

    /**
     * Manipulator - Control target cooldown in seconds
     */

    public int manipulatorCooldown = 60;

    /**
     * (Client Side) Welcome Voice - Play welcome voice
     */

    @Category("magic")
    public String credit = "";
}