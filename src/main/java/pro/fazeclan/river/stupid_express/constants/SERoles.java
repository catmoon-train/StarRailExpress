package pro.fazeclan.river.stupid_express.constants;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.api.TMMRoles;
// import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.Util;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentManager;

import pro.fazeclan.river.stupid_express.BuyableShopEntry;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.role.amnesiac.RoleSelectionHandler;
import pro.fazeclan.river.stupid_express.role.arsonist.OilDousingHandler;
import pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent;
import pro.fazeclan.river.stupid_express.role.avaricious.AvariciousGoldHandler;
import pro.fazeclan.river.stupid_express.role.necromancer.RevivalSelectionHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SERoles {
    private static final HashMap<String, Role> ROLES = new HashMap<>();

    public static Role AMNESIAC = registerRole(new NormalRole(
            StupidExpress.id("amnesiac"),
            0x9baae8,
            false,
            false,
            Role.MoodType.REAL,
            TMMRoles.CIVILIAN.getMaxSprintTime(),
            false
    ));

    public static Role ARSONIST = registerRole(new NormalRole(
            StupidExpress.id("arsonist"),
            0xfc9526,
            false,
            false,
            Role.MoodType.FAKE,
            -1,
            true
    )).setCanUseInstinct(true);

    public static Role AVARICIOUS = registerRole(new NormalRole(
            StupidExpress.id("avaricious"),
            0x8f00ff,
            false,
            true,
            Role.MoodType.FAKE,
            -1,
            true
    ));

    public static Role NECROMANCER = registerRole(new NormalRole(
            StupidExpress.id("necromancer"),
            0x9457ff,
            false,
            true,
            Role.MoodType.FAKE,
            -1,
            true
    ));

    public static Role INITIATE = registerRole(new NormalRole(
            StupidExpress.id("initiate"),
            0xffd154,
            false,
            false,
            Role.MoodType.REAL,
            TMMRoles.CIVILIAN.getMaxSprintTime(),
            true
    ));

    public static List<ShopEntry> INITIATE_SHOP = Util.make(new ArrayList<>(), entries -> {
        entries.add(new BuyableShopEntry(TMMItems.KNIFE.getDefaultInstance(), 200, ShopEntry.Type.WEAPON));
    });

    public static List<ShopEntry> NECROMANCER_SHOP = Util.make(new ArrayList<>(), entries -> {
        entries.add(new BuyableShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
    });

    public static void init() {

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
        });
        /// NECROMANCER

        RevivalSelectionHandler.init();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var playerList = server.getPlayerList().getPlayers();
            if (playerList.isEmpty()) {
                return;
            }
            var killerRoleCount = (int) Math.floor((float) playerList.size() / (float) 6);

            if (killerRoleCount > 1) {
                Harpymodloader.setRoleMaximum(NECROMANCER.getIdentifier(), playerList.size() >= 12 ? 1 : 0);
                Harpymodloader.setRoleMaximum(AVARICIOUS.getIdentifier(), 1);
                Harpymodloader.setRoleMaximum(INITIATE.getIdentifier(), playerList.size() >= 12 ? 1 : 0);
                RoleAssignmentManager.addOccupationRole(SERoles.INITIATE, SERoles.INITIATE);
            } else {
                Harpymodloader.setRoleMaximum(NECROMANCER.getIdentifier(), 0);
                Harpymodloader.setRoleMaximum(AVARICIOUS.getIdentifier(), 0);
                Harpymodloader.setRoleMaximum(INITIATE.getIdentifier(), 0);
            }
        });

        /// AVARICIOUS

        AvariciousGoldHandler.onGameStart();

    }

    public static Role registerRole(Role role) {
        TMMRoles.registerRole(role);
        ROLES.put(role.identifier().getPath(), role);
        return role;
    }

}
