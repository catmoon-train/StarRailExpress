package pro.fazeclan.river.stupid_express.constants;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.Util;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentManager;

import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.role.amnesiac.RoleSelectionHandler;
import pro.fazeclan.river.stupid_express.role.arsonist.OilDousingHandler;
import pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent;
import pro.fazeclan.river.stupid_express.role.avaricious.AvariciousGoldHandler;
import pro.fazeclan.river.stupid_express.role.initiate.InitiateRole;
import pro.fazeclan.river.stupid_express.role.necromancer.RevivalSelectionHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SERoles {
    private static final HashMap<String, SRERole> ROLES = new HashMap<>();

    public static SRERole AMNESIAC = registerRole(new NormalRole(
            StupidExpress.id("amnesiac"),
            0x9baae8,
            false,
            false,
            SRERole.MoodType.REAL,
            TMMRoles.CIVILIAN.getMaxSprintTime(),
            false)).setPassiveIncome(false).setNeutralForInnocent(true).setDefaultEnableChance(5000).setDefaultEnableNeededPlayerCount(12);

    public static SRERole ARSONIST = registerRole(new NormalRole(
            StupidExpress.id("arsonist"),
            0xfc9526,
            false,
            false,
            SRERole.MoodType.FAKE,
            -1,
            true)).setCanUseInstinctAndNightVision(true).setDefaultEnableNeededPlayerCount(12);

    public static SRERole AVARICIOUS = registerRole(new NormalRole(
            StupidExpress.id("avaricious"),
            0x8f00ff,
            false,
            true,
            SRERole.MoodType.FAKE,
            -1,
            true)).setServerGameTickEvent((player, gameWorldComponent) -> {
                AvariciousGoldHandler.playerServerTick(player, gameWorldComponent);
            }).setPassiveIncome(false).setInitialCoinCount(0).setDefaultEnableNeededPlayerCount(12);

    public static SRERole NECROMANCER = registerRole(new NormalRole(
            StupidExpress.id("necromancer"),
            0x9457ff,
            false,
            true,
            SRERole.MoodType.FAKE,
            -1,
            true)).setDefaultEnableChance(5000).setDefaultEnableNeededPlayerCount(12);

    public static SRERole INITIATE = registerRole(new InitiateRole(
            StupidExpress.id("initiate"),
            0xffd154,
            false,
            false,
            SRERole.MoodType.REAL,
            TMMRoles.CIVILIAN.getMaxSprintTime(),
            true)).setCanBeRandomedByOtherRoles(false).setDefaultMax(1).setDefaultEnableChance(8000)
            .setDefaultEnableNeededPlayerCount(12)
            .setCannotKnifeLeftClick(true);

    public static List<ShopEntry> INITIATE_SHOP = Util.make(new ArrayList<>(), entries -> {
        entries.add(new ShopEntry(TMMItems.KNIFE.getDefaultInstance(), 200, ShopEntry.Type.WEAPON));
    });

    public static List<ShopEntry> NECROMANCER_SHOP = Util.make(new ArrayList<>(), entries -> {
        entries.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
    });

    public static void init() {
        RoleAssignmentManager.addOccupationRole(SERoles.INITIATE, SERoles.INITIATE);

        /// AMNESIAC
        Harpymodloader.setRoleMaximum(AMNESIAC.getIdentifier(), 1);
        RoleSelectionHandler.init();

        /// ARSONIST
        Harpymodloader.setRoleMaximum(ARSONIST.getIdentifier(), 1);
        OilDousingHandler.init();

        ResetPlayerEvent.EVENT.register(player -> {
            var dousedComponent = DousedPlayerComponent.KEY.get(player);
            dousedComponent.reset();
            dousedComponent.sync();
            player.removeTag("nearDoor");
        });
        /// NECROMANCER

        RevivalSelectionHandler.init();

        /// AVARICIOUS

        AvariciousGoldHandler.registerEvents();

    }

    public static SRERole registerRole(SRERole role) {
        TMMRoles.registerRole(role);
        ROLES.put(role.identifier().getPath(), role);
        return role;
    }

}
