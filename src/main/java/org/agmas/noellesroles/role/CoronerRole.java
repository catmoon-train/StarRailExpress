package org.agmas.noellesroles.role;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.scanner.IdentityScannerLedger;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.innocence.CoronerRoleData;

import java.util.List;

/** 验尸官商店和随身扫描仪的首次丢弃限制，不改变原有验尸能力。 */
public final class CoronerRole extends NormalRole {
    public static final int SCANNER_PRICE = 125;

    public CoronerRole(ResourceLocation id, int color, int maxSprintTime) {
        super(id, color, true, false, MoodType.REAL, maxSprintTime, false);
        setCanSeeCoin(true);
        setRoleData(CoronerRoleData::new);
    }

    @Override public List<ShopEntry> getShopEntries() {
        return List.of(new ShopEntry(ModItems.PORTABLE_IDENTITY_SCANNER.getDefaultInstance(),
                SCANNER_PRICE, ShopEntry.Type.TOOL));
    }

    @Override public InteractionResult onDropItem(Player player, ItemStack stack) {
        if (!stack.is(ModItems.PORTABLE_IDENTITY_SCANNER) || player.isCreative()
                || !SREGameWorldComponent.KEY.get(player.level()).isRunning()
                || !GameUtils.isPlayerAliveAndSurvival(player)) return InteractionResult.PASS;
        var data = RoleData.getNullable(CoronerRoleData.class, player);
        int remaining = data == null ? IdentityScannerLedger.CORONER_DROP_LOCK_TICKS : data.scannerDropRemaining();
        if (remaining > 0) {
            player.displayClientMessage(Component.translatable("message.noellesroles.identity_scanner.drop_cooldown",
                    (remaining + 19) / 20), true);
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }
}
