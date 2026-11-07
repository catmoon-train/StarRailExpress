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

/**
 * Steve 的挖掘与材料商店。挖任务点不拆方块，只累计虚拟材料并扣镐耐久。
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

    /** 完成一次任务点开采：方块不变，30% 空挖也正常消耗耐久。 */
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
        SteveMaterial material = randomDrop(level);
        if (material != null) data.add(material, 1);
        pick.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.7F, 1.1F);
        if (material == null) {
            player.displayClientMessage(Component.translatable("message.noellesroles.steve.mined_empty")
                    .withStyle(ChatFormatting.GRAY), true);
        } else {
            player.displayClientMessage(Component.translatable("message.noellesroles.steve.mined",
                    Component.translatable(material.item().getDescriptionId())).withStyle(ChatFormatting.GREEN), true);
        }
        return true;
    }

    /** 服务端独立抽取：30% 无产出，其余四种材料各占 17.5%。 */
    public static SteveMaterial randomDrop(ServerLevel level) {
        return materialDrop(level.random.nextInt(40));
    }

    static SteveMaterial materialDrop(int roll) {
        if (roll < 0 || roll >= 40) throw new IllegalArgumentException("roll must be in [0, 40)");
        return roll < 12 ? null : SteveMaterial.values()[(roll - 12) / 7];
    }

    public static int count(Player player, Item item) {
        SteveRoleData data = RoleData.getNullable(SteveRoleData.class, player);
        SteveMaterial material = SteveMaterial.forItem(item);
        return data == null || material == null ? 0 : data.count(material);
    }

    public static boolean take(Player player, Item item, int amount) {
        SteveRoleData data = RoleData.getNullable(SteveRoleData.class, player);
        SteveMaterial material = SteveMaterial.forItem(item);
        return data != null && material != null && data.take(material, amount);
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
