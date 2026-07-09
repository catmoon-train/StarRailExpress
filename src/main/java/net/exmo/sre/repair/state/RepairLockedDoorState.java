package net.exmo.sre.repair.state;

import net.exmo.sre.repair.*;
import net.exmo.sre.repair.role.*;
import net.exmo.sre.repair.arena.*;
import net.exmo.sre.repair.event.*;
import net.exmo.sre.repair.util.*;

import io.wifi.starrailexpress.game.data.MapConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.agmas.noellesroles.component.ModComponents;

import java.util.*;

public final class RepairLockedDoorState {
    private static final Map<ServerLevel, Map<BlockPos, DoorLock>> LOCKS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<String, EscapeRoute>> ROUTES = new WeakHashMap<>();

    private RepairLockedDoorState() {
    }

    public static void prepare(ServerLevel level, MapConfig.RepairConfig config) {
        Map<BlockPos, DoorLock> locks = new HashMap<>();
        Map<String, EscapeRoute> routes = new HashMap<>();
        if (config != null) {
            for (MapConfig.LockedDoorEntry entry : config.lockedDoors) {
                locks.put(entry.pos.toBlockPos(), new DoorLock(entry.lockId, entry.requiredItem, entry.consume, false));
            }
            for (MapConfig.EscapeRouteEntry entry : config.escapeRoutes) {
                routes.put(entry.id, new EscapeRoute(entry.id, entry.displayKey, entry.pos.toBlockPos(),
                        Math.max(1, entry.capacity), new ArrayList<>(entry.requiredItems), 0));
            }
        } else {
            prepareDefaultMansionLocks(level, locks, routes);
        }
        LOCKS.put(level, locks);
        ROUTES.put(level, routes);
    }

    /**
     * 默认庄园+墓地场景的锁与逃生路线（与 RepairManorScene 的坐标一一对应）。
     * 锁只放在捷径上，主通道全部是无门拱廊 —— 幸存者不会再被锁死在房间里。
     * 两座出口大门由 RepairExitGateBlock 自己处理（修满机器后可开），不注册为路线。
     */
    private static void prepareDefaultMansionLocks(ServerLevel level, Map<BlockPos, DoorLock> locks,
            Map<String, EscapeRoute> routes) {
        BlockPos base = RepairArenaBuilder.defaultMansionBase(level);
        // 锁 #1：工坊 → 墓地小路的后门（旧钥匙）
        locks.put(RepairManorScene.at(base, RepairManorScene.LOCK_WORKSHOP_BACKDOOR),
                new DoorLock("manor_workshop_backdoor", "repair_old_key", true, false));
        // 锁 #2：教堂东侧门（撬锁器）
        locks.put(RepairManorScene.at(base, RepairManorScene.LOCK_CHAPEL_SIDE_DOOR),
                new DoorLock("chapel_side_door", "repair_lockpick", true, false));
        // 锁 #3：地穴铁门（旧钥匙），门后是密道与高级战利品
        locks.put(RepairManorScene.at(base, RepairManorScene.LOCK_CRYPT_GATE),
                new DoorLock("crypt_gate", "repair_old_key", true, false));

        routes.put("crypt_tunnel", new EscapeRoute("crypt_tunnel",
                "hud.noellesroles.repair.route.crypt_tunnel",
                RepairManorScene.at(base, RepairManorScene.ROUTE_CRYPT_TUNNEL), 1,
                new ArrayList<>(List.of("repair_old_key", "repair_crowbar")), 0));
        routes.put("service_lift", new EscapeRoute("service_lift",
                "hud.noellesroles.repair.route.service_lift",
                RepairManorScene.at(base, RepairManorScene.ROUTE_SERVICE_LIFT), 2,
                new ArrayList<>(List.of("repair_fuse", "repair_gear_handle", "repair_battery")), 0));
    }

