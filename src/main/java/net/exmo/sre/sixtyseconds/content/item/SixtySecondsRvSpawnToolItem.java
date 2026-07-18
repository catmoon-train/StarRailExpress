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
 * 管理员搭图工具：登记<b>房车刷新点</b>（每队一个，按登记顺序对应队伍序号）。
 * <ul>
 *   <li><b>右键方块</b>：把该方块<b>上方</b>一格记为下一支队伍的房车刷新点，追加进
 *       {@link SixtySecondsConfig#rvSpawnPoints}（立刻落盘）。</li>
 *   <li><b>潜行右键</b>：清空所有房车刷新点（回退到「住宅旁自动找点」）。</li>
 * </ul>
 * 也可用 {@code /sre:60s_area rv} 系列指令查看/删除/开关。
 */
public class SixtySecondsRvSpawnToolItem extends Item {

    private static final String LANG = "message.noellesroles.sixty_seconds.rv_tool.";

    public SixtySecondsRvSpawnToolItem(Properties properties) {
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
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.wand.no_permission")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        SixtySecondsConfig config = SixtySecondsConfigStore.current(serverLevel)
                .orElseGet(SixtySecondsConfig::new);
        if (config.rvSpawnPoints == null) {
            config.rvSpawnPoints = new java.util.ArrayList<>();
        }
        if (player.isShiftKeyDown()) {
            int cleared = config.rvSpawnPoints.size();
            config.rvSpawnPoints.clear();
            SixtySecondsConfigStore.save(serverLevel, config);
            player.displayClientMessage(Component.translatable(LANG + "cleared", cleared)
                    .withStyle(ChatFormatting.YELLOW), false);
            return InteractionResult.SUCCESS;
        }
        BlockPos spot = context.getClickedPos().relative(context.getClickedFace());
        config.rvSpawnPoints.add(new SixtySecondsConfig.Vec(spot.getX(), spot.getY(), spot.getZ()));
        SixtySecondsConfigStore.save(serverLevel, config);
        int teamNo = config.rvSpawnPoints.size();
        player.displayClientMessage(Component.translatable(LANG + "added", teamNo,
                spot.getX(), spot.getY(), spot.getZ()).withStyle(ChatFormatting.GREEN), false);
        if (!config.rvEnabled) {
            player.displayClientMessage(Component.translatable(LANG + "not_enabled")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return InteractionResult.SUCCESS;
    }
}
