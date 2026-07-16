package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsVaultBlockEntity;
import net.exmo.sre.sixtyseconds.network.OpenStationS2CPacket;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * 合成台交互：60s 模式内右键 书桌(工作台/讲台)/灶台(熔炉族/营火)/浴缸(炼药锅) →
 * 打开该站配方界面（拦截原版 GUI）；C2S 合成请求在服务端校验 科技/供电/材料 后成交。
 */
public final class SixtySecondsStations {
    private static final double CRAFT_RANGE_SQR = 6.0 * 6.0;

    /** 合成时扫描附近容器的半径（格）。 */
    private static final int CONTAINER_SCAN_RADIUS = 16;

    private SixtySecondsStations() {
    }

    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK
                .register(SixtySecondsStations::tickSmelts); // 冶金炉延迟发放
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                    || !SixtySecondsMod.isActive(level)) {
                return InteractionResult.PASS;
            }
            // 扳手拆除功能方块走物品自身 useOn；创造/潜行放行原版交互（管理员配置）
            if (player.isShiftKeyDown()
                    || player.getMainHandItem().getItem()
                            instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsWrenchItem) {
                return InteractionResult.PASS;
            }
            // 研究台：右键打开科技树（废料解锁配方）
            if (level.getBlockState(hitResult.getBlockPos())
                    .is(org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_RESEARCH_TABLE)) {
                if (GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
                    SixtySecondsTechTree.open(serverPlayer);
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.PASS;
            }
            // 拆解台：右键打开拆解界面（可合成物品按 -60% 拆回基础资源）
            if (level.getBlockState(hitResult.getBlockPos())
                    .is(org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_DISMANTLER)) {
                if (GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
                    ServerPlayNetworking.send(serverPlayer, new net.exmo.sre.sixtyseconds.network
                            .OpenDismantleS2CPacket(hitResult.getBlockPos()));
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.PASS;
            }
            SixtySecondsRecipes.Station station =
                    SixtySecondsRecipes.stationOf(level.getBlockState(hitResult.getBlockPos()));
            if (station == null || !GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
                return InteractionResult.PASS;
            }
            SixtySecondsState.Data data = SixtySecondsState.get(serverPlayer.serverLevel());
            SixtySecondsState.TeamData team =
                    data.teams.get(SixtySecondsStatsComponent.KEY.get(serverPlayer).teamId);
            String[] unlocked = team == null ? new String[0] : team.unlockedTech.toArray(new String[0]);
            boolean powered = SixtySecondsPowerSystem.isPowered(serverPlayer.serverLevel(), team);
            ServerPlayNetworking.send(serverPlayer,
                    new OpenStationS2CPacket(station.ordinal(), hitResult.getBlockPos(), unlocked, powered));
            return InteractionResult.SUCCESS;
        });
    }

    /** C2S 合成请求：尝试合成 count 次（clamp 1..64），材料不够时能合几次合几次。 */
    public static void handleCraft(ServerPlayer player, String recipeId, BlockPos stationPos, int count) {
        if (!SixtySecondsMod.isActive(player.level()) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        SixtySecondsRecipes.Recipe recipe = SixtySecondsRecipes.byId(recipeId);
        if (recipe == null) {
            return;
        }
        // 站点校验：位置上确实是对应合成站且在交互距离内
        if (player.distanceToSqr(stationPos.getX() + 0.5, stationPos.getY() + 0.5, stationPos.getZ() + 0.5)
                > CRAFT_RANGE_SQR
                || SixtySecondsRecipes.stationOf(level.getBlockState(stationPos)) != recipe.station()) {
            return;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        SixtySecondsState.TeamData team = data.teams.get(SixtySecondsStatsComponent.KEY.get(player).teamId);
        if (!SixtySecondsTechTree.isUnlocked(team, recipe.techId())) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.craft_tech_locked",
                    Component.translatable("tech.noellesroles.sixty_seconds." + recipe.techId())), true);
            return;
        }
        if (recipe.needsPower() && !SixtySecondsPowerSystem.isPowered(level, team)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.craft_no_power"), true);
            return;
        }
        int crafted = 0;
        int want = net.minecraft.util.Mth.clamp(count, 1, 64);
        for (int round = 0; round < want; round++) {
            // 材料校验：先全量检查再统一扣除，避免扣一半（组配料「任意 X」按候选合计）
            SixtySecondsRecipes.Ingredient missing = null;
            for (SixtySecondsRecipes.Ingredient input : recipe.inputs()) {
                if (countMatching(player, input) < input.count()) {
                    missing = input;
                    break;
                }
            }
            if (missing != null) {
                if (crafted == 0) {
                    player.displayClientMessage(Component.translatable(
                            "message.noellesroles.sixty_seconds.craft_missing",
                            missing.displayName(), missing.count()), true);
                    return;
                }
                break;
            }
            for (SixtySecondsRecipes.Ingredient input : recipe.inputs()) {
                consumeMatching(player, input);
            }
            crafted++;
        }
        if (crafted == 0) {
            return;
        }
        // 冶金炉：每件 4 秒制作时间——材料已扣，延迟发放产物
        if (recipe.station() == SixtySecondsRecipes.Station.SMELTER) {
            PENDING_SMELTS.add(new PendingSmelt(player.getUUID(), recipe.id(), crafted,
                    level.getGameTime() + (long) SMELT_TICKS_PER_ITEM * crafted));
            player.playNotifySound(SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.PLAYERS, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.craft_smelting",
                    SixtySecondsRecipes.outputStack(recipe).getHoverName(), 4 * crafted), true);
            return;
        }
        deliver(player, recipe, crafted);
    }

    /**
     * 收集避难所内所有可用容器（玩家背包 + 附近方块容器）。
     * 保险库按队伍归属过滤；其他容器（箱子/木桶/基地箱等）只要在范围内即纳入。
     * 返回的列表第一个元素始终是玩家背包（优先扣除）。
     */
    private static List<Container> findNearbyContainers(ServerPlayer player) {
        List<Container> containers = new ArrayList<>();
        // 玩家背包优先
        containers.add(player.getInventory());

        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        int teamId = SixtySecondsStatsComponent.KEY.get(player).teamId;
        int r = CONTAINER_SCAN_RADIUS;
        int rSqr = r * r;

        // 按 chunk 遍历（比 BlockPos.betweenClosed 更高效）
        int minCX = (playerPos.getX() - r) >> 4;
        int maxCX = (playerPos.getX() + r) >> 4;
        int minCZ = (playerPos.getZ() - r) >> 4;
        int maxCZ = (playerPos.getZ() + r) >> 4;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof Container container)) {
                        continue;
                    }
                    // 跳过功能性方块实体（熔炉、酿造台、投掷器等）
                    if (be instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity
                            || be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity
                            || be instanceof net.minecraft.world.level.block.entity.HopperBlockEntity
                            || be instanceof net.minecraft.world.level.block.entity.DispenserBlockEntity) {
                        continue;
                    }
                    // 检查距离
                    BlockPos bePos = be.getBlockPos();
                    double dx = bePos.getX() + 0.5 - playerPos.getX();
                    double dy = bePos.getY() + 0.5 - playerPos.getY();
                    double dz = bePos.getZ() + 0.5 - playerPos.getZ();
                    if (dx * dx + dy * dy + dz * dz > rSqr) {
                        continue;
                    }
                    // 保险库/基地箱子：检查队伍归属
                    if (be instanceof SixtySecondsVaultBlockEntity vault) {
                        if (vault.ownerTeamId >= 0 && vault.ownerTeamId != teamId) {
                            continue; // 别队的保险库不能取
                        }
                        containers.add(container);
                        continue;
                    }
                    // 原版箱子、木桶、潜影盒等
                    if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
                            || be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity
                            || be instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity) {
                        containers.add(container);
                    }
                }
            }
        }
        return containers;
    }

    /** 所有可用容器中可充当该配料的物品总数（背包 + 避难所容器）。 */
    private static int countMatching(ServerPlayer player, SixtySecondsRecipes.Ingredient input) {
        int have = 0;
        for (Container container : findNearbyContainers(player)) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (input.matches(stack)) {
                    have += stack.getCount();
                }
            }
        }
        return have;
    }

    /**
     * 按配料扣除（优先扣玩家背包，再扣容器；组配料跨候选扣够为止；
     * 调用前须先 countMatching 校验充足）。
     */
    private static void consumeMatching(ServerPlayer player, SixtySecondsRecipes.Ingredient input) {
        int left = input.count();
        List<Container> containers = findNearbyContainers(player);
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize() && left > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (input.matches(stack)) {
                    int take = Math.min(left, stack.getCount());
                    stack.shrink(take);
                    left -= take;
                }
            }
            // 方块实体容器需标记脏数据以同步
            if (container instanceof BlockEntity be) {
                be.setChanged();
            }
        }
    }

    /** 发放产物 + 完成提示。 */
    private static void deliver(ServerPlayer player, SixtySecondsRecipes.Recipe recipe, int crafted) {
        for (int i = 0; i < crafted; i++) {
            ItemStack output = SixtySecondsRecipes.outputStack(recipe);
            if (!player.getInventory().add(output)) {
                player.drop(output, false);
            }
        }
        player.playNotifySound(SoundEvents.VILLAGER_WORK_TOOLSMITH, SoundSource.PLAYERS, 0.8F, 1.1F);
        ItemStack shown = SixtySecondsRecipes.outputStack(recipe);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.craft_done", shown.getHoverName()), true);
    }

    // ── 冶金炉制作队列（每件 4 秒；材料先扣、到点发放）────────────────────

    private static final int SMELT_TICKS_PER_ITEM = 80;

    private record PendingSmelt(java.util.UUID player, String recipeId, int rounds, long finishTick) {
    }

    private static final java.util.List<PendingSmelt> PENDING_SMELTS = new java.util.ArrayList<>();

    /** 服务端逐 tick：到点把冶炼产物发给玩家（掉线则掉在原告知位置无从投递，改为直接丢弃该单）。 */
    public static void tickSmelts(ServerLevel level) {
        if (PENDING_SMELTS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        PENDING_SMELTS.removeIf(pending -> {
            if (now < pending.finishTick()) {
                return false;
            }
            SixtySecondsRecipes.Recipe recipe = SixtySecondsRecipes.byId(pending.recipeId());
            if (recipe != null && level.getPlayerByUUID(pending.player()) instanceof ServerPlayer player) {
                deliver(player, recipe, pending.rounds());
            }
            return true;
        });
    }

    public static void resetSmelts() {
        PENDING_SMELTS.clear();
    }
}
