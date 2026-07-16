package net.exmo.sre.sixtyseconds.content.block;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 基地设施方块（放在白色混凝土上，按所在队伍的避难所盒生效）：
 * <ul>
 *   <li><b>ALARM 基地报警器</b>：有别队玩家闯入本队基地时向全队播报并鸣响（10 秒冷却）。</li>
 *   <li><b>DOLL 玩偶</b>：基地内每分钟给本队在家玩家 +10 理智（同队多个不叠加）。</li>
 *   <li><b>SUBWOOFER 次声波音响</b>：闯入基地的敌对玩家持续虚弱 I，进入时失明 5 秒。</li>
 * </ul>
 * 位置登记走 onPlace/onRemove 静态表（同 PowerSystem 发电机套路，按局内存活即可）。
 */
public class SixtySecondsBaseUtilityBlock extends Block {

    public enum Kind {
        ALARM, DOLL, SUBWOOFER
    }

    /** 已放置的设施：位置 → 种类（对局内内存态）。 */
    private static final Map<BlockPos, Kind> PLACED = new HashMap<>();
    /** 报警器每队冷却：teamId → 下次可播报 gameTime。 */
    private static final Map<Integer, Long> ALARM_COOLDOWN = new HashMap<>();
    /** 玩偶每队下次生效 gameTime。 */
    private static final Map<Integer, Long> DOLL_NEXT = new HashMap<>();
    /** 音响失明去重：玩家 uuid → 失明过期 gameTime。 */
    private static final Map<UUID, Long> BLINDED = new HashMap<>();

    private final Kind kind;

    public SixtySecondsBaseUtilityBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            PLACED.put(pos.immutable(), kind);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            PLACED.remove(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(SixtySecondsBaseUtilityBlock::tick);
    }

    public static void reset() {
        PLACED.clear();
        ALARM_COOLDOWN.clear();
        DOLL_NEXT.clear();
        BLINDED.clear();
    }

    private static void tick(ServerLevel level) {
        if (PLACED.isEmpty() || !SixtySecondsMod.isActive(level) || level.getGameTime() % 20 != 0) {
            return;
        }
        long now = level.getGameTime();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        for (Map.Entry<BlockPos, Kind> entry : PLACED.entrySet()) {
            BlockPos pos = entry.getKey();
            SixtySecondsState.TeamData owner = teamAt(data, pos);
            if (owner == null) {
                continue;
            }
            switch (entry.getValue()) {
                case ALARM -> tickAlarm(level, pos, owner, now);
                case DOLL -> tickDoll(level, owner, now);
                case SUBWOOFER -> tickSubwoofer(level, owner, now);
            }
        }
    }

    private static SixtySecondsState.TeamData teamAt(SixtySecondsState.Data data, BlockPos pos) {
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (team.shelterBox != null && team.shelterBox.contains(pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5)) {
                return team;
            }
        }
        return null;
    }

    private static void tickAlarm(ServerLevel level, BlockPos pos, SixtySecondsState.TeamData owner, long now) {
        if (ALARM_COOLDOWN.getOrDefault(owner.teamId, 0L) > now) {
            return;
        }
        ServerPlayer intruder = findIntruder(level, owner);
        if (intruder == null) {
            return;
        }
        ALARM_COOLDOWN.put(owner.teamId, now + 200);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.5F, 0.6F);
        Component msg = Component.translatable("message.noellesroles.sixty_seconds.base_alarm_triggered")
                .withStyle(ChatFormatting.RED);
        for (UUID uuid : owner.members) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer member) {
                member.displayClientMessage(msg, false);
                member.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
            }
        }
    }

    private static void tickDoll(ServerLevel level, SixtySecondsState.TeamData owner, long now) {
        if (DOLL_NEXT.getOrDefault(owner.teamId, 0L) > now) {
            return; // 每分钟一次；同队多个玩偶共享该计时 → 不叠加
        }
        DOLL_NEXT.put(owner.teamId, now + 20L * 60L);
        for (UUID uuid : owner.members) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer member
                    && GameUtils.isPlayerAliveAndSurvival(member)
                    && owner.shelterBox != null && owner.shelterBox.contains(member.position())) {
                SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(member);
                stats.sanity = Math.min(stats.sanityMax, stats.sanity + 10);
                stats.sync();
            }
        }
    }

    private static void tickSubwoofer(ServerLevel level, SixtySecondsState.TeamData owner, long now) {
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)
                    || SixtySecondsStatsComponent.KEY.get(player).teamId == owner.teamId
                    || owner.shelterBox == null || !owner.shelterBox.contains(player.position())) {
                continue;
            }
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, true));
            if (BLINDED.getOrDefault(player.getUUID(), 0L) < now) {
                BLINDED.put(player.getUUID(), now + 20L * 30L); // 30 秒内不再重复致盲
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, true));
            }
        }
    }

    private static ServerPlayer findIntruder(ServerLevel level, SixtySecondsState.TeamData owner) {
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(player)
                    && SixtySecondsStatsComponent.KEY.get(player).teamId != owner.teamId
                    && owner.shelterBox != null && owner.shelterBox.contains(player.position())) {
                return player;
            }
        }
        return null;
    }
}
