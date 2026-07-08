package net.exmo.sre.repair.role;

import net.exmo.sre.repair.role.RepairRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.awt.*;

public class RepairRoles {
    public static final ResourceLocation REPAIR_SURVIVOR_ID = Noellesroles.id("repair_survivor");
    public static final ResourceLocation REPAIR_HUNTER_ID = Noellesroles.id("repair_hunter");
    public static final ResourceLocation REPAIR_NEUTRAL_ID = Noellesroles.id("repair_neutral");
    public static final ResourceLocation REPAIR_MECHANIC_ID = Noellesroles.id("repair_mechanic");
    public static final ResourceLocation REPAIR_MEDIC_ID = Noellesroles.id("repair_medic");
    public static final ResourceLocation REPAIR_RUNNER_ID = Noellesroles.id("repair_runner");
    public static final ResourceLocation REPAIR_WARDEN_ID = Noellesroles.id("repair_warden");
    public static final ResourceLocation REPAIR_BRUTE_ID = Noellesroles.id("repair_brute");
    public static final ResourceLocation REPAIR_TRACKER_ID = Noellesroles.id("repair_tracker");
    public static final ResourceLocation REPAIR_ARCHIVIST_ID = Noellesroles.id("repair_archivist");
    public static final ResourceLocation REPAIR_SABOTEUR_ID = Noellesroles.id("repair_saboteur");
    public static final ResourceLocation REPAIR_COLLECTOR_ID = Noellesroles.id("repair_collector");
    public static SRERole REPAIR_SURVIVOR = TMMRoles.registerRole(new RepairRole(
            REPAIR_SURVIVOR_ID, new Color(60, 210, 230).getRGB(), true, false,
            SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false))
            .setCanBeRandomedByOtherRoles(false).setCanSeeCoin(true);

    public static SRERole REPAIR_HUNTER = TMMRoles.registerRole(new RepairRole(
            REPAIR_HUNTER_ID, new Color(140, 20, 20).getRGB(), false, true,
            SRERole.MoodType.FAKE, Integer.MAX_VALUE, true))
            .setCanBeRandomedByOtherRoles(false).setCanSeeCoin(true);

    public static SRERole REPAIR_NEUTRAL = TMMRoles.registerRole(new RepairRole(
            REPAIR_NEUTRAL_ID, new Color(210, 180, 60).getRGB(), false, false,
            SRERole.MoodType.FAKE, TMMRoles.CIVILIAN.getMaxSprintTime(), true))
            .setCanBeRandomedByOtherRoles(false).setCanSeeCoin(true);

    public static SRERole REPAIR_MECHANIC = TMMRoles.registerRole(new RepairRole(
            REPAIR_MECHANIC_ID, new Color(65, 220, 230).getRGB(), true, false,
            SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false))
            .setCanBeRandomedByOtherRoles(false).setCanSeeCoin(true);
    public static SRERole REPAIR_MEDIC = TMMRoles.registerRole(new RepairRole(
            REPAIR_MEDIC_ID, new Color(90, 245, 180).getRGB(), true, false,
            SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false))
            .setCanBeRandomedByOtherRoles(false).setCanSeeCoin(true);
    public static SRERole REPAIR_RUNNER = TMMRoles.registerRole(new RepairRole(
            REPAIR_RUNNER_ID, new Color(90, 150, 255).getRGB(), true, false,
            SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime() + 40, false))
            .setCanBeRandomedByOtherRoles(false).setCanSeeCoin(true);

    public static SRERole REPAIR_WARDEN = TMMRoles.registerRole(new RepairRole(
            REPAIR_WARDEN_ID, new Color(130, 25, 25).getRGB(), false, true,
            SRERole.MoodType.FAKE, Integer.MAX_VALUE, true))
            .setCanBeRandomedByOtherRoles(false).setCanSeeCoin(true);
    public static SRERole REPAIR_BRUTE = TMMRoles.registerRole(new RepairRole(
            REPAIR_BRUTE_ID, new Color(180, 45, 35).getRGB(), false, true,
            SRERole.MoodType.FAKE, Integer.MAX_VALUE, true))
            .setCanBeRandomedByOtherRoles(false).setCanSeeCoin(true);
    public static SRERole REPAIR_TRACKER = TMMRoles.registerRole(new RepairRole(
            REPAIR_TRACKER_ID, new Color(115, 35, 160).getRGB(), false, true,
            SRERole.MoodType.FAKE, Integer.MAX_VALUE, true))
            .setCanBeRandomedByOtherRoles(false).setCanSeeCoin(true);

    public static SRERole REPAIR_ARCHIVIST = TMMRoles.registerRole(new RepairRole(
            REPAIR_ARCHIVIST_ID, new Color(210, 180, 60).getRGB(), false, false,
            SRERole.MoodType.FAKE, TMMRoles.CIVILIAN.getMaxSprintTime(), true))
            .setCanBeRandomedByOtherRoles(false).setCanSeeCoin(true);
    public static SRERole REPAIR_SABOTEUR = TMMRoles.registerRole(new RepairRole(
            REPAIR_SABOTEUR_ID, new Color(195, 130, 35).getRGB(), false, false,
            SRERole.MoodType.FAKE, TMMRoles.CIVILIAN.getMaxSprintTime(), true))
            .setCanBeRandomedByOtherRoles(false).setCanSeeCoin(true);
    public static SRERole REPAIR_COLLECTOR = TMMRoles.registerRole(new RepairRole(
            REPAIR_COLLECTOR_ID, new Color(190, 190, 70).getRGB(), false, false,
            SRERole.MoodType.FAKE, TMMRoles.CIVILIAN.getMaxSprintTime(), true))
            .setCanBeRandomedByOtherRoles(false).setCanSeeCoin(true);
}
