package net.exmo.sre.sixtyseconds.arena;

import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.content.block.ShelterDoorBlock;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.init.ModEffects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 搜索区进出：存返回点 → 传送到本队搜索区 → 每 tick 限制在盒内 → 送回（参照 {@code WarlockDomainManager}）。
 * P0 骨架：仅完成传送 + 限制 + 送回；搜索区内的物资箱/掉落业务见 {@code SupplyBox}（后续批次）。
 */
public final class SixtySecondsSearchZones {
    public static final int EXPLORE_INVIS_TICKS = 20 * 20;         // 出门隐身 20s
    public static final int RETURN_COOLDOWN_TICKS = 20 * 45;       // 归来冷却 45s（白天）
    public static final int RETURN_COOLDOWN_NIGHT_TICKS = 20 * 5;  // 晚上出门只锁 5s（夜袭凶险，放人快速撤回）

    private static final Map<UUID, ReturnPos> RETURNS = new HashMap<>();

    private SixtySecondsSearchZones() {
    }

    /** 出门探索（未绑定门/旧调用）：用本队默认搜索区。 */
    public static void enter(ServerPlayer player) {
        enter(player, null);
    }

    /**
     * 出门探索：传送到 {@code doorPos} 绑定的专属探索区（未绑定则用本队默认搜索区），隐身 15s；
     * 归来需等冷却——白天 45s、晚上仅 5s（夜里探索区有夜袭怪，允许快速撤回）。
     * 每扇门可绑定独立探索区（出生点 + 限制盒），由绑定工具在 {@code searchDoorBindings} 里配置。
     */
    public static void enter(ServerPlayer player, BlockPos doorPos) {
        ServerLevel level = player.serverLevel();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        SixtySecondsState.TeamData team = data.teams.get(stats.teamId);
        if (team == null) {
            player.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.no_search_zone"), true);
            return;
        }
        // 出门 = 直接落在<b>门外那格</b>（穿过门到玩家当前一侧的对面），不再传送到独立探索区；
        // 出门后整片世界自由活动，没有任何限制盒。无门坐标（旧无参调用）时就近取玩家脚下安全点。
        BlockPos safe = doorPos != null ? doorOutsideSpot(level, doorPos, player.blockPosition())
                : findSafeSpot(level, player.blockPosition());
        // confineBox 传 null：SearchZones.tick 不再拽人——这是「空气墙 / 被拉回探索区中心」的根因所在。
        RETURNS.put(player.getUUID(), new ReturnPos(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), null, safe.immutable()));
        player.teleportTo(level, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        long now = level.getGameTime();
        boolean night = net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.isNight(data, now);
        stats.exploreCooldownEndTick = now + (night ? RETURN_COOLDOWN_NIGHT_TICKS : RETURN_COOLDOWN_TICKS);
        stats.sync();
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, EXPLORE_INVIS_TICKS, 0, false, false, true));
        // 出门探索后移除闯入者标记——已离开别人的避难所
        player.removeEffect(ModEffects.BREAK_IN_INTRUDER);
        // 出门后无探索区盒（自由活动）：清掉区域地图限制盒，小地图退回全图 playArea。
        net.exmo.sre.sixtyseconds.network.SixtySecondsMapZoneS2CPacket.sendClear(player);
        // 危险等级提示：等级越高稀有物越常见、但游荡怪更多更强（SixtySecondsAreaLevels/PveSystem）
        int areaLevel = net.exmo.sre.sixtyseconds.logic.SixtySecondsAreaLevels.levelAt(level, safe);
        player.displayClientMessage(Component
                .translatable("message.noellesroles.sixty_seconds.area_level_enter", areaLevel)
                .withStyle(areaLevel >= 4 ? net.minecraft.ChatFormatting.RED
                        : areaLevel >= 2 ? net.minecraft.ChatFormatting.YELLOW
                                : net.minecraft.ChatFormatting.GREEN), false);
    }

    /** 把玩家送回进入搜索区前的位置（受归来冷却限制：白天 45s / 晚上 5s）。 */
    public static void returnPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        long now = level.getGameTime();
        if (now < stats.exploreCooldownEndTick) {
            int seconds = (int) Math.ceil((stats.exploreCooldownEndTick - now) / 20.0D);
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.return_cooldown", seconds), true);
            return;
        }
        ReturnPos pos = RETURNS.remove(player.getUUID());
        if (pos != null) {
            SixtySecondsState.TeamData team = SixtySecondsState.get(level).teams
                    .get(SixtySecondsStatsComponent.KEY.get(player).teamId);
            // 「回家」= 回<b>本队</b>避难所出生点，而非「出门前的原坐标」——玩家撬门闯入别队后会站在别队避难所里，
            // 再从别队门出门探索，原坐标就落在别队避难所内，导致「回家却回到别人家」（本次要修的 BUG）。
            // 无队伍信息时才兜底用原坐标。
            BlockPos home = team != null && team.shelterSpawn != null
                    ? team.shelterSpawn
                    : BlockPos.containing(pos.x, pos.y, pos.z);
            BlockPos safe = findSafeSpot(level, home); // 落点安全校正，防落进方块窒息
            player.teleportTo(level, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
            // 区域地图切回本队避难所
            if (team != null) {
                net.exmo.sre.sixtyseconds.network.SixtySecondsMapZoneS2CPacket.send(
                        player, team.shelterBox, team.shelterSpawn, true);
                // 回家时清除避难所内的怪物
                net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem.clearShelterMobs(level, team.shelterBox);
            }
            // 回家后移除闯入者标记（破门闯入的 PvP 豁免效果）
            player.removeEffect(ModEffects.BREAK_IN_INTRUDER);
        }
    }

    public static boolean isInSearchZone(ServerPlayer player) {
        return RETURNS.containsKey(player.getUUID());
    }

    /** 清除玩家的搜索区状态（闯入者离开别队避难所用——解除 RETURNS 中的别队避难所盒限制）。 */
    public static void clearReturnEntry(ServerPlayer player) {
        RETURNS.remove(player.getUUID());
    }

    /** 本次出门的探索区限制盒（PVE 游荡怪落点约束用）；不在探索区返回 null。 */
    public static AABB confineBox(ServerPlayer player) {
        ReturnPos pos = RETURNS.get(player.getUUID());
        return pos == null ? null : pos.confineBox;
    }

    /** 本次出门的入口落点（探索区内、门口）；不在搜索区时返回 null。回家门校验用。 */
    public static BlockPos entrySpawn(ServerPlayer player) {
        ReturnPos pos = RETURNS.get(player.getUUID());
        return pos == null ? null : pos.entrySpawn;
    }

    /**
     * 更换在外玩家的活动限制盒与入口点（海岛扬帆用：出门后从探索区转移到群岛海域），
     * 返回点/冷却均保持不变——「返回住所」仍走原流程。玩家不在外时无操作。
     */
    public static void updateConfine(ServerPlayer player, AABB box, BlockPos entrySpawn) {
        ReturnPos pos = RETURNS.get(player.getUUID());
        if (pos == null) {
            return;
        }
        RETURNS.put(player.getUUID(), new ReturnPos(pos.x, pos.y, pos.z, pos.yaw, pos.pitch,
                box, entrySpawn.immutable()));
    }

    /** 无视归来冷却强制送回住所（海岛模式关闭时清场用）。 */
    public static void forceReturn(ServerPlayer player) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        stats.exploreCooldownEndTick = 0;
        stats.sync();
        returnPlayer(player);
    }

    /**
     * 传送落点安全校正：目标格或头顶被方块（避难所门/墙）占住时，就近找一个双格净空的落脚点
     * （优先有实心地面的格子），找不到则原样返回——防止玩家被传进方块里窒息。
     * 公开给闯入（{@code SixtySecondsBreakIn}）/拜访/做客离开等所有进出避难所的传送共用。
     */
    public static BlockPos findSafeSpot(ServerLevel level, BlockPos target) {
        if (isClear(level, target)) {
            return target;
        }
        // 放大搜索窗口（±4 水平、上探至 +8、下探至 -3）：优先「净空且脚下有支撑」的落脚点，其次任意净空点。
        // 旧窗口（±2 水平、dy∈{0,1,-1,2}）太小，出生点若埋在墙/门里且周围紧凑时会找不到而原样返回 → 撬锁/撬棍闯入
        // 落进方块窒息（本次要修的 BUG）。所有进出避难所的传送（闯入/回家/出门探索/拜访离开）共用此校正。
        BlockPos fallback = null;
        int[] dys = { 0, 1, 2, -1, 3, 4, -2, 5, 6, -3, 7, 8 };
        for (int dy : dys) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = target.offset(dx, dy, dz);
                    if (!isClear(level, pos)) {
                        continue;
                    }
                    if (!level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
                        return pos; // 净空且脚下有支撑，最优
                    }
                    if (fallback == null) {
                        fallback = pos;
                    }
                }
            }
        }
        return fallback != null ? fallback : target;
    }

    /** 脚部与头部两格均无碰撞体。 */
    private static boolean isClear(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    /**
     * 门外落脚点：从门穿到 {@code inside}（玩家出门前所在侧）的对面 1~3 格，就近找双格净空点。
     * 优先按门朝向（{@link ShelterDoorBlock#FACING}）取「远离 inside」的一侧；读不到朝向时退回
     * 水平四向里离 inside 最远者。全被墙堵死时兜底返回门口的 {@link #findSafeSpot}。
     */
    private static BlockPos doorOutsideSpot(ServerLevel level, BlockPos door, BlockPos inside) {
        List<Direction> dirs = new ArrayList<>();
        var state = level.getBlockState(door);
        if (state.hasProperty(ShelterDoorBlock.FACING)) {
            Direction facing = state.getValue(ShelterDoorBlock.FACING);
            dirs.add(door.relative(facing, 2).distSqr(inside) >= door.relative(facing.getOpposite(), 2).distSqr(inside)
                    ? facing : facing.getOpposite());
        }
        // 兜底方向：按「离 inside 更远」排序的水平四向（读不到门朝向 / 首选方向被堵时用）
        Direction[] horizontals = Direction.Plane.HORIZONTAL.stream()
                .sorted(java.util.Comparator.comparingDouble(d -> -door.relative(d, 2).distSqr(inside)))
                .toArray(Direction[]::new);
        for (Direction d : horizontals) {
            if (!dirs.contains(d)) {
                dirs.add(d);
            }
        }
        for (Direction d : dirs) {
            for (int dist = 1; dist <= 3; dist++) {
                BlockPos safe = findSafeSpot(level, door.relative(d, dist));
                if (isClear(level, safe)) {
                    return safe;
                }
            }
        }
        return findSafeSpot(level, door);
    }

    /**
     * 出门探索不再有任何活动限制盒（confineBox 恒为 null）——玩家出门后可走到天涯海角。
     * 本方法保留为空实现：{@code RETURNS} 仍记录返回点/入口供「返回住所」与门匹配使用，
     * 只是不再每 tick 用 {@code limitPlayerToBox} 拽人（删除「空气墙 / 被拉回探索区中心」的根因）。
     */
    public static void tick(ServerLevel level) {
    }

    public static void reset(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            ReturnPos pos = RETURNS.get(player.getUUID());
            if (pos != null) {
                player.teleportTo(level, pos.x, pos.y, pos.z, pos.yaw, pos.pitch);
            }
        }
        RETURNS.clear();
    }

    private record ReturnPos(double x, double y, double z, float yaw, float pitch, AABB confineBox,
            BlockPos entrySpawn) {
    }
}
