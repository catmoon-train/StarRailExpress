package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.content.item.SixtySecondsBreakInItem;
import net.exmo.sre.sixtyseconds.network.OpenBreakInSelectS2CPacket;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 撬棍/撬锁器闯入别队避难所：
 * <ul>
 *   <li><b>撬棍</b>（alarms=true）：强行闯入并<b>触发目标队报警</b>。</li>
 *   <li><b>撬锁器</b>（alarms=false）：<b>潜行进入不报警</b>。</li>
 * </ul>
 * 流程：右键 → 选队界面（{@code BreakInSelectScreen}，只列门等级不高于工具等级的队）→
 * C2S 回传 → {@link #execute} 服务端按主手物品重校验后<b>消耗物品</b>并把玩家
 * <b>安全落点</b>传进目标队避难所内（旧实现随机选队 + 直传 shelterSpawn，出生点若在门/墙体方块里
 * 会窒息掉血倒地死——与 {@code SixtySecondsSearchZones.enter} 的落点校正同款修法）。
 */
public final class SixtySecondsBreakIn {
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
     * 右键使用：列出可闯入的别队让玩家选择；此时<b>不消耗</b>物品（选定并成功传送才消耗）。
     * @return 是否成功打开了选择界面。
     */
    public static boolean openSelect(ServerPlayer player, boolean alarms, int tier) {
        ServerLevel level = player.serverLevel();
        if (SixtySecondsVisiting.isVisiting(player)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.breakin_while_visiting"), true);
            return false;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        // 前两天新手保护期：不许破门闯入别人家（撬棍/撬锁器）
        if (isHomeEntryLocked(data)) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.breakin_too_early").withStyle(ChatFormatting.RED), true);
            return false;
        }
        int myTeam = SixtySecondsStatsComponent.KEY.get(player).teamId;

        List<Integer> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        boolean anyDoorTooHigh = false;
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            // 排除自己家：teamId + 成员名单双重判定——teamId 偶发未同步/为 -1（重连未恢复等）时仍能靠
            // 成员名单认出自己队，杜绝「撬自己家的门」。
            if (isOwnTeam(team, player, myTeam) || team.shelterSpawn == null) {
                continue;
            }
            if (team.doorLevel > tier) {
                anyDoorTooHigh = true; // 门等级高于工具等级，撬不开
                continue;
            }
            ids.add(team.teamId);
            labels.add("Team " + (team.teamId + 1) + " (" + team.members.size() + ")");
        }
        if (ids.isEmpty()) {
            player.displayClientMessage(Component.translatable(anyDoorTooHigh
                    ? "message.noellesroles.sixty_seconds.breakin_door_too_high"
                    : "message.noellesroles.sixty_seconds.breakin_no_target"), true);
            return false;
        }
        ServerPlayNetworking.send(player, new OpenBreakInSelectS2CPacket(
                ids.stream().mapToInt(Integer::intValue).toArray(), labels.toArray(new String[0]), alarms));
        return true;
    }

    /**
     * 选队回传：按<b>主手物品</b>重校验（等级/报警属性以服务端当下持有物为准，防伪造包白嫖），
     * 成功则消耗 1 个并安全传送进目标队避难所。
     */
    public static void execute(ServerPlayer player, int targetTeamId) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof SixtySecondsBreakInItem item)) {
            return;
        }
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
        SixtySecondsState.TeamData target = data.teams.get(targetTeamId);
        int myTeam = SixtySecondsStatsComponent.KEY.get(player).teamId;
        // 服务端权威：绝不闯入自己家（teamId + 成员名单双重判定，防伪造包 / teamId 未同步绕过）
        if (target == null || isOwnTeam(target, player, myTeam) || target.shelterSpawn == null
                || target.shelterBox == null) {
            return;
        }
        if (target.doorLevel > item.tier()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.breakin_door_too_high"), true);
            return;
        }
        long now = level.getGameTime();
        // 门锁：撬棍强闯被阻断（有效期内）
        if (item.alarms() && target.doorLockActive(now)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.breakin_door_locked")
                            .withStyle(ChatFormatting.RED), true);
            return;
        }
        // 落点安全校正 + 校验（须在消耗物品/触发陷阱之前）：shelterSpawn 可能在门/墙体里（会窒息），
        // 更糟的是该「房间」可能根本没建成庇护所、出生点悬在虚空——传过去会掉进虚空摔死，死亡再把玩家
        // 弹回世界出生点（「潜入没有庇护所的房间传到世界边境而死」根因）。落点脚下无地面=没有真正的庇护所，直接禁止。
        BlockPos safe = SixtySecondsSearchZones.findSafeSpot(level, target.shelterSpawn);
        if (!hasSafeShelterLanding(level, safe)) {
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
