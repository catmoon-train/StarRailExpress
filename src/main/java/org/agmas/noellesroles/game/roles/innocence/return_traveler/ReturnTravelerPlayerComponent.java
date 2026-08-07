/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.game.roles.innocence.return_traveler;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.TMMRoles;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 归途旅人组件 —— 平民阵营，拥有两个可切换技能。
 *
 * <p>
 * <b>技能切换</b>：按技能切换键（Y）即可在「旧日渡口」「末班车」之间切换，
 * 技能键（G）释放当前选中的技能。
 * </p>
 *
 * <p>
 * <b>旧日渡口</b>：起手播放粒子爆发与音效，2 秒后把 4 格内最近的 2 名玩家拉入里世界
 * （与怀旧者里世界一致）持续 10 秒；10 秒后里世界破碎（特效音效与怀旧者相同），
 * 随后归途旅人本人获得 15 秒 {@link ModEffects#PLAYER_ISOLATION} 与
 * {@link ModEffects#GHOST_STATE}。
 * 冷却 80 秒。
 * </p>
 *
 * <p>
 * <b>末班车</b>：一局仅能释放一次。起手 3 秒后，把 12 格内除自己以外的所有玩家拉入里世界
 * 30 秒（怀旧者与布袋鬼不受影响）。在末班车里世界期间再次按下技能键，归途旅人本人转为平民
 * 并重新播放欢迎报幕。
 * </p>
 *
 * <p>
 * 所有药水效果均以 {@code showParticles=false, showIcon=false} 施加，不产生气泡。
 * </p>
 */
public class ReturnTravelerPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<ReturnTravelerPlayerComponent> KEY = ModComponents.RETURN_TRAVELER;

    public static final ResourceLocation SKILL_ID = Noellesroles.id("return_traveler_ability");

    /** 模式：旧日渡口。 */
    public static final int MODE_OLD_FERRY = 0;
    /** 模式：末班车。 */
    public static final int MODE_LAST_TRAIN = 1;

    // ---- 旧日渡口 ----
    /** 前摇 2 秒。 */
    public static final int OLD_FERRY_WINDUP_TICKS = 40;
    /** 里世界持续 10 秒。 */
    public static final int OLD_FERRY_BACKWORLD_TICKS = 200;
    /** 拉人半径 4 格。 */
    public static final double OLD_FERRY_RADIUS = 4.0D;
    /** 最多拉 2 人。 */
    public static final int OLD_FERRY_MAX_TARGETS = 2;
    /** 冷却 80 秒。 */
    public static final int OLD_FERRY_COOLDOWN_TICKS = 80 * 20;
    /** 结束后自身隐匿 15 秒。 */
    public static final int OLD_FERRY_SELF_HIDE_TICKS = 15 * 20;

    // ---- 末班车 ----
    /** 前摇 3 秒。 */
    public static final int LAST_TRAIN_WINDUP_TICKS = 60;
    /** 里世界持续 30 秒。 */
    public static final int LAST_TRAIN_BACKWORLD_TICKS = 30 * 20;
    /** 拉人半径 12 格。 */
    public static final double LAST_TRAIN_RADIUS = 12.0D;

    /**
     * 全局记录：当前正被归途旅人关在里世界中的玩家。
     * 用于死亡拦截（里世界中的人不会被杀手杀死），不依赖共享的描边效果，
     * 因此不会误伤布袋鬼里世界的目标。
     */
    private static final Set<UUID> BACKWORLD_VICTIMS = new HashSet<>();

    private final Player player;

    /** 当前选中的技能模式（同步给自己，用于 HUD 提示）。 */
    public int currentMode = MODE_OLD_FERRY;

    /** 末班车是否已经用过（一局一次）。 */
    public boolean lastTrainUsed = false;

    /** 末班车里世界是否正在进行（用于「再次按键转职」）。 */
    public boolean lastTrainActive = false;

    /** 旧日渡口前摇剩余 tick（>0 表示正在起手）。 */
    private int oldFerryWindup = 0;
    /** 旧日渡口里世界剩余 tick。 */
    private int oldFerryBackworld = 0;
    /** 旧日渡口冷却剩余 tick。 */
    public int oldFerryCooldown = 0;

    /** 末班车前摇剩余 tick。 */
    private int lastTrainWindup = 0;
    /** 末班车里世界剩余 tick。 */
    private int lastTrainBackworld = 0;

    /** 旧日渡口受害者。 */
    private final Set<UUID> oldFerryVictims = new HashSet<>();
    /** 末班车受害者。 */
    private final Set<UUID> lastTrainVictims = new HashSet<>();

    public ReturnTravelerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return this.player == target;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void init() {
        releaseAllVictims();
        currentMode = MODE_OLD_FERRY;
        lastTrainUsed = false;
        lastTrainActive = false;
        oldFerryWindup = 0;
        oldFerryBackworld = 0;
        oldFerryCooldown = 0;
        lastTrainWindup = 0;
        lastTrainBackworld = 0;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    // ------------------------------------------------------------------
    // 技能入口
    // ------------------------------------------------------------------

    /** 切换技能（蹲下 + 技能切换键），不受冷却影响。 */
    public void toggleMode() {
        currentMode = (currentMode == MODE_OLD_FERRY) ? MODE_LAST_TRAIN : MODE_OLD_FERRY;
        if (player instanceof ServerPlayer serverPlayer) {
            Component message = (currentMode == MODE_OLD_FERRY)
                    ? Component.translatable("message.noellesroles.return_traveler.mode.old_ferry")
                            .withStyle(ChatFormatting.AQUA)
                    : Component.translatable("message.noellesroles.return_traveler.mode.last_train")
                            .withStyle(ChatFormatting.GOLD);
            serverPlayer.displayClientMessage(message, true);
        }
        sync();
    }

    /** 释放当前模式的技能（技能键）。 */
    public boolean useAbility() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverPlayer.level());
        if (!gameWorld.isSkillAvailable || !gameWorld.isRole(serverPlayer, ModRoles.RETURN_TRAVELER)) {
            return false;
        }

        return currentMode == MODE_OLD_FERRY ? useOldFerry(serverPlayer) : useLastTrain(serverPlayer);
    }

    // ------------------------------------------------------------------
    // 旧日渡口
    // ------------------------------------------------------------------

    private boolean useOldFerry(ServerPlayer serverPlayer) {
        if (oldFerryWindup > 0 || oldFerryBackworld > 0) {
            return false;
        }
        if (oldFerryCooldown > 0) {
            serverPlayer.displayClientMessage(Component.translatable("message.sre.skill.cooldown",
                    String.format("%.1f", oldFerryCooldown / 20.0F)).withStyle(ChatFormatting.RED), true);
            return false;
        }

        oldFerryWindup = OLD_FERRY_WINDUP_TICKS;
        spawnFerryOpenEffect(serverPlayer);
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.return_traveler.old_ferry.start")
                        .withStyle(ChatFormatting.AQUA),
                true);
        sync();
        return true;
    }

    /**
     * 旧日渡口开启特效：一次性批量粒子爆发 + 音效。
     *
     * <p>
     * 性能取向与苦力怕爆炸一致：使用服务端 {@link ServerLevel#sendParticles} 单次批量下发，
     * 而不是每 tick 逐粒子发包，客户端只收到 3 个粒子包。
     * </p>
     */
    private void spawnFerryOpenEffect(ServerPlayer serverPlayer) {
        ServerLevel level = serverPlayer.serverLevel();
        double x = serverPlayer.getX();
        double y = serverPlayer.getY() + 1.0D;
        double z = serverPlayer.getZ();

        // 爆开的核心（与苦力怕爆炸相同的 EXPLOSION_EMITTER，单个粒子由客户端展开成整团爆炸）
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        // 向外扩散的渡口幽光
        level.sendParticles(ParticleTypes.SOUL, x, y, z, 24, 1.2D, 0.6D, 1.2D, 0.05D);
        // 汇聚回旋的传送门碎片
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 40, 0.8D, 1.0D, 0.8D, 0.25D);

        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 0.5F);
        level.playSound(null, x, y, z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    /** 前摇结束：把 4 格内最近的 2 名玩家拉入里世界，归途旅人本人也一并进入里世界。 */
    private void startOldFerryBackworld(ServerPlayer serverPlayer) {
        ServerLevel level = serverPlayer.serverLevel();
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);

        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer other : level.players()) {
            if (other == serverPlayer)
                continue;
            if (!isValidBackworldTarget(gameWorld, other))
                continue;
            if (other.distanceTo(serverPlayer) > OLD_FERRY_RADIUS)
                continue;
            candidates.add(other);
        }
        candidates.sort(Comparator.comparingDouble(serverPlayer::distanceTo));

        oldFerryVictims.clear();
        for (ServerPlayer target : candidates) {
            if (oldFerryVictims.size() >= OLD_FERRY_MAX_TARGETS)
                break;
            oldFerryVictims.add(target.getUUID());
            BACKWORLD_VICTIMS.add(target.getUUID());
            spawnEnterBackworldEffect(target);
            applyBackworldEffects(target, OLD_FERRY_BACKWORLD_TICKS);
            target.displayClientMessage(
                    Component.translatable("message.noellesroles.return_traveler.dragged")
                            .withStyle(ChatFormatting.DARK_GRAY),
                    true);
        }

        // 归途旅人本人也拉入里世界（与受害者身处同一里世界，获得隐身/禁言/禁用物品等效果）
        oldFerryVictims.add(serverPlayer.getUUID());
        BACKWORLD_VICTIMS.add(serverPlayer.getUUID());
        spawnEnterBackworldEffect(serverPlayer);
        applyBackworldEffects(serverPlayer, OLD_FERRY_BACKWORLD_TICKS);
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.return_traveler.dragged")
                        .withStyle(ChatFormatting.DARK_GRAY),
                true);

        oldFerryBackworld = OLD_FERRY_BACKWORLD_TICKS;
        // 被卷入里世界的其他乘客数量（不含本人）
        int others = oldFerryVictims.size() - 1;
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.return_traveler.old_ferry.open",
                        others).withStyle(ChatFormatting.AQUA),
                true);
        sync();
    }

    /** 旧日渡口结束：里世界破碎，归途旅人自身隐匿 15 秒，并进入 80 秒冷却。 */
    private void finishOldFerry(ServerPlayer serverPlayer) {
        for (UUID uuid : new HashSet<>(oldFerryVictims)) {
            ServerPlayer target = serverPlayer.server.getPlayerList().getPlayer(uuid);
            if (target != null) {
                removeBackworldEffects(target);
                spawnCollapseEffect(target);
            }
            BACKWORLD_VICTIMS.remove(uuid);
        }
        oldFerryVictims.clear();
        oldFerryBackworld = 0;
        // 清理自身的同界描边
        serverPlayer.removeEffect(ModEffects.BACKWORLD_OUTLINE);

        // 自身隐匿：15 秒 玩家隔离 + 幽灵状态
        serverPlayer.addEffect(new MobEffectInstance(
                ModEffects.PLAYER_ISOLATION, OLD_FERRY_SELF_HIDE_TICKS, 0, true, false, false));
        serverPlayer.addEffect(new MobEffectInstance(
                ModEffects.GHOST_STATE, OLD_FERRY_SELF_HIDE_TICKS, 0, true, false, false));
        spawnCollapseEffect(serverPlayer);

        oldFerryCooldown = OLD_FERRY_COOLDOWN_TICKS;
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.return_traveler.old_ferry.end")
                        .withStyle(ChatFormatting.DARK_AQUA),
                true);
        sync();
    }

    // ------------------------------------------------------------------
    // 末班车
    // ------------------------------------------------------------------

    private boolean useLastTrain(ServerPlayer serverPlayer) {
        if (lastTrainUsed) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.return_traveler.last_train.used")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (lastTrainWindup > 0 || lastTrainBackworld > 0) {
            return false;
        }

        lastTrainUsed = true;
        lastTrainWindup = LAST_TRAIN_WINDUP_TICKS;
        ServerLevel level = serverPlayer.serverLevel();
        level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 1.0F, 0.4F);
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.return_traveler.last_train.start")
                        .withStyle(ChatFormatting.GOLD),
                true);
        sync();
        return true;
    }

    /** 前摇结束：把 12 格内除自己以外的所有玩家拉入里世界 30 秒。 */
    private void startLastTrainBackworld(ServerPlayer serverPlayer) {
        ServerLevel level = serverPlayer.serverLevel();
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);

        lastTrainVictims.clear();
        for (ServerPlayer other : level.players()) {
            if (other == serverPlayer)
                continue;
            if (!isValidBackworldTarget(gameWorld, other))
                continue;
            if (other.distanceTo(serverPlayer) > LAST_TRAIN_RADIUS)
                continue;

            lastTrainVictims.add(other.getUUID());
            BACKWORLD_VICTIMS.add(other.getUUID());
            spawnEnterBackworldEffect(other);
            applyBackworldEffects(other, LAST_TRAIN_BACKWORLD_TICKS);
            other.displayClientMessage(
                    Component.translatable("message.noellesroles.return_traveler.dragged")
                            .withStyle(ChatFormatting.DARK_GRAY),
                    true);
        }

        lastTrainBackworld = LAST_TRAIN_BACKWORLD_TICKS;
        lastTrainActive = true;

        // 施法者随后立即转为平民，不再需要透视里世界，因此不给自身施加描边效果

        ServerLevel sl = serverPlayer.serverLevel();
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                serverPlayer.getX(), serverPlayer.getY() + 1.0D, serverPlayer.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL,
                serverPlayer.getX(), serverPlayer.getY() + 1.0D, serverPlayer.getZ(),
                80, 2.0D, 1.2D, 2.0D, 0.35D);
        sl.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 0.4F);

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.return_traveler.last_train.open",
                        lastTrainVictims.size()).withStyle(ChatFormatting.GOLD),
                true);
        RoleUtils.changeRole(serverPlayer, TMMRoles.CIVILIAN);
        RoleUtils.sendWelcomeAnnouncement(serverPlayer, TMMRoles.CIVILIAN);
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.return_traveler.last_train.leave")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                true);
        sync();
    }

    /** 末班车里世界结束（时间到 / 主动下车）。 */
    private void finishLastTrain(ServerPlayer serverPlayer) {
        for (UUID uuid : new HashSet<>(lastTrainVictims)) {
            ServerPlayer target = serverPlayer.server.getPlayerList().getPlayer(uuid);
            if (target != null) {
                removeBackworldEffects(target);
                spawnCollapseEffect(target);
            }
            BACKWORLD_VICTIMS.remove(uuid);
        }
        lastTrainVictims.clear();
        lastTrainBackworld = 0;
        lastTrainActive = false;
        serverPlayer.removeEffect(ModEffects.BACKWORLD_OUTLINE);
        spawnCollapseEffect(serverPlayer);
        sync();
    }

    // 未使用到的方法：
    // /** 检查 4 格内是否存在可被旧日渡口拉入里世界的合法目标。 */
    // private boolean hasNearbyBackworldTarget(ServerPlayer serverPlayer) {
    // ServerLevel level = serverPlayer.serverLevel();
    // SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
    // for (ServerPlayer other : level.players()) {
    // if (other == serverPlayer) continue;
    // if (!isValidBackworldTarget(gameWorld, other)) continue;
    // if (other.distanceTo(serverPlayer) > OLD_FERRY_RADIUS) continue;
    // return true;
    // }
    // return false;
    // }
    // /** 末班车期间再次按下技能键：结束里世界并把自己变为平民。 */
    // private void leaveAsCivilian(ServerPlayer serverPlayer) {
    // finishLastTrain(serverPlayer);
    // serverPlayer.displayClientMessage(
    // Component.translatable("message.noellesroles.return_traveler.last_train.leave")
    // .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
    // true);
    // RoleUtils.changeRole(serverPlayer, TMMRoles.CIVILIAN);
    // // 转职后重新报幕，让玩家知道自己已经是平民
    // RoleUtils.sendWelcomeAnnouncement(serverPlayer, TMMRoles.CIVILIAN);
    // }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverPlayer.level());

        // 仅当“游戏未运行”时才强制释放被困者。
        // 里世界效果本身有固定时长（约 30 秒）会自然到期，因此施法者死亡/退场
        // 或末班车结局换阵营都不需要立刻解除，受害者独立维持到效果结束即可。
        boolean running = gameWorld.isRunning();
        if (!running) {
            if (!oldFerryVictims.isEmpty() || !lastTrainVictims.isEmpty()) {
                releaseAllVictims();
            }
            oldFerryBackworld = 0;
            lastTrainBackworld = 0;
            lastTrainActive = false;
            oldFerryWindup = 0;
            lastTrainWindup = 0;
            return;
        }
        if (lastTrainWindup > 0) {
            lastTrainWindup--;
            spawnWindupParticles(serverPlayer);
            if (lastTrainWindup == 0) {
                startLastTrainBackworld(serverPlayer);
            }
        }
        if (lastTrainBackworld > 0) {
            maintainBackworld(serverPlayer, lastTrainVictims);
            lastTrainBackworld--;
            if (lastTrainBackworld == 0) {
                finishLastTrain(serverPlayer);
            }
        }

        if (!gameWorld.isRole(player, ModRoles.RETURN_TRAVELER)) {
            // 不是 RETURN_TRAVELER 职业则不执行职业CCA逻辑
            return;
        }
        // 前摇
        if (oldFerryWindup > 0) {
            oldFerryWindup--;
            spawnWindupParticles(serverPlayer);
            if (oldFerryWindup == 0) {
                startOldFerryBackworld(serverPlayer);
            }
        }

        // 里世界计时（药水时长已给满，此处只推进倒计时）
        if (oldFerryBackworld > 0) {
            maintainBackworld(serverPlayer, oldFerryVictims);
            oldFerryBackworld--;
            if (oldFerryBackworld == 0) {
                finishOldFerry(serverPlayer);
            }
        }

        tickCooldown();
    }

    private void tickCooldown() {
        if (oldFerryCooldown > 0) {
            oldFerryCooldown--;
            if (oldFerryCooldown == 0 || oldFerryCooldown % 400 == 0) {
                sync();
            }
        }
    }

    /** 剔除已经掉线/死亡的人并清除其里世界效果（药水已直接给满技能时长，无需每 tick 续期）。 */
    private void maintainBackworld(ServerPlayer owner, Set<UUID> victims) {
        if (victims.isEmpty()) {
            return;
        }
        victims.removeIf(uuid -> {
            ServerPlayer target = owner.server.getPlayerList().getPlayer(uuid);
            if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
                BACKWORLD_VICTIMS.remove(uuid);
                if (target != null) {
                    removeBackworldEffects(target);
                }
                return true;
            }
            return false;
        });
    }

    // ------------------------------------------------------------------
    // 里世界效果（与怀旧者一致）
    // ------------------------------------------------------------------

    /**
     * 施加里世界的全部药水效果：隐身、灰白滤镜标记、禁用物品、禁言、消除脚步声、同界描边。
     * 时长直接给满对应里世界技能持续时间；技能结束时由 {@link #removeBackworldEffects} 兜底清除。
     * 全部以 ambient=true、不显示粒子/图标的方式施加（隐藏气泡）。
     */
    private static void applyBackworldEffects(ServerPlayer target, int duration) {
        target.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, duration, 0, true, false, false));
        target.addEffect(new MobEffectInstance(ModEffects.NOSTALGIST_BACKWORLD, duration, 0, true, false, false));
        target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, duration, 0, true, false, false));
        target.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, duration, 0, true, false, false));
        target.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN, duration, 0, true, false, false));
        target.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE, duration, 0, true, false, false));
        target.addEffect(new MobEffectInstance(ModEffects.FOOTSTEP_VANISH, duration, 0, true, false, false));
        target.addEffect(new MobEffectInstance(ModEffects.BACKWORLD_OUTLINE, duration, 0, true, false, false));
    }

    private static void removeBackworldEffects(ServerPlayer target) {
        target.removeEffect(MobEffects.INVISIBILITY);
        target.removeEffect(ModEffects.NOSTALGIST_BACKWORLD);
        target.removeEffect(ModEffects.USED_BANED);
        target.removeEffect(ModEffects.SKILL_BANED);
        target.removeEffect(ModEffects.CHAT_BAN);
        target.removeEffect(ModEffects.VOICE_SILENCE);
        target.removeEffect(ModEffects.FOOTSTEP_VANISH);
        target.removeEffect(ModEffects.BACKWORLD_OUTLINE);
    }

    /** 进入里世界的特效（一次性批量下发）。 */
    private static void spawnEnterBackworldEffect(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                target.getX(), target.getY() + 1.0D, target.getZ(), 50, 0.5D, 1.0D, 0.5D, 0.5D);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    /** 里世界破碎特效 —— 与怀旧者 collapseBackWorld 完全一致。 */
    private static void spawnCollapseEffect(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.7F, 1.0F);
        level.sendParticles(ParticleTypes.PORTAL,
                target.getX(), target.getY() + 1.0D, target.getZ(), 40, 0.5D, 1.0D, 0.5D, 0.4D);
    }

    /** 前摇期间的周身粒子（每 tick 少量，开销与怀旧者退出前摇相同）。 */
    private static void spawnWindupParticles(ServerPlayer serverPlayer) {
        serverPlayer.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL,
                serverPlayer.getX(), serverPlayer.getY() + 1.0D, serverPlayer.getZ(),
                6, 0.4D, 0.7D, 0.4D, 0.03D);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /**
     * 判断目标是否可以被拉入里世界。
     * 怀旧者与布袋鬼不受影响（他们自己就是里世界的主人），已在里世界中的人也跳过。
     */
    private static boolean isValidBackworldTarget(SREGameWorldComponent gameWorld, ServerPlayer target) {
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        if (gameWorld.isRole(target, ModRoles.NOSTALGIST) || gameWorld.isRole(target, ModRoles.MA_CHEN_XU)) {
            return false;
        }
        // 已在别人的里世界里（怀旧者灰白世界 / 布袋鬼里世界），不重复拉取
        if (target.hasEffect(ModEffects.NOSTALGIST_BACKWORLD) || target.hasEffect(ModEffects.OTHERWORLD_AURA)) {
            return false;
        }
        return true;
    }

    /** 释放所有被困者（游戏结束 / 归途旅人退场）。 */
    private void releaseAllVictims() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            BACKWORLD_VICTIMS.removeAll(oldFerryVictims);
            BACKWORLD_VICTIMS.removeAll(lastTrainVictims);
            oldFerryVictims.clear();
            lastTrainVictims.clear();
            return;
        }
        Set<UUID> all = new HashSet<>(oldFerryVictims);
        all.addAll(lastTrainVictims);
        for (UUID uuid : all) {
            ServerPlayer target = serverPlayer.server.getPlayerList().getPlayer(uuid);
            if (target != null) {
                removeBackworldEffects(target);
            }
            BACKWORLD_VICTIMS.remove(uuid);
        }
        oldFerryVictims.clear();
        lastTrainVictims.clear();
        if (player.hasEffect(ModEffects.BACKWORLD_OUTLINE)) {
            player.removeEffect(ModEffects.BACKWORLD_OUTLINE);
        }
    }

    public static void registerEvents() {
        // 被归途旅人拉进里世界的玩家在里世界中不会被杀手杀死（掉出列车的环境即死除外）
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (victim != null && BACKWORLD_VICTIMS.contains(victim.getUUID())
                    && !GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason)) {
                return false;
            }
            return true;
        });
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("currentMode", currentMode);
        tag.putBoolean("lastTrainUsed", lastTrainUsed);
        tag.putInt("oldFerryCooldown", oldFerryCooldown);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        currentMode = tag.getInt("currentMode");
        lastTrainUsed = tag.getBoolean("lastTrainUsed");
        oldFerryCooldown = tag.getInt("oldFerryCooldown");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void clientTick() {

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());

        if (!gameWorld.isRole(player, ModRoles.RETURN_TRAVELER)) {
            // 不是 RETURN_TRAVELER 职业则不执行职业CCA逻辑
            return;
        }
        // 仅当“游戏未运行”时才强制释放被困者。
        // 里世界效果本身有固定时长（约 30 秒）会自然到期，因此施法者死亡/退场
        // 或末班车结局换阵营都不需要立刻解除，受害者独立维持到效果结束即可。
        boolean running = gameWorld.isRunning();
        if (!running) {
            if (!oldFerryVictims.isEmpty() || !lastTrainVictims.isEmpty()) {
                oldFerryVictims.clear();
                lastTrainVictims.clear();
            }
            oldFerryBackworld = 0;
            lastTrainBackworld = 0;
            lastTrainActive = false;
            oldFerryWindup = 0;
            lastTrainWindup = 0;
            return;
        }
        tickCooldown();
    }
}
