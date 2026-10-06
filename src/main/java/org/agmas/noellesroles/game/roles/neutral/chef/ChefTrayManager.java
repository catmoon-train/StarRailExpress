package org.agmas.noellesroles.game.roles.neutral.chef;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.ChefTrayS2CPacket;
import org.agmas.noellesroles.role.ModRoles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 厨师「客户端」食物盘 / 饮料盘的服务端账本。
 *
 * <p>盘子本身只画在客户端（见 {@code ClientChefTrayManager}），服务端不生成方块实体，
 * 所以这里用「坐标 → 盘 id」和「盘 id → 内容物」两张表把状态单独记下来。
 *
 * <p>规则：
 * <ul>
 * <li>只有厨师能放入，且食物盘只收「烹饪后的食物 / 一包零食」，饮料盘只收「一杯水」；</li>
 * <li>任何玩家都能取，但同一名玩家两次取用之间有 {@link #TAKE_COOLDOWN_TICKS} 冷却；</li>
 * <li><b>取用不消耗存货</b>：盘子里的食物 / 饮料不会被取走，只是「借」一份给玩家，
 * 所以同一个盘子可以被多名玩家反复取用，也永远不会因为被取空而消失内容物；</li>
 * <li>盘子会一直存在到当局游戏结束，届时 {@link #clearAll(ServerLevel)} 统一清除。</li>
 * </ul>
 */
public final class ChefTrayManager {

    /** 每名玩家两次取用之间的冷却（30 秒）。 */
    public static final int TAKE_COOLDOWN_TICKS = 20 * 30;

    /** 单个盘子最多能装几份。 */
    public static final int MAX_ITEMS_PER_TRAY = 8;

    /** 一个盘子的数据。 */
    public static final class Tray {
        public final UUID id;
        public final BlockPos pos;
        public final boolean drink;
        public final List<ItemStack> items = new ArrayList<>();

        public Tray(UUID id, BlockPos pos, boolean drink) {
            this.id = id;
            this.pos = pos.immutable();
            this.drink = drink;
        }

        public boolean isEmpty() {
            return items.isEmpty();
        }
    }

    /** 所有盘子，保持放置顺序。 */
    private static final Map<UUID, Tray> TRAYS = new LinkedHashMap<>();
    /** 坐标 → 盘 id，用于右键时快速定位。 */
    private static final Map<BlockPos, UUID> INDEX = new HashMap<>();
    /** 玩家 UUID → 下次可取用的游戏时刻。 */
    private static final Map<UUID, Long> TAKE_READY_AT = new HashMap<>();

    private ChefTrayManager() {
    }

    // ==================== 查询 ====================

    public static Tray getAt(BlockPos pos) {
        UUID id = INDEX.get(pos);
        return id == null ? null : TRAYS.get(id);
    }

    public static boolean hasTrayAt(BlockPos pos) {
        return getAt(pos) != null;
    }

    /** 某个物品能否放进这种盘子。 */
    public static boolean canPut(ItemStack stack, boolean drinkTray) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (drinkTray) {
            return stack.is(ModItems.A_BOTTLE_OF_WATER);
        }
        return stack.is(ModItems.COOKED_FOOD) || stack.is(ModItems.LINGSHI);
    }

    // ==================== 放置 ====================

    /**
     * 在 pos 处放一个「客户端」盘子。返回 true 表示成功。
     */
    public static boolean placeTray(ServerPlayer chef, BlockPos pos, boolean drink) {
        if (chef == null || pos == null) {
            return false;
        }
        ServerLevel level = chef.serverLevel();
        if (!isRunningGame(level) || !GameUtils.isPlayerAliveAndSurvival(chef)) {
            return false;
        }
        pos = pos.immutable();
        if (TRAYS.size() >= 64 || INDEX.containsKey(pos)) {
            return false;
        }
        // 服务端该位置必须真的是空气：客户端画出来的方块服务端并不承认，不能覆盖真实方块
        BlockState state = level.getBlockState(pos);
        if (!state.isAir()) {
            return false;
        }
        // 必须踩在地面上
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            chef.displayClientMessage(
                    Component.translatable("message.noellesroles.chef.tray_need_ground").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        UUID id = UUID.randomUUID();
        Tray tray = new Tray(id, pos, drink);
        TRAYS.put(id, tray);
        INDEX.put(pos, id);

        broadcast(level, ChefTrayS2CPacket.place(id, pos, drink, ItemStack.EMPTY));
        level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, drink ? 1.4F : 1.0F);
        chef.displayClientMessage(Component.translatable(drink
                ? "message.noellesroles.chef.tray_placed_drink"
                : "message.noellesroles.chef.tray_placed_food").withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    // ==================== 交互（放入 / 取出） ====================

    /**
     * 处理一次右键：优先尝试放入，条件不满足（或空手）时尝试取出。
     */
    public static void onRightClick(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return;
        }
        Tray tray = getAt(pos.immutable());
        if (tray == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!GameUtils.isPlayerAliveAndSurvival(player) || player.isSpectator()) {
            return;
        }
        if (player.distanceToSqr(pos.getCenter()) > 36.0D) {
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty() && canPut(held, tray.drink)) {
            if (!isChef(player)) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.chef.tray_only_chef_put").withStyle(ChatFormatting.RED),
                        true);
                return;
            }
            if (!tray.items.isEmpty()) {
                // 盘子里已经有东西了就不收：盘内物品是「展示 + 无限取用」的，
                // 塞第二份既渲染不出来（只下发一份代表物品）也没有意义
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.chef.tray_has_item")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }
            if (tray.items.size() >= MAX_ITEMS_PER_TRAY) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.chef.tray_full").withStyle(ChatFormatting.RED),
                        true);
                return;
            }
            tray.items.add(held.split(1));
            player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.2F);
            syncContent(level, tray);
            return;
        }

        // 只有空手时才尝试取出。
        // 手上有东西就取出会造成「厨师手持食物右键盘子 → 食物被放进盘子又立刻被取回、
        // 并白白进入 30 秒冷却」这种看起来像凭空消耗冷却的情况。
        if (!held.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.chef.tray_hand_not_empty")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return;
        }
        takeOne(player, tray);
    }

    /**
     * 从盘子里<b>复制</b>一份直接放进玩家的主手（<b>不会把盘子里的食物取走</b>）。
     *
     * <p>
     * 盘子里的存货是「无限供应」的：这里只读取一份副本发给玩家，{@code tray.items} 保持不变，
     * 因此冷却结束后右键还能继续取，同一个盘子可以供多名玩家反复取用。
     *
     * <p>
     * 主手必须为空：取出的东西要直接给到主手，主手被占时既放不下，也不该白扣一次冷却。
     * 取出失败（空盘 / 主手有东西 / 冷却中）一律不会设置冷却，只有真正拿到食物才进入冷却。
     */
    private static void takeOne(ServerPlayer player, Tray tray) {
        if (tray.items.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.chef.tray_empty").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!player.getMainHandItem().isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.chef.tray_hand_not_empty")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return;
        }

        long now = player.level().getGameTime();
        Long readyAt = TAKE_READY_AT.get(player.getUUID());
        if (readyAt != null && now < readyAt) {
            player.displayClientMessage(Component.translatable("message.noellesroles.chef.tray_take_cd",
                    String.format("%.1f", (readyAt - now) / 20.0F)).withStyle(ChatFormatting.RED), true);
            return;
        }

        ItemStack taken = tray.items.get(player.getRandom().nextInt(tray.items.size())).copy();
        taken.setCount(1);
        // 直接进主手：保证「冷却结束后右键一定能拿到厨师放进去的那个食物 / 饮料」，
        // 不会因为 Inventory.add 找空槽失败而只掉在地上。
        player.setItemInHand(InteractionHand.MAIN_HAND, taken);
        player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 0.9F);
        TAKE_READY_AT.put(player.getUUID(), now + TAKE_COOLDOWN_TICKS);
        // 取用不消耗存货 → 盘子内容没有变化，不需要再广播 UPDATE
    }

    // ==================== 清理 ====================

    /** 游戏结束时清除所有「客户端」盘子。 */
    public static void clearAll(ServerLevel level) {
        if (level == null) {
            return;
        }
        for (Tray tray : TRAYS.values()) {
            level.removeBlockEntity(tray.pos);
            broadcast(level, ChefTrayS2CPacket.remove(tray.id, tray.pos));
        }
        TRAYS.clear();
        INDEX.clear();
        TAKE_READY_AT.clear();
    }

    // ==================== 内部工具 ====================

    private static boolean isRunningGame(ServerLevel level) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        return gameWorld != null && gameWorld.isRunning();
    }

    private static boolean isChef(ServerPlayer player) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld != null && gameWorld.isRole(player, ModRoles.CHEF);
    }

    /** 内容物变化时通知所有客户端：带上要渲染的那一份物品，供客户端渲染真实模型。 */
    private static void syncContent(ServerLevel level, Tray tray) {
        ItemStack shown = tray.items.isEmpty() ? ItemStack.EMPTY : tray.items.get(0).copy();
        if (!shown.isEmpty()) {
            shown.setCount(1);
        }
        broadcast(level, ChefTrayS2CPacket.update(tray.id, tray.pos, shown));
    }

    private static void broadcast(ServerLevel level, ChefTrayS2CPacket packet) {
        for (ServerPlayer player : level.players()) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    /** 供调试 / 其它逻辑查询当前场上盘子数量。 */
    public static int trayCount() {
        return TRAYS.size();
    }
}
