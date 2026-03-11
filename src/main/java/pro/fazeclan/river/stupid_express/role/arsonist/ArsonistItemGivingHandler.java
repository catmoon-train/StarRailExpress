package pro.fazeclan.river.stupid_express.role.arsonist;

import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent;

public class ArsonistItemGivingHandler {

    public static void init() {
        ModdedRoleAssigned.EVENT.register(((player, role) -> {
            if (role.equals(SERoles.ARSONIST)) {
                player.addItem(SEItems.JERRY_CAN.getDefaultInstance());
                player.addItem(SEItems.LIGHTER.getDefaultInstance());
                var dousedComponent = DousedPlayerComponent.KEY.get(player);
                dousedComponent.dousedCount = 0;
            }
        }));
    }

}
