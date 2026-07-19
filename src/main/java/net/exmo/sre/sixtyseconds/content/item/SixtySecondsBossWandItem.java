package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * 管理员搭图工具：<b>Boss 刷新点绑定魔杖</b>——在世界任意位置右键方块登记一个 Boss 刷新点，
 * 写入 {@code config.bossSpawnPoints}（落盘）。4-5 星区域固定 Boss / 1-5 星「伤害 Boss」生成时，
 * 优先取落在目标区域内的已登记刷新点；无则在该区域随机选合理落点。
 * <p>
 * 操作：
 * <ul>
 *   <li><b>右键</b>方块 = 在该位置登记 Boss 刷新点并落盘。</li>
 *   <li><b>潜行右键</b>方块 = 移除距离最近（≤8 格）的已登记刷新点。</li>
 * </ul>
 * 只对管理员/创造模式生效。清理用 {@code /sre:60s boss_spawn list|remove|clear}。
 */
public class SixtySecondsBossWandItem extends Item {
    private static final String LANG = "message.noellesroles.sixty_seconds.boss_wand.";

    public SixtySecondsBossWandItem(Properties properties) {
        super(properties);
    }

    private static boolean isAdmin(Player player) {
        return player.hasPermissions(2) || player.isCreative();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player raw = context.getPlayer();
        if (level.isClientSide || !(raw instanceof ServerPlayer player)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!isAdmin(player)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.wand.no_permission")
                            .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().immutable();
        SixtySecondsConfig config = SixtySecondsConfigStore.current(serverLevel).orElseGet(SixtySecondsConfig::new);
        if (player.isShiftKeyDown()) {
            // 移除最近的刷新点（≤8 格）
            SixtySecondsConfig.Vec nearest = null;
            double bestDist = 8 * 8;
            if (config.bossSpawnPoints != null) {
                for (SixtySecondsConfig.Vec v : config.bossSpawnPoints) {
                    if (v == null) {
                        continue;
                    }
                    double d = (v.x - pos.getX()) * (v.x - pos.getX())
                            + (v.y - pos.getY()) * (v.y - pos.getY())
                            + (v.z - pos.getZ()) * (v.z - pos.getZ());
                    if (d <= bestDist) {
                        bestDist = d;
                        nearest = v;
                    }
                }
            }
            if (nearest == null) {
                player.displayClientMessage(
                        Component.translatable(LANG + "no_nearby").withStyle(ChatFormatting.YELLOW), true);
                return InteractionResult.SUCCESS;
            }
            config.bossSpawnPoints.remove(nearest);
            SixtySecondsConfigStore.save(serverLevel, config);
            player.displayClientMessage(
                    Component.translatable(LANG + "removed", nearest.x, nearest.y, nearest.z)
                            .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.SUCCESS;
        }
        if (config.bossSpawnPoints == null) {
            config.bossSpawnPoints = new java.util.ArrayList<>();
        }
        // 避免重复登记同位置
        for (SixtySecondsConfig.Vec v : config.bossSpawnPoints) {
            if (v != null && v.x == pos.getX() && v.y == pos.getY() && v.z == pos.getZ()) {
                player.displayClientMessage(
                        Component.translatable(LANG + "exists").withStyle(ChatFormatting.YELLOW), true);
                return InteractionResult.SUCCESS;
            }
        }
        config.bossSpawnPoints.add(new SixtySecondsConfig.Vec(pos.getX(), pos.getY(), pos.getZ()));
        SixtySecondsConfigStore.save(serverLevel, config);
        player.displayClientMessage(
                Component.translatable(LANG + "added", pos.getX(), pos.getY(), pos.getZ(),
                        config.bossSpawnPoints.size()).withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.SUCCESS;
    }
}
