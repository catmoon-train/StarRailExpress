package org.agmas.noellesroles.game.halloween.online;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.agmas.noellesroles.game.halloween.HalloweenItems;
import org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritHandler;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/** 将落盘失败的稳定收益凭据留在真实物品上。凭据只包含收益，不含配置或密钥。
 * 凭据代表已获得的游戏南瓜：转手/拆栈重试同一个 id，不重新发放职业累计。 */
public final class OnlinePumpkinIncome {
    private static final String RECEIPTS = "noellesroles_online_pumpkin_receipts";
    private static final String SETTLED = "noellesroles_pumpkin_settled";
    private static long lastMalformedWarning;
    private OnlinePumpkinIncome() {}

    public static boolean hasReceipt(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(RECEIPTS);
    }

    /** 返回本次应给原职业账本的数量；失败重试、转移凭据均返回零。 */
    public static int rewardCredit(ServerPlayer player, ItemStack stack) {
        if (hasReceipt(stack)) {
            retry(player, stack);
            return 0;
        }
        if (PumpkinSpiritHandler.settled(stack)) return 0;
        int amount = stack.getCount();
        if (HalloweenOnlineSync.shouldRecord(player)) {
            addReceipt(stack, fresh(player, amount)); // force 之前保留稳定 id，错误不标记成功。
            retry(player, stack);
        } else setSettled(stack);
        return amount;
    }

    /** 拾取/小镇事务已成功插入后调用；落盘失败时必须修改实际背包栈，不能只修改地上余数。
     * 成功路径不改变任何物品组件/合并规则。 */
    public static void pickup(ServerPlayer player, int count) {
        if (count <= 0 || !HalloweenOnlineSync.shouldRecord(player)) return;
        Earning earning = fresh(player, count);
        if (HalloweenOnlineSync.persist(player, earning)) return;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack actual = player.getInventory().getItem(i);
            if (!actual.isEmpty() && actual.is(HalloweenItems.PUMPKIN) && PumpkinSpiritHandler.settled(actual)) {
                // 南瓜可合并到已有 settled 栈；凭据 amount 是本次成功插入量，不是整个栈的数量。
                addReceipt(actual, earning);
                return;
            }
        }
        // 正常 Inventory.add/小镇事务保证存在真实南瓜；不把异常调用伪装成可靠成功。
        throw new IllegalStateException("Fresh pumpkin income has no actual inventory stack; outbox write failed");
    }

    public static void retryInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack actual = player.getInventory().getItem(i);
            if (!actual.isEmpty() && actual.is(HalloweenItems.PUMPKIN) && hasReceipt(actual)) retry(player, actual);
        }
    }

    private static Earning fresh(ServerPlayer player, int count) {
        // 身份只从服务端真实玩家取得，绝不接受客户端发送的 UUID。
        return new Earning(UUID.randomUUID(), player.getUUID(), count, System.currentTimeMillis());
    }

    private static void addReceipt(ItemStack stack, Earning earning) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            ListTag receipts = tag.getList(RECEIPTS, Tag.TAG_COMPOUND);
            CompoundTag receipt = new CompoundTag();
            receipt.putUUID("id", earning.id());
            receipt.putUUID("player", earning.minecraftUuid());
            receipt.putInt("amount", earning.amount());
            receipt.putLong("earnedAt", earning.earnedAt());
            receipts.add(receipt);
            tag.put(RECEIPTS, receipts);
            tag.remove(SETTLED);
        });
    }

    private static void retry(ServerPlayer player, ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag receipts = tag.getList(RECEIPTS, Tag.TAG_COMPOUND);
        if (receipts.isEmpty()) {
            malformed();
            return;
        }
        ListTag remaining = new ListTag();
        for (int i = 0; i < receipts.size(); i++) {
            CompoundTag receipt = receipts.getCompound(i);
            boolean persisted = false;
            try {
                if (!receipt.hasUUID("id") || !receipt.hasUUID("player")) throw new IllegalArgumentException();
                Earning earning = new Earning(receipt.getUUID("id"), receipt.getUUID("player"),
                        receipt.getInt("amount"), receipt.getLong("earnedAt"));
                persisted = HalloweenOnlineSync.persist(player, earning);
            } catch (RuntimeException ex) { malformed(); }
            if (!persisted) remaining.add(receipt);
        }
        if (remaining.isEmpty()) {
            tag.remove(RECEIPTS);
            tag.putBoolean(SETTLED, true);
        } else {
            tag.put(RECEIPTS, remaining);
            tag.remove(SETTLED);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void setSettled(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(SETTLED, true));
    }

    private static void malformed() {
        long now = System.currentTimeMillis();
        if (now - lastMalformedWarning >= 60_000) {
            lastMalformedWarning = now;
            LoggerFactory.getLogger(OnlinePumpkinIncome.class).error("[HalloweenOnline] 南瓜收益凭据损坏；保留原始物品供恢复，不会当作已写盘。");
        }
    }
}
