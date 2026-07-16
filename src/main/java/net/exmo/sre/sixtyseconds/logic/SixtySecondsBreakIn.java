package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.content.item.SixtySecondsBreakInItem;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 撬棍/撬锁器闯入别队避难所：
 * <ul>
 *   <li><b>撬棍</b>（alarms=true）：强行闯入并<b>触发目标队报警</b>；门锁可挡。</li>
 *   <li><b>撬锁器</b>（alarms=false）：<b>潜行进入不报警</b>；可能触发门陷阱。</li>
 * </ul>
 * 流程：在探索区<b>对着别队的避难所门</b>右键 → 统一门菜单（{@code SixtySecondsDoorMenu}）给出
 * 「强闯 / 潜入 / 查看门情报」选项 → C2S 回传 → {@link #executeAtDoor} 服务端按门定位目标队
 * （{@link #teamByDoor}）、从背包取<b>等级最高</b>的对应工具重校验后<b>消耗</b>并把玩家
 * <b>安全落点</b>传进目标队避难所内（直传 shelterSpawn 落点若在门/墙体方块里会窒息掉血倒地死——
 * 与 {@code SixtySecondsSearchZones.enter} 的落点校正同款修法）。
 * <p>旧流程（物品右键任意位置开选队界面远程闯入）已废弃：闯入必须走到目标家门口。
 */
public final class SixtySecondsBreakIn {
    /** 门→队伍匹配容差：returnDoorPos 2 格（门体两格高）；searchZoneSpawn 出口落点 5 格。 */
    private static final int DOOR_MATCH_DIST_SQR = 2 * 2;
    private static final int SPAWN_MATCH_DIST_SQR = 5 * 5;

    private SixtySecondsBreakIn() {
    }

    /**
     * 前两天（新手保护期）是否禁止<b>进入别人家</b>（破门闯入 / 拜访进入避难所共用）。
     * {@code dayNumber<=2} 覆盖准备阶段(0)与第 1、2 天；第 3 天起才允许进别人家。
     */
    public static boolean isHomeEntryLocked(SixtySecondsState.Data data) {
        return data.dayNumber <= 2;
    }

    /**
     * 该队是否是玩家<b>自己家</b>：teamId 相同<b>或</b>玩家在其成员名单里。
     * 双重判定——玩家 {@code teamId} 偶发未同步/为 -1（重连未恢复等）时，单看 teamId 会把自己家当成别队，
     * 导致「撬自己家的门」；成员名单是稳定的归属真值，兜底堵住。
     */
    private static boolean isOwnTeam(SixtySecondsState.TeamData team, ServerPlayer player, int myTeam) {
        return team.teamId == myTeam || team.members.contains(player.getUUID());
    }

    /**
     * 按门定位其所属的<b>别队</b>：优先出口门绑定 {@code returnDoorPos}（2 格容差），
     * 其次共享探索区的出口落点 {@code searchZoneSpawn}（5 格容差，出口点就在各家门旁）。
     * 自己队 / 无避难所的队不算目标；多队命中取最近者。找不到返回 null。
     */
    public static SixtySecondsState.TeamData teamByDoor(SixtySecondsState.Data data, ServerPlayer player,
            BlockPos pos) {
        int myTeam = SixtySecondsStatsComponent.KEY.get(player).teamId;
        SixtySecondsState.TeamData best = null;
        double bestDist = Double.MAX_VALUE;
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (isOwnTeam(team, player, myTeam) || team.shelterSpawn == null || team.shelterBox == null) {
                continue;
            }
            double dist = Double.MAX_VALUE;
            if (team.returnDoorPos != null && pos.distSqr(team.returnDoorPos) <= DOOR_MATCH_DIST_SQR) {
                dist = pos.distSqr(team.returnDoorPos);
            } else if (team.searchZoneSpawn != null && pos.distSqr(team.searchZoneSpawn) <= SPAWN_MATCH_DIST_SQR) {
                dist = pos.distSqr(team.searchZoneSpawn);
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = team;
            }
        }
        return best;
    }

    /** 背包内指定类型（报警=撬棍/潜行=撬锁器）中<b>等级最高</b>的一件；没有返回 null。 */
    public static ItemStack findBestTool(ServerPlayer player, boolean alarms) {
        ItemStack best = null;
        int bestTier = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof SixtySecondsBreakInItem item && item.alarms() == alarms
                    && item.tier() > bestTier) {
                bestTier = item.tier();
                best = stack;
            }
        }
        return best;
    }

    /** 门菜单「查看门情报」：报门等级/耐久/门锁状态（门陷阱是暗手，不透露）。 */
    public static void inspectDoor(ServerPlayer player, BlockPos doorPos) {
        ServerLevel level = player.serverLevel();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        SixtySecondsState.TeamData target = teamByDoor(data, player, doorPos);
        if (target == null) {
            return;
        }
        Component lock = Component.translatable(target.doorLockActive(level.getGameTime())
                ? "message.noellesroles.sixty_seconds.breakin_inspect_locked"
                : "message.noellesroles.sixty_seconds.breakin_inspect_unlocked");
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.breakin_inspect", target.teamId + 1,
                target.doorLevel, target.doorHp, target.doorMaxHp, lock), false);
    }

    /**
     * 门菜单「强闯/潜入」回传：按门定位目标队（{@link #teamByDoor}，防伪造包指定任意队远程闯入），
     * 从背包取<b>等级最高</b>的对应工具重校验（等级/报警属性以服务端当下持有物为准），
     * 成功则消耗 1 个并安全传送进目标队避难所。
     */
    public static void executeAtDoor(ServerPlayer player, BlockPos doorPos, boolean alarms) {
        if (SixtySecondsVisiting.isVisiting(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        // 前两天新手保护期：不许破门闯入（服务端权威校验，防伪造包绕过）
        if (isHomeEntryLocked(data)) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.breakin_too_early").withStyle(ChatFormatting.RED), true);
            return;
        }
        SixtySecondsState.TeamData target = teamByDoor(data, player, doorPos);
        if (target == null) {
            return;
        }
        ItemStack stack = findBestTool(player, alarms);
        if (stack == null || !(stack.getItem() instanceof SixtySecondsBreakInItem item)) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.breakin_no_tool").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (target.doorLevel > item.tier()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.breakin_door_too_high"), true);
            return;
        }
        long now = level.getGameTime();
        // 门锁：1 级只挡撬棍（alarms=true）；2/3 级（强化/阻击门锁）连开锁器一起挡
        if (target.doorLockActive(now) && (item.alarms() || target.doorLockTier >= 2)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.breakin_door_locked")
                            .withStyle(ChatFormatting.RED), true);
            return;
        }
        // 落点安全校正前先确保目标区块已加载——未加载时 getBlockState 返回空气假值，
        // findSafeSpot/hasSafeShelterLanding 会误判为安全落点，传送后区块加载才露出真地形，导致
        // 被卡进方块窒息或掉进虚空弹回世界出生点（「撬门被传送到未知地方」根因）。
        level.getChunk(target.shelterSpawn);
        // 落点安全校正 + 校验（须在消耗物品/触发陷阱之前）：shelterSpawn 可能在门/墙体里（会窒息），
        // 更糟的是该「房间」可能根本没建成庇护所、出生点悬在虚空——传过去会掉进虚空摔死，死亡再把玩家
        // 弹回世界出生点（「潜入没有庇护所的房间传到世界边境而死」根因）。落点脚下无地面=没有真正的庇护所，直接禁止。
        BlockPos safe = SixtySecondsSearchZones.findSafeSpot(level, target.shelterSpawn);
        // 落点必须仍在目标队避难所盒内——findSafeSpot 可能搜到盒外，传送过去完全不认识
        if (!target.shelterBox.contains(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5)
                || !hasSafeShelterLanding(level, safe)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.breakin_no_shelter")
                            .withStyle(ChatFormatting.RED), true);
            return; // 不消耗物品、不触发陷阱、不传送
        }

        // 门陷阱：开锁器潜行触发警报（有效期内；触发即拆除）
        boolean triggeredTrap = !item.alarms() && target.doorTrapActive(now);
        if (triggeredTrap) {
            target.doorTrapEndTick = 0L; // 一次性消耗
        }
        stack.shrink(1);

        player.teleportTo(level, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        // 区域地图切到目标队避难所（否则小地图仍显示自己家，闯入后完全摸不着方向）
        net.exmo.sre.sixtyseconds.network.SixtySecondsMapZoneS2CPacket.send(
                player, target.shelterBox, target.shelterSpawn, true);

        if (item.alarms() || triggeredTrap) {
            for (UUID uuid : target.members) {
                if (level.getPlayerByUUID(uuid) instanceof ServerPlayer member) {
                    member.displayClientMessage(Component.translatable(
                            triggeredTrap
                                    ? "message.noellesroles.sixty_seconds.breakin_trap_alarm"
                                    : "message.noellesroles.sixty_seconds.breakin_alarm")
                            .withStyle(ChatFormatting.RED), false);
                    member.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.5F, 0.7F);
                }
            }
            if (triggeredTrap) {
                player.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.breakin_trapped")
                        .withStyle(ChatFormatting.RED), false);
            } else {
                player.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.breakin_forced")
                        .withStyle(ChatFormatting.GOLD), false);
            }
        } else {
            player.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.breakin_sneak")
                    .withStyle(ChatFormatting.GREEN), false);
        }
    }

    /**
     * 目标避难所是否有可安全落脚的地面：落点脚下 6 格内存在实心方块。
     * <p>没建成庇护所的「房间」其出生点悬在虚空里，脚下一路空气——传送过去会掉进虚空摔死，
     * 死亡再把玩家弹回世界出生点。以此判定并直接禁止潜入这种房间。
     * （{@code findSafeSpot} 内部读方块会强制加载目标区块，故此处读到的是真实地形而非空气占位。）
     */
    private static boolean hasSafeShelterLanding(ServerLevel level, BlockPos landing) {
        for (int dy = 1; dy <= 6; dy++) {
            BlockPos below = landing.below(dy);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
