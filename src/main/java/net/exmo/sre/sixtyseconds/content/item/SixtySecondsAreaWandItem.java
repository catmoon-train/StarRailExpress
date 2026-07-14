package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig.DoorBinding;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig.Vec;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.exmo.sre.sixtyseconds.content.block.ShelterDoorBlock;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 管理员搭图工具：把一扇避难所 SEARCH 门绑定到一块独立的探索区（每个避难所独立对应一个探索区的门）。
 * <p>
 * 三步绑定（均右键，坐标记为模板绝对坐标，开局按队叠加网格偏移克隆）：
 * <ol>
 *   <li>右键一扇<b>避难所门</b> → 选中它为待绑定门（由 {@link ShelterDoorBlock} 检测手持本工具时调 {@link #selectDoor}）。</li>
 *   <li>右键探索区里的一个方块 → 设为<b>出生点 / 盒角 A</b>（玩家出门落在这里）。</li>
 *   <li>再右键对角方块 → 设为<b>盒角 B</b> 并<b>提交绑定</b>，写入 {@code sixty_seconds_config.json}。</li>
 * </ol>
 * 潜行右键任意方块 = 取消当前选择。绑定后该门用其专属探索区（出生点 + 限制盒），未绑定的门回退到全局搜索区。
 */
public class SixtySecondsAreaWandItem extends Item {
    /** 快速绑定的水平半径（盒 = 出生点 ±QUICK_RADIUS，垂直 -4..+16）。 */
    private static final int QUICK_RADIUS = 24;

    /** 待绑定门（模板绝对坐标），按玩家。 */
    private static final Map<UUID, BlockPos> PENDING_DOOR = new HashMap<>();
    /** 已设的盒角 A / 出生点，按玩家。 */
    private static final Map<UUID, BlockPos> PENDING_CORNER_A = new HashMap<>();

    public SixtySecondsAreaWandItem(Properties properties) {
        super(properties);
    }

    private static boolean isAdmin(Player player) {
        return player.hasPermissions(2) || player.isCreative();
    }

    /** 由门方块在「手持本工具右键门」时调用：记录待绑定门，等待两次角点右键。 */
    public static void selectDoor(ServerPlayer player, BlockPos doorPos) {
        if (!isAdmin(player)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.wand.no_permission")
                            .withStyle(ChatFormatting.RED), true);
            return;
        }
        PENDING_DOOR.put(player.getUUID(), doorPos.immutable());
        PENDING_CORNER_A.remove(player.getUUID());
        player.displayClientMessage(
                Component.translatable("message.noellesroles.sixty_seconds.wand.door_selected",
                        doorPos.getX(), doorPos.getY(), doorPos.getZ()).withStyle(ChatFormatting.AQUA), true);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player raw = context.getPlayer();
        if (level.isClientSide || !(raw instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!isAdmin(player)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.wand.no_permission")
                            .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        UUID id = player.getUUID();
        BlockPos pos = context.getClickedPos();

        // 门由门方块处理（selectDoor），这里只处理角点
        if (serverLevel.getBlockState(pos).getBlock() instanceof ShelterDoorBlock) {
            return InteractionResult.SUCCESS;
        }
        // 潜行右键：已选门 = 快速绑定（一步成型）；未选门 = 取消/清状态
        if (player.isShiftKeyDown()) {
            BlockPos quickDoor = PENDING_DOOR.get(id);
            if (quickDoor != null) {
                // 快速绑定：出生点=点击处上方，盒=以出生点为中心 水平±QUICK_RADIUS、垂直 -4..+16
                BlockPos spawn = pos.above();
                BlockPos boxMin = spawn.offset(-QUICK_RADIUS, -4, -QUICK_RADIUS);
                BlockPos boxMax = spawn.offset(QUICK_RADIUS, 16, QUICK_RADIUS);
                commitBinding(serverLevel, player, quickDoor, spawn, boxMin, boxMax);
                PENDING_DOOR.remove(id);
                PENDING_CORNER_A.remove(id);
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.sixty_seconds.wand.quick_bound",
                                QUICK_RADIUS).withStyle(ChatFormatting.GREEN), true);
                return InteractionResult.SUCCESS;
            }
            PENDING_CORNER_A.remove(id);
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.wand.cancelled")
                            .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        BlockPos door = PENDING_DOOR.get(id);
        if (door == null) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.wand.need_door")
                            .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.SUCCESS;
        }
        BlockPos cornerA = PENDING_CORNER_A.get(id);
        if (cornerA == null) {
            PENDING_CORNER_A.put(id, pos.immutable());
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.wand.corner_a",
                            pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.SUCCESS;
        }
        commitBinding(serverLevel, player, door, cornerA, pos);
        PENDING_DOOR.remove(id);
        PENDING_CORNER_A.remove(id);
        return InteractionResult.SUCCESS;
    }

    /** 提交一条门→探索区绑定并落盘（同门已存在则覆盖）。出生点 = 盒角 A（精确两角模式）。 */
    private static void commitBinding(ServerLevel level, ServerPlayer player, BlockPos door, BlockPos a, BlockPos b) {
        BlockPos boxMin = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()));
        BlockPos boxMax = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
        commitBinding(level, player, door, a, boxMin, boxMax);
    }

    /** 提交一条门→探索区绑定并落盘：显式指定出生点与盒两角（快速绑定/精确模式共用）。 */
    private static void commitBinding(ServerLevel level, ServerPlayer player, BlockPos door, BlockPos spawn,
            BlockPos boxMin, BlockPos boxMax) {
        SixtySecondsConfig config = SixtySecondsConfigStore.current(level).orElseGet(SixtySecondsConfig::new);
        if (config.searchDoorBindings == null) {
            config.searchDoorBindings = new ArrayList<>();
        }
        config.searchDoorBindings.removeIf(bd -> bd.door != null
                && bd.door.x == door.getX() && bd.door.y == door.getY() && bd.door.z == door.getZ());
        config.searchDoorBindings.add(new DoorBinding(
                new Vec(door.getX(), door.getY(), door.getZ()),
                new Vec(boxMin.getX(), boxMin.getY(), boxMin.getZ()),
                new Vec(boxMax.getX(), boxMax.getY(), boxMax.getZ()),
                new Vec(spawn.getX(), spawn.getY(), spawn.getZ())));
        SixtySecondsConfigStore.save(level, config);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.sixty_seconds.wand.bound",
                        config.searchDoorBindings.size()).withStyle(ChatFormatting.GREEN), true);
    }
}
