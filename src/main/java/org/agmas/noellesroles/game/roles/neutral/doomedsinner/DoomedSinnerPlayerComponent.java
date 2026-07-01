package org.agmas.noellesroles.game.roles.neutral.doomedsinner;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.replay.ReplayEvent;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMEntities;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.DoomedSinnerFateRevealS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * 宿命的罪人（Doomed Sinner）—— 中立独立胜利角色。
 *
 * <p>核心机制「轮回」：罪人通过以 <b>不同</b> 的死亡原因死去来累积胜利进度，
 * 需要的不同死因数量随人数变化（最低 5，最高 10）。每次非彻底死亡后会在自己的
 * 房间复活，留下的尸体在数秒后消失。若以 <b>同一种</b> 死因死去达到 3 次，则彻底死亡。</p>
 *
 * <ul>
 *   <li>技能 1「命运的启示」：近距离查看目标最近 3 次的杀人方式（GUI）。</li>
 *   <li>技能 2「重启」：随机挑选一种死因进行死亡脱离（回到房间、短暂无敌），
 *       不计入死亡记录、不触发死亡判定。</li>
 * </ul>
 *
 * 胜利判定参见 {@link org.agmas.noellesroles.CustomWinnerClass}，
 * 技能注册参见 {@link org.agmas.noellesroles.init.ModRolesInitialEventRegister}。
 */
