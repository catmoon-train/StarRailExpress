package org.agmas.noellesroles.game.command_block;

import java.util.function.Predicate;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.game.command_block.item.StevePickaxeItem;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Steve 的挖掘与材料商店。挖任务点不拆方块，只给基础掉落，并扣铁镐耐久。
 */
public final class SteveMiner {
    public static final int USES = 32;
    public static final int DIAMOND_USES = 128;
    /** 客户端初始化时绑定任务点缓存；专用服务端不加载客户端类。 */
    public static Predicate<BlockPos> clientTaskBlock = pos -> false;

    private SteveMiner() {
    }

    public static boolean isSteve(Player player) {
        return player != null && SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.STEVE);
    }

    public static boolean isTaskBlock(BlockPos pos) {
        return pos != null && GameUtils.taskBlocks != null && GameUtils.taskBlocks.containsKey(pos);
    }

    public static boolean canAct(Player player) {
        return isSteve(player) && GameUtils.isPlayerAliveAndSurvival(player)
                && SREGameWorldComponent.KEY.get(player.level()).isRunning()
                && !SREGameTimeComponent.KEY.get(player.level()).isTimeFrozen()
                && !player.hasEffect(ModEffects.USED_BANED);
    }

    public static boolean canMine(Player player, BlockPos pos) {
        if (player == null || pos == null || !canAct(player)) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        boolean task = player.level().isClientSide ? clientTaskBlock.test(pos) : isTaskBlock(pos);
        return held.getItem() instanceof StevePickaxeItem && !held.isEmpty()
                && held.getDamageValue() < held.getMaxDamage() && task
                && !player.level().getBlockState(pos).isAir();
    }

    /** 挖掉任务点：方块留在原地，换成一份基础掉落。 */
    public static boolean mine(ServerPlayer player, BlockPos pos) {
        if (!canMine(player, pos) || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        SteveRoleData data = RoleData.getNullable(SteveRoleData.class, player);
        if (data == null || !player.canInteractWithBlock(pos, 1.0D)) {
            return false;
        }
        ItemStack pick = player.getMainHandItem();
        if (pick.getDamageValue() >= pick.getMaxDamage()) {
            return false;
        }
        if (!data.tryHarvest(level.getGameTime(), ((StevePickaxeItem) pick.getItem()).miningTicks())) {
            return false;
        }
        ItemStack drop = randomDrop(level);
        Component dropName = drop.getHoverName();
        if (!player.getInventory().add(drop)) {
            player.drop(drop, false);
        }
        pick.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.7F, 1.1F);
        player.displayClientMessage(Component.translatable("message.noellesroles.steve.mined", dropName)
                .withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    public static ItemStack randomDrop(ServerLevel level) {
        Item item = switch (level.random.nextInt(4)) {
            case 0 -> Items.OAK_PLANKS;
            case 1 -> Items.IRON_INGOT;
            case 2 -> Items.GOLD_INGOT;
            default -> Items.DIAMOND;
        };
        return new ItemStack(item);
    }

    public static int count(Player player, Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static boolean take(Player player, Item item, int amount) {
        if (count(player, item) < amount) {
            return false;
        }
        int left = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(item)) {
                continue;
            }
            int used = Math.min(left, stack.getCount());
            stack.shrink(used);
            left -= used;
        }
        return left == 0;
    }

    /** 商店图标显示所需材料，购买时扣材料而不是金币。 */
    public static ShopEntry offer(ItemStack goods, Item cost, int amount) {
        ItemStack icon = goods.copy();
        icon.set(DataComponents.ITEM_NAME, Component.translatable("shop.noellesroles.steve.cost",
                goods.getHoverName(), Component.translatable(cost.getDescriptionId()), amount));
        ShopEntry entry = new ShopEntry(icon, 0, ShopEntry.Type.TOOL) {
            @Override
            public boolean canBuy(Player player) {
                if (!canAct(player)) {
                    return false;
                }
                if (count(player, cost) < amount) {
                    setFailedMessage(Component.translatable("message.noellesroles.steve.need",
                            Component.translatable(cost.getDescriptionId()), amount));
                    return false;
                }
                return true;
            }

            @Override
            public boolean onBuy(Player player) {
                if (!canBuy(player)) {
                    return false;
                }
                // 先完成发货再扣材料；背包满时绝不能吞掉玩家资源。
                if (!org.agmas.noellesroles.utils.RoleUtils.insertStackInFreeSlot(player, goods.copy())) {
                    setFailedMessage(Component.translatable("message.noellesroles.steve.inventory_full"));
                    return false;
                }
                take(player, cost, amount);
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                return true;
            }
        };
        entry.setFailedMessage(Component.translatable("message.noellesroles.steve.need",
                Component.translatable(cost.getDescriptionId()), amount));
        return entry;
    }
}
