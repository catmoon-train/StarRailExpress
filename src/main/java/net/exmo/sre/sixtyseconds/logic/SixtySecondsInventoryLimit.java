package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.component.FamilyPosition;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 背包槽位限制（用屏障占位 + 弹出手动放入；仅 60s 模式相位机驱动，结束清除，不改共享代码）：
 * <ul>
 *   <li><b>准备阶段</b>：按家庭身份携带上限——父亲 8 格、其余 2 格（{@link FamilyPosition#carryLimit}），其余槽位屏障覆盖。</li>
 *   <li><b>游戏日</b>：可用两排（0–17），其余屏障覆盖。</li>
 * </ul>
 */
public final class SixtySecondsInventoryLimit {
    /** 游戏日可用槽位数（快捷栏 + 主背包第一排）。 */
    public static final int DAY_ALLOWED_SLOTS = 18;
    public static final int LAST_MAIN_SLOT = 35;

    private SixtySecondsInventoryLimit() {
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        boolean prep = data.phase == SixtySecondsPhase.PREPARATION;
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerSpectatingOrCreative(player)) {
                continue;
            }
            int allowed = prep ? carryLimit(player) : DAY_ALLOWED_SLOTS;
            enforce(player, allowed);
        }
    }

    private static int carryLimit(ServerPlayer player) {
        FamilyPosition position = SixtySecondsStatsComponent.KEY.get(player).familyPosition;
        return position == null ? FamilyPosition.MOTHER.carryLimit : position.carryLimit;
    }

    private static void enforce(ServerPlayer player, int firstRestrictedSlot) {
        Inventory inventory = player.getInventory();
        for (int slot = firstRestrictedSlot; slot <= LAST_MAIN_SLOT; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (isBarrier(current)) {
                continue;
            }
            if (!current.isEmpty()) {
                ItemStack real = current.copy();
                inventory.setItem(slot, barrier()); // 先占位，避免 add 又落回受限槽
                inventory.add(real);
                if (!real.isEmpty()) {
                    player.drop(real, false);
                }
            } else {
                inventory.setItem(slot, barrier());
            }
        }
    }

    /** 结束/退出模式时清除屏障占位（只删屏障，不动真实物品）。 */
    public static void clear(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot <= LAST_MAIN_SLOT; slot++) {
                if (isBarrier(inventory.getItem(slot))) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    private static ItemStack barrier() {
        return new ItemStack(Items.BARRIER);
    }

    private static boolean isBarrier(ItemStack stack) {
        return stack.is(Items.BARRIER);
    }
}
