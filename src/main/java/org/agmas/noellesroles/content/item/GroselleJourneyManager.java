package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.event.EarlyKillPlayer;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 格罗赛尔游记 - 放逐管理器。
 *
 * <p>持有者用书将目标放逐到配置坐标（默认 -100/50/21000）。被放逐的玩家：
 * <ul>
 *   <li>无法使用技能 / 物品 / 背包（{@link ModEffects#SKILL_BANED}/{@link ModEffects#USED_BANED}/{@link ModEffects#INVENTORY_BANED}），
 *       因而也无法对其他玩家发动攻击；原版规则下玩家本就不能徒手造成伤害，故游记中玩家之间无法互相伤害。</li>
 *   <li>在游记中的任何死亡都会通过 {@link EarlyKillPlayer} 改判为「将其放入游记的持有者」击杀。</li>
 *   <li>需要站到信标（{@link Blocks#BEACON}）方块上才能回归被放逐前的位置。</li>
 * </ul>
 *
 * <p>采用与 {@code PelicanManager} 一致的静态管理器 + 服务端 tick 思路，避免新增 CCA。
 */
public final class GroselleJourneyManager {

    /** 游记内死亡的死因（用于击杀改判与死亡信息）。 */
    public static final ResourceLocation DEATH_REASON = Noellesroles.id("grosell_travelog");

    /** 放逐记录：被放逐玩家 UUID -> 放逐信息（持有者 + 放逐前位置）。 */
    private static final Map<UUID, Banishment> banished = new ConcurrentHashMap<>();

    private GroselleJourneyManager() {
    }

    private record Banishment(UUID banisher, ResourceKey<Level> dimension,
                             double x, double y, double z, float yaw, float pitch) {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(GroselleJourneyManager::tick);

        // 游记内的死亡改判为放逐持有者的击杀（在击杀流程最前端解析真正击杀者）。
        EarlyKillPlayer.FIND_KILLER_EVENT.register((victim, originalKiller, reason) -> {
            if (!(victim instanceof ServerPlayer serverVictim)) {
                return null;
            }
            Banishment b = banished.get(serverVictim.getUUID());
            if (b == null) {
                return null;
            }
            // 返回持有者（可能离线为 null，则维持原击杀者）。
            return serverVictim.level().getPlayerByUUID(b.banisher);
        });

        // 一局结束时清空状态并把仍在游记中的玩家送回。
        OnGameEnd.EVENT.register((world, gameWorldComponent) -> clearAll(world.getServer()));
    }

    public static boolean isBanished(UUID playerId) {
        return banished.containsKey(playerId);
    }

    /**
     * 将目标放逐到游记坐标。
     *
     * @return 是否成功放逐
     */
    public static boolean banish(ServerPlayer banisher, ServerPlayer target) {
        if (banisher == null || target == null) {
            return false;
        }
        if (target.getUUID().equals(banisher.getUUID())) {
            return false;
        }
        if (banished.containsKey(target.getUUID())) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(target) || !GameUtils.isPlayerAliveAndSurvival(banisher)) {
            return false;
        }

        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        ServerLevel originLevel = target.serverLevel();

        // 记录放逐前位置。
        banished.put(target.getUUID(), new Banishment(banisher.getUUID(), originLevel.dimension(),
                target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot()));

        // 放逐处（原位置）粒子与声音。
        spawnBanishFx(originLevel, target.getX(), target.getY() + 1.0, target.getZ());

        // 传送进游记。
        target.teleportTo(originLevel,
                config.grosellTravelogBanishX + 0.5,
                config.grosellTravelogBanishY,
                config.grosellTravelogBanishZ + 0.5,
                Set.of(), target.getYRot(), target.getXRot());
        applyRestrictions(target);

        // 目的地粒子与声音。
        spawnBanishFx(target.serverLevel(), target.getX(), target.getY() + 1.0, target.getZ());

        target.displayClientMessage(Component
                .translatable("message.noellesroles.grosell_travelog.banished")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        return true;
    }

    /** 将玩家从游记送回被放逐前的位置。 */
    public static void returnPlayer(ServerPlayer player) {
        Banishment b = banished.remove(player.getUUID());
        if (b == null) {
            return;
        }
        ServerLevel dim = player.server.getLevel(b.dimension);
        if (dim == null) {
            dim = player.serverLevel();
        }

        spawnBanishFx(player.serverLevel(), player.getX(), player.getY() + 1.0, player.getZ());
        player.teleportTo(dim, b.x, b.y, b.z, Set.of(), b.yaw, b.pitch);
        removeRestrictions(player);
        spawnBanishFx(dim, b.x, b.y + 1.0, b.z);

        player.displayClientMessage(Component
                .translatable("message.noellesroles.grosell_travelog.returned")
                .withStyle(ChatFormatting.GREEN), true);
    }

    private static void tick(MinecraftServer server) {
        if (banished.isEmpty()) {
            return;
        }
        for (UUID id : List.copyOf(banished.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                // 离线：保留记录，等其回归或一局结束清理。
                continue;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                // 在游记中死亡 / 变为旁观：仅清理状态，不再送回。
                banished.remove(id);
                continue;
            }
            // 持续封禁技能 / 物品 / 背包。
            applyRestrictions(player);
            // 站上信标即回归。
            if (player.level().getBlockState(player.blockPosition().below()).is(Blocks.BEACON)) {
                returnPlayer(player);
            }
        }
    }

    private static void clearAll(MinecraftServer server) {
        if (banished.isEmpty()) {
            return;
        }
        for (UUID id : List.copyOf(banished.keySet())) {
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(id);
            if (player != null) {
                returnPlayer(player);
            } else {
                banished.remove(id);
            }
        }
        banished.clear();
    }

    private static void applyRestrictions(ServerPlayer player) {
        addHiddenEffect(player, ModEffects.SKILL_BANED);
        addHiddenEffect(player, ModEffects.USED_BANED);
        addHiddenEffect(player, ModEffects.INVENTORY_BANED);
    }

    private static void removeRestrictions(ServerPlayer player) {
        player.removeEffect(ModEffects.SKILL_BANED);
        player.removeEffect(ModEffects.USED_BANED);
        player.removeEffect(ModEffects.INVENTORY_BANED);
    }

    private static void addHiddenEffect(ServerPlayer player, Holder<MobEffect> effect) {
        // 短时长 + 每 tick 刷新，确保期间始终生效；不显示粒子 / 图标。
        player.addEffect(new MobEffectInstance(effect, 40, 0, false, false, false));
    }

    private static void spawnBanishFx(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.PORTAL, x, y, z, 50, 0.4, 0.6, 0.4, 0.4);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 25, 0.3, 0.5, 0.3, 0.05);
        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0f, 0.6f);
    }
}