public class DoomedSinnerPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<DoomedSinnerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            Noellesroles.id("doomed_sinner"),
            DoomedSinnerPlayerComponent.class);

    // ── 死亡事件监听：累积死因 / 安排复活 ───────────────────────────
    static {
        OnPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (!(player instanceof ServerPlayer)) {
                return;
            }
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (!gameWorld.isRunning()) {
                return;
            }
            if (!gameWorld.isRole(player, ModRoles.DOOMED_SINNER)) {
                return;
            }
            KEY.get(player).onDeath(deathReason);
        });
    }

    private final Player player;

    /** 已累积的不同死因（胜利进度）。 */
    private final Set<ResourceLocation> distinctReasons = new LinkedHashSet<>();
    /** 每种死因死亡次数（用于「同一死因 3 次彻底死亡」判定）。 */
    private final Map<ResourceLocation, Integer> reasonCounts = new HashMap<>();
    /** 待消失的尸体：实体 UUID -> 剩余 tick。 */
    private final Map<UUID, Integer> corpseTimers = new HashMap<>();

    /** 需要达成的不同死因数量。 */
    public int requiredReasons = 5;

    /** 自己的房间锚点（复活点）。 */
    private boolean roomCaptured = false;
    private double roomX, roomY, roomZ;
    /** 进入运行后用于让坐标稳定的延迟计数。 */
    private int roomCaptureDelay = 0;

    /** 复活调度。 */
    private boolean pendingRevive = false;
    private int reviveDelay = 0;

    /** 是否已彻底死亡（达成同一死因 3 次）。 */
    private boolean permanentlyDead = false;

    public DoomedSinnerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ── 生命周期 ──────────────────────────────────────────────────

    @Override
    public void init() {
        distinctReasons.clear();
        reasonCounts.clear();
        corpseTimers.clear();
        roomCaptured = false;
        roomCaptureDelay = 0;
        pendingRevive = false;
        reviveDelay = 0;
        permanentlyDead = false;
        if (player.level() instanceof ServerLevel) {
            int totalPlayers = SREGameWorldComponent.KEY.get(player.level()).getPlayerCount();
            requiredReasons = computeRequiredReasons(totalPlayers);
        }
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    /** 不同死因目标数量：&lt;=16 人为下限，&gt;=32 人为上限，中间线性插值。 */
    public static int computeRequiredReasons(int totalPlayers) {
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        int min = Math.max(1, config.doomedSinnerMinReasons);
        int max = Math.max(min, config.doomedSinnerMaxReasons);
        if (totalPlayers <= 16) {
            return min;
        }
        if (totalPlayers >= 32) {
            return max;
        }
        double t = (totalPlayers - 16) / (32.0 - 16.0);
        return Math.max(min, Math.min(max, (int) Math.round(min + t * (max - min))));
    }

    // ── 死亡处理 ──────────────────────────────────────────────────

    /**
     * 玩家真正死亡时（已进入旁观）调用。根据死因决定彻底死亡或安排复活。
     */
    private void onDeath(ResourceLocation deathReason) {
        if (deathReason == null) {
            deathReason = GameConstants.DeathReasons.GENERIC;
        }
        if (permanentlyDead) {
            return;
        }
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();

        int newCount = reasonCounts.getOrDefault(deathReason, 0) + 1;
        reasonCounts.put(deathReason, newCount);
        boolean isNewReason = distinctReasons.add(deathReason);

        // 1) 达成不同死因数量 -> 立即独立胜利
        if (isNewReason && distinctReasons.size() >= requiredReasons
                && player.level() instanceof ServerLevel serverLevel) {
            announceProgress(deathReason, true);
            sync();
            RoleUtils.customWinnerWin(serverLevel, GameUtils.WinStatus.CUSTOM,
                    ModRoles.DOOMED_SINNER_ID.getPath(),
                    OptionalInt.of(ModRoles.DOOMED_SINNER.color()));
            return;
        }

        // 2) 同一死因达到上限 / 不可复活的死因 -> 彻底死亡（保留普通尸体）
        boolean nonRevivable = deathReason.equals(GameConstants.DeathReasons.DISCONNECT)
                || deathReason.equals(GameConstants.DeathReasons.DEATH_AFK);
        if (newCount >= Math.max(2, config.doomedSinnerSamePermanentCount) || nonRevivable) {
            permanentlyDead = true;
            broadcastPermanentDeath(deathReason);
            sync();
            return;
        }

        // 3) 非彻底死亡 -> 标记尸体定时消失，安排回房间复活
        trackFreshCorpse();
        pendingRevive = true;
        reviveDelay = 2;
        announceProgress(deathReason, false);
        sync();
    }

    /** 找到本玩家刚生成的尸体，加入定时消失列表。 */
    private void trackFreshCorpse() {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        int despawnTicks = Math.max(20, NoellesRolesConfig.HANDLER.instance().doomedSinnerCorpseDespawnSeconds * 20);
        for (PlayerBodyEntity body : serverLevel.getEntities(TMMEntities.PLAYER_BODY,
                b -> player.getUUID().equals(b.getPlayerUuid()))) {
            corpseTimers.putIfAbsent(body.getUUID(), despawnTicks);
        }
    }

    // ── 每 tick ───────────────────────────────────────────────────

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.DOOMED_SINNER)) {
            return;
        }
        if (!gameWorld.isRunning()) {
            return;
        }

        // 尸体定时消失（无论存活与否都要推进）
        tickCorpses();

        // 复活调度（在旁观状态下执行）
        if (pendingRevive) {
            reviveDelay--;
            if (reviveDelay <= 0) {
                doRevive(serverPlayer);
            }
            return;
        }

        // 以下仅在存活时执行
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        // 捕获房间锚点（进入存活状态后等待坐标稳定再记录）
        if (!roomCaptured) {
            roomCaptureDelay++;
            if (roomCaptureDelay >= 40) {
                roomX = serverPlayer.getX();
                roomY = serverPlayer.getY();
                roomZ = serverPlayer.getZ();
                roomCaptured = true;
                sync();
            }
        }
    }

    private void tickCorpses() {
        if (corpseTimers.isEmpty() || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        var iterator = corpseTimers.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                Entity body = serverLevel.getEntity(entry.getKey());
                if (body != null) {
                    body.discard();
                }
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    /** 在自己的房间复活（不是真正死亡时）。 */
    private void doRevive(ServerPlayer serverPlayer) {
        pendingRevive = false;
        if (permanentlyDead) {
            return;
        }
        if (!serverPlayer.isSpectator()) {
            return;
        }
        double x = roomCaptured ? roomX : serverPlayer.getX();
        double y = roomCaptured ? roomY : serverPlayer.getY();
        double z = roomCaptured ? roomZ : serverPlayer.getZ();
        GameUtils.revivePlayer(serverPlayer, x, y, z);
        int invincibleTicks = Math.max(0,
                NoellesRolesConfig.HANDLER.instance().doomedSinnerReviveInvincibleSeconds * 20);
        if (invincibleTicks > 0) {
            serverPlayer.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, invincibleTicks, 0, false, false, false));
        }
        serverPlayer.playNotifySound(SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8f, 1.4f);
        sync();
    }

    // ── 技能 ──────────────────────────────────────────────────────

    /**
     * 技能 1「命运的启示」：近距离查看目标最近 3 次杀人方式，打开 GUI。
     *
     * @return 是否成功施放（决定是否进入冷却）。
     */
    public static boolean revealFate(ServerPlayer caster, ServerPlayer target) {
        if (target == null || target == caster) {
            caster.displayClientMessage(Component.translatable("message.noellesroles.doomed_sinner.no_target")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        double range = NoellesRolesConfig.HANDLER.instance().doomedSinnerRevealRange;
        if (caster.distanceTo(target) > range) {
            caster.displayClientMessage(Component.translatable("message.noellesroles.doomed_sinner.too_far")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }

        List<String> methods = recentKillMethods(target.getUUID(), 3);
        ServerPlayNetworking.send(caster,
                new DoomedSinnerFateRevealS2CPacket(target.getGameProfile().getName(), methods));
        caster.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f);
        return true;
    }

    /** 从回放记录中读取目标最近 N 次的杀人方式（死因 ResourceLocation 字符串）。 */
    public static List<String> recentKillMethods(UUID targetUuid, int limit) {
        List<String> result = new ArrayList<>();
        if (SRE.REPLAY_MANAGER == null) {
            return result;
        }
        List<ReplayEvent> events = SRE.REPLAY_MANAGER.getEventsByPlayer(targetUuid);
        events.stream()
                .filter(e -> e.eventType() == ReplayEventTypes.EventType.PLAYER_KILL)
                .filter(e -> e.details() instanceof ReplayEventTypes.PlayerKillDetails d
                        && targetUuid.equals(d.killerUuid()))
                .sorted(Comparator.comparingLong(ReplayEvent::timestamp).reversed())
                .limit(limit)
                .forEach(e -> result
                        .add(((ReplayEventTypes.PlayerKillDetails) e.details()).deathReason().toString()));
        return result;
    }

    /**
     * 技能 2「重启」：随机挑选一种死因进行死亡脱离 —— 回到房间、短暂无敌，
     * 不记录死亡、不推进胜利进度、不触发死亡判定。
     *
     * @return 是否成功施放（决定是否进入冷却）。
     */
    public static boolean reboot(ServerPlayer caster) {
        DoomedSinnerPlayerComponent component = KEY.get(caster);
        if (component.permanentlyDead || !GameUtils.isPlayerAliveAndSurvival(caster)) {
            return false;
        }
        double x = component.roomCaptured ? component.roomX : caster.getX();
        double y = component.roomCaptured ? component.roomY : caster.getY();
        double z = component.roomCaptured ? component.roomZ : caster.getZ();
        caster.teleportTo(x, y, z);
        int invincibleTicks = Math.max(20,
                NoellesRolesConfig.HANDLER.instance().doomedSinnerReviveInvincibleSeconds * 20);
        caster.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, invincibleTicks, 0, false, false, false));
        caster.playNotifySound(SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0f, 0.8f);

        ResourceLocation randomReason = pickRandomDeathReason(caster);
        caster.displayClientMessage(Component.translatable("message.noellesroles.doomed_sinner.reboot",
                Component.translatable(deathReasonKey(randomReason))).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    private static ResourceLocation pickRandomDeathReason(ServerPlayer caster) {
        List<ResourceLocation> reasons = new ArrayList<>(GameConstants.DeathReasons.getConstantDeathReasons());
        if (reasons.isEmpty()) {
            return GameConstants.DeathReasons.GENERIC;
        }
        int index = caster.getRandom().nextInt(reasons.size());
        return reasons.get(index);
    }

    public static String deathReasonKey(ResourceLocation reason) {
        return "death_reason." + reason.getNamespace() + "." + reason.getPath();
    }

    // ── 消息 ──────────────────────────────────────────────────────

    private void announceProgress(ResourceLocation deathReason, boolean isWin) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Component reasonText = Component.translatable(deathReasonKey(deathReason));
        if (isWin) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.doomed_sinner.final_reason", reasonText,
                    distinctReasons.size(), requiredReasons).withStyle(ChatFormatting.GOLD), true);
        } else {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.doomed_sinner.reborn", reasonText,
                    distinctReasons.size(), requiredReasons).withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    private void broadcastPermanentDeath(ResourceLocation deathReason) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Component message = Component.translatable("message.noellesroles.doomed_sinner.perish",
                player.getDisplayName(), Component.translatable(deathReasonKey(deathReason)))
                .withStyle(ChatFormatting.DARK_RED);
        for (ServerPlayer p : serverLevel.players()) {
            p.sendSystemMessage(message);
        }
    }

    // ── 胜利判定（供 CustomWinnerClass 调用） ──────────────────────

    public static boolean checkDoomedSinnerVictory(ServerLevel serverLevel) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        for (ServerPlayer player : serverLevel.players()) {
            if (!gameWorld.isRole(player, ModRoles.DOOMED_SINNER)) {
                continue;
            }
            DoomedSinnerPlayerComponent component = KEY.get(player);
            if (!component.permanentlyDead
                    && component.requiredReasons > 0
                    && component.distinctReasons.size() >= component.requiredReasons) {
                RoleUtils.customWinnerWin(serverLevel, GameUtils.WinStatus.CUSTOM,
                        ModRoles.DOOMED_SINNER_ID.getPath(),
                        OptionalInt.of(ModRoles.DOOMED_SINNER.color()));
                return true;
            }
        }
        return false;
    }

    // ── 序列化（仅同步，不持久化） ────────────────────────────────

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("Required", requiredReasons);
        tag.putBoolean("Dead", permanentlyDead);
        ListTag reasonList = new ListTag();
        for (ResourceLocation reason : distinctReasons) {
            reasonList.add(StringTag.valueOf(reason.toString()));
        }
        tag.put("Reasons", reasonList);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        requiredReasons = tag.getInt("Required");
        permanentlyDead = tag.getBoolean("Dead");
        distinctReasons.clear();
        if (tag.contains("Reasons", Tag.TAG_LIST)) {
            ListTag reasonList = tag.getList("Reasons", Tag.TAG_STRING);
            for (Tag t : reasonList) {
                ResourceLocation reason = ResourceLocation.tryParse(t.getAsString());
                if (reason != null) {
                    distinctReasons.add(reason);
                }
            }
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 不持久化
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 不持久化
    }

    // ── 客户端读取（HUD） ─────────────────────────────────────────

    public int getDistinctCount() {
        return distinctReasons.size();
    }

    public boolean isPermanentlyDead() {
        return permanentlyDead;
    }
}
