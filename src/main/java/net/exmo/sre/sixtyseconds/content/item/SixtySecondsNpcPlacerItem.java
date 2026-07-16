package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsNpcEntity;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsNpcSpawner;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 管理员搭图工具：登记 NPC 生成点（模板绝对坐标，写进 {@code SixtySecondsConfig.npcSpawns} 并<b>立即落盘</b>）。
 * <ul>
 *   <li><b>潜行右键空气</b>：循环切换要放的变体（商人 → 军人 → 强盗 → 旅者）。</li>
 *   <li><b>右键方块</b>：在该方块上方登记一个生成点（朝向取玩家当前朝向），并立刻生成一只预览实体。</li>
 *   <li><b>潜行右键方块</b>：删除附近（3 格内）最近的一个已登记生成点。</li>
 * </ul>
 * 做成<b>一把切档工具</b>而非四个刷怪蛋：少 3 份注册/贴图/语言键，且与既有
 * {@code sixty_seconds_area_wand} 的交互习惯一致。
 * <p>选中的档位按玩家记在内存里（重启复位到商人，无所谓——这是搭图工具不是玩法物品）。
 */
public class SixtySecondsNpcPlacerItem extends Item {
    /** 删除生成点时的搜索半径（格）。 */
    private static final double REMOVE_RADIUS = 3.0;

    /** 每个管理员当前选中的变体。 */
    private static final Map<UUID, SixtySecondsNpcEntity.Variant> SELECTED = new HashMap<>();

    public SixtySecondsNpcPlacerItem(Properties properties) {
        super(properties);
    }

    private static boolean isAdmin(Player player) {
        return player.hasPermissions(2) || player.isCreative();
    }

    public static SixtySecondsNpcEntity.Variant selectedOf(Player player) {
        return SELECTED.getOrDefault(player.getUUID(), SixtySecondsNpcEntity.Variant.MERCHANT);
    }

    /** 潜行右键空气：循环切档。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (!isAdmin(player)) {
            noPermission(serverPlayer);
            return InteractionResultHolder.fail(stack);
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        SixtySecondsNpcEntity.Variant[] all = SixtySecondsNpcEntity.Variant.values();
        SixtySecondsNpcEntity.Variant next = all[(selectedOf(player).ordinal() + 1) % all.length];
        SELECTED.put(player.getUUID(), next);
        serverPlayer.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.npc.placer_variant",
                Component.translatable(next.nameKey())).withStyle(ChatFormatting.AQUA), true);
        return InteractionResultHolder.success(stack);
    }

    /** 右键方块：登记（潜行时改为删除最近的生成点）。 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player raw = context.getPlayer();
        if (level.isClientSide || !(raw instanceof ServerPlayer player)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!isAdmin(player)) {
            noPermission(player);
            return InteractionResult.FAIL;
        }
        SixtySecondsConfig config = SixtySecondsConfigStore.load(serverLevel)
                .orElseGet(SixtySecondsConfig::new);
        if (config.npcSpawns == null) {
            config.npcSpawns = new java.util.ArrayList<>();
        }
        BlockPos target = context.getClickedPos().above();
        if (player.isShiftKeyDown()) {
            return remove(serverLevel, player, config, target);
        }
        return place(serverLevel, player, config, target);
    }

    private InteractionResult place(ServerLevel level, ServerPlayer player, SixtySecondsConfig config,
            BlockPos pos) {
        SixtySecondsNpcEntity.Variant variant = selectedOf(player);
        float yaw = player.getYRot();
        SixtySecondsConfig.NpcSpawn spawn = new SixtySecondsConfig.NpcSpawn(
                variant.id, new SixtySecondsConfig.Vec(pos.getX(), pos.getY(), pos.getZ()), yaw);
        config.npcSpawns.add(spawn);
        // 显式落盘：CCA sync 只同步不持久化，配置改动必须自己写文件
        SixtySecondsConfigStore.save(level, config);
        // 立刻生成一只预览，让搭图者当场看到效果（开局时会按配置重新生成）
        SixtySecondsNpcSpawner.spawnAt(level, pos, variant, yaw, spawn.profile, spawn.garrisonRadius, -1);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.npc.placed",
                Component.translatable(variant.nameKey()), pos.getX(), pos.getY(), pos.getZ(),
                config.npcSpawns.size()).withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult remove(ServerLevel level, ServerPlayer player, SixtySecondsConfig config,
            BlockPos pos) {
        SixtySecondsConfig.NpcSpawn nearest = null;
        double best = REMOVE_RADIUS * REMOVE_RADIUS;
        for (SixtySecondsConfig.NpcSpawn spawn : config.npcSpawns) {
            if (spawn.pos == null) {
                continue;
            }
            double distSqr = spawn.pos.toBlockPos().distSqr(pos);
            if (distSqr <= best) {
                best = distSqr;
                nearest = spawn;
            }
        }
        if (nearest == null) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.npc.placer_none_here")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }
        config.npcSpawns.remove(nearest);
        SixtySecondsConfigStore.save(level, config);
        // 顺手清掉那附近的预览实体，免得配置删了人还杵在那儿
        BlockPos at = nearest.pos.toBlockPos();
        for (SixtySecondsNpcEntity npc : level.getEntitiesOfClass(SixtySecondsNpcEntity.class,
                new net.minecraft.world.phys.AABB(at).inflate(2.0))) {
            npc.discard();
        }
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.npc.removed",
                at.getX(), at.getY(), at.getZ(), config.npcSpawns.size())
                .withStyle(ChatFormatting.YELLOW), true);
        return InteractionResult.SUCCESS;
    }

    private static void noPermission(ServerPlayer player) {
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.wand.no_permission")
                .withStyle(ChatFormatting.RED), true);
    }
}
