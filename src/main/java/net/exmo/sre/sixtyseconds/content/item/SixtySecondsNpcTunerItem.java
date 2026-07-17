package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsNpcEntity;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsNpcSpawner;
import net.exmo.sre.sixtyseconds.shop.SixtySecondsShopStore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 管理员搭图工具：<b>调校</b>已登记的 NPC 生成点——放置器（{@link SixtySecondsNpcPlacerItem}）只能加/删，
 * 改档案与半径原先得手改 JSON 或敲 {@code /sre:60s npc profile|radius}，本工具让它变成指哪调哪。
 * <ul>
 *   <li><b>右键生成点附近</b>：切换该点的商人货架档案（在 {@code sixty_seconds_npc_shop.json} 的档案名之间循环）。</li>
 *   <li><b>潜行右键</b>：驻守半径 +2（到 {@link #MAX_RADIUS} 回绕到 {@link #MIN_RADIUS}）。</li>
 *   <li>两种操作都会<b>立刻落盘</b>并重放该点的预览立牌，当场看到结果。</li>
 * </ul>
 * 只对<b>已登记</b>的点生效（{@link #SEARCH_RADIUS} 格内最近的一个）；附近没有点就给提示。
 */
public class SixtySecondsNpcTunerItem extends Item {
    /** 找生成点的搜索半径（格）。 */
    private static final double SEARCH_RADIUS = 4.0;
    private static final int MIN_RADIUS = 2;
    private static final int MAX_RADIUS = 24;
    private static final int RADIUS_STEP = 2;

    public SixtySecondsNpcTunerItem(Properties properties) {
        super(properties);
    }

    private static boolean isAdmin(Player player) {
        return player.hasPermissions(2) || player.isCreative();
    }

    /** 右键空气：什么也不做（避免误触改配置）；实际操作都要对着方块/生成点。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
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
        BlockPos target = context.getClickedPos().above();
        SixtySecondsConfig.NpcSpawn spawn = nearest(config, target);
        if (spawn == null) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.npc.placer_none_here")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            cycleRadius(serverLevel, player, config, spawn);
        } else {
            cycleProfile(serverLevel, player, config, spawn);
        }
        return InteractionResult.SUCCESS;
    }

    /** {@link #SEARCH_RADIUS} 内最近的已登记生成点。 */
    private static SixtySecondsConfig.NpcSpawn nearest(SixtySecondsConfig config, BlockPos pos) {
        if (config.npcSpawns == null) {
            return null;
        }
        SixtySecondsConfig.NpcSpawn best = null;
        double bestDist = SEARCH_RADIUS * SEARCH_RADIUS;
        for (SixtySecondsConfig.NpcSpawn spawn : config.npcSpawns) {
            if (spawn.pos == null) {
                continue;
            }
            double distSqr = spawn.pos.toBlockPos().distSqr(pos);
            if (distSqr <= bestDist) {
                bestDist = distSqr;
                best = spawn;
            }
        }
        return best;
    }

    /** 在商店档案名之间循环（档案表为空时退回 default）。 */
    private static void cycleProfile(ServerLevel level, ServerPlayer player, SixtySecondsConfig config,
            SixtySecondsConfig.NpcSpawn spawn) {
        List<String> profiles = SixtySecondsShopStore.get(level).profileNames();
        String next = "default";
        if (!profiles.isEmpty()) {
            int index = profiles.indexOf(spawn.profile);
            next = profiles.get((index + 1) % profiles.size());
        }
        spawn.profile = next;
        SixtySecondsConfigStore.save(level, config);
        refreshPreview(level, spawn);
        String shown = next;
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.npc.tuner_profile", shown)
                .withStyle(ChatFormatting.AQUA), true);
    }

    private static void cycleRadius(ServerLevel level, ServerPlayer player, SixtySecondsConfig config,
            SixtySecondsConfig.NpcSpawn spawn) {
        int next = spawn.garrisonRadius + RADIUS_STEP;
        if (next > MAX_RADIUS) {
            next = MIN_RADIUS;
        }
        spawn.garrisonRadius = next;
        SixtySecondsConfigStore.save(level, config);
        refreshPreview(level, spawn);
        int shown = next;
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.npc.tuner_radius", shown)
                .withStyle(ChatFormatting.AQUA), true);
    }

    /** 重放该点的预览立牌：清掉旧的再按新参数放一只，改完当场可见。 */
    private static void refreshPreview(ServerLevel level, SixtySecondsConfig.NpcSpawn spawn) {
        BlockPos at = spawn.pos.toBlockPos();
        for (SixtySecondsNpcEntity npc : level.getEntitiesOfClass(SixtySecondsNpcEntity.class,
                new net.minecraft.world.phys.AABB(at).inflate(2.0), SixtySecondsNpcEntity::isEditorPreview)) {
            npc.discard();
        }
        SixtySecondsNpcSpawner.spawnPreview(level, at,
                SixtySecondsNpcEntity.Variant.byId(spawn.variant), spawn.yaw, spawn.profile,
                spawn.garrisonRadius);
    }
}
