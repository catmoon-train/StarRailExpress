package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * 合成台交互：60s 模式内右键 书桌(工作台/讲台)/灶台(熔炉族/营火)/浴缸(炼药锅) →
 * 打开该站配方界面（拦截原版 GUI）；C2S 合成请求在服务端校验 科技/供电/材料 后成交。
 */
public final class SixtySecondsStations {
    private static final double CRAFT_RANGE_SQR = 6.0 * 6.0;

    private SixtySecondsStations() {
    }

    public static void register() {
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

    /** C2S 合成请求。 */
    public static void handleCraft(ServerPlayer player, String recipeId, BlockPos stationPos) {
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
        // 材料校验：先全量检查再统一扣除，避免扣一半
        for (SixtySecondsRecipes.Ingredient input : recipe.inputs()) {
            int have = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(input.item())) {
                    have += stack.getCount();
                }
            }
            if (have < input.count()) {
                player.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.craft_missing",
                        input.item().getDescription(), input.count()), true);
                return;
            }
        }
        for (SixtySecondsRecipes.Ingredient input : recipe.inputs()) {
            SixtySecondsTechTree.consume(player, input.item(), input.count());
        }
        ItemStack output = SixtySecondsRecipes.outputStack(recipe);
        if (!player.getInventory().add(output)) {
            player.drop(output, false);
        }
        player.playNotifySound(SoundEvents.VILLAGER_WORK_TOOLSMITH, SoundSource.PLAYERS, 0.8F, 1.1F);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.craft_done",
                SixtySecondsRecipes.outputStack(recipe).getHoverName()), true);
    }
}