    public static boolean handleUse(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level) || !RepairModeState.isNonHunterRepairPlayer(player)) {
            return false;
        }
        for (EscapeRoute route : ROUTES.getOrDefault(level, Map.of()).values()) {
            // 容差 1 格：密道铁栏是 2 格高、电梯铁板是 3x3，点击任意相邻部分都算
            if (route.pos.distManhattan(pos) <= 1) {
                return tryEscape(level, player, route);
            }
        }
        Map<BlockPos, DoorLock> levelLocks = LOCKS.getOrDefault(level, Map.of());
        DoorLock lock = levelLocks.get(pos);
        BlockPos lockPos = pos;
        if (lock == null && level.getBlockState(pos).hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && level.getBlockState(pos).getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            lockPos = pos.below();
            lock = levelLocks.get(lockPos);
        }
        if (lock == null || lock.opened) {
            return false;
        }
        if (!hasRequired(player, lock.requiredItem)) {
            prompt(player, Component.translatable("message.noellesroles.repair.lock_missing",
                    itemName(lock.requiredItem)).withStyle(ChatFormatting.RED));
            return true;
        }
        consumeOrDamage(player, lock.requiredItem, lock.consume);
        lock.opened = true;
        openBlock(level, lockPos);
        prompt(player, Component.translatable("message.noellesroles.repair.lock_opened").withStyle(ChatFormatting.GREEN));
        level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    public static void reset(ServerLevel level) {
        LOCKS.remove(level);
        ROUTES.remove(level);
    }

    private static boolean tryEscape(ServerLevel level, ServerPlayer player, EscapeRoute route) {
        if (route.used >= route.capacity) {
            prompt(player, Component.translatable("message.noellesroles.repair.route_full").withStyle(ChatFormatting.RED));
            return true;
        }
        for (String required : route.requiredItems) {
            if (!hasRequired(player, required)) {
                prompt(player, Component.translatable("message.noellesroles.repair.route_missing",
                        itemName(required)).withStyle(ChatFormatting.RED));
                return true;
            }
        }
        for (String required : route.requiredItems) {
            consumeOrDamage(player, required, !required.endsWith("crowbar"));
        }
        route.used++;
        RepairModeState.clearRestraints(player);
        player.addTag(RepairModeState.ESCAPED_TAG);
        ModComponents.REPAIR_ROLES.get(player).escapedRouteId = route.id;
        ModComponents.REPAIR_ROLES.get(player).sync();
        RepairModeState.awardCoins(player, 150, "repair_coin_source.escape_route");
        player.displayClientMessage(Component.translatable("message.noellesroles.repair.route_escaped",
                route.displayKey == null || route.displayKey.isEmpty()
                        ? Component.literal(route.id)
                        : Component.translatable(route.displayKey)), false);
        player.setGameMode(GameType.SPECTATOR);
        level.playSound(null, route.pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    private static void openBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.OPEN)) {
            level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.OPEN, true));
            BlockPos otherHalf = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherHalf);
            if (otherState.hasProperty(BlockStateProperties.OPEN)) {
                level.setBlockAndUpdate(otherHalf, otherState.setValue(BlockStateProperties.OPEN, true));
            }
        }
    }

    private static boolean hasRequired(ServerPlayer player, String id) {
        if (id == null || id.isEmpty()) {
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && itemMatches(stack, id)) {
                return true;
            }
        }
        return false;
    }

    private static void consumeOrDamage(ServerPlayer player, String id, boolean consume) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && itemMatches(stack, id)) {
                if (consume) {
                    stack.shrink(1);
                } else {
                    stack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                }
                return;
            }
        }
    }

    private static boolean itemMatches(ItemStack stack, String id) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.toString().equals(id) || key.getPath().equals(id);
    }

    private static Component itemName(String id) {
        return Component.translatable("item.noellesroles." + (id == null ? "unknown" : id.replace("noellesroles:", "")));
    }

    private static void prompt(ServerPlayer player, Component component) {
        var repair = ModComponents.REPAIR_ROLES.get(player);
        repair.lockPromptKey = component.getString();
        repair.sync();
        player.displayClientMessage(component, true);
    }

    private static final class DoorLock {
        private final String lockId;
        private final String requiredItem;
        private final boolean consume;
        private boolean opened;

        private DoorLock(String lockId, String requiredItem, boolean consume, boolean opened) {
            this.lockId = lockId;
            this.requiredItem = requiredItem;
            this.consume = consume;
            this.opened = opened;
        }
    }

    private static final class EscapeRoute {
        private final String id;
        private final String displayKey;
        private final BlockPos pos;
        private final int capacity;
        private final List<String> requiredItems;
        private int used;

        private EscapeRoute(String id, String displayKey, BlockPos pos, int capacity, List<String> requiredItems,
                int used) {
            this.id = id;
            this.displayKey = displayKey;
            this.pos = pos;
            this.capacity = capacity;
            this.requiredItems = requiredItems;
            this.used = used;
        }
    }
}
