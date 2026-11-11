package org.agmas.noellesroles.game.scanner;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameStarted;
import io.wifi.starrailexpress.event.OnPlayerDeathWithBody;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.agmas.noellesroles.content.block_entity.IdentityScannerBlockEntity;
import org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.IdentityScannerStateS2CPacket;
import org.agmas.noellesroles.utils.DisguisedBodyUtils;
import org.agmas.noellesroles.utils.MoneyUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 统一服务端入口：真实死亡快照、个人去重、机器冷却、公开历史和随身扫描。 */
public final class IdentityScannerService {
    private static final Map<ResourceKey<Level>, IdentityScannerLedger> LEDGERS = new HashMap<>();
    private record Viewing(ResourceKey<Level> dimension, BlockPos pos, long refreshedAt) { }
    private static final java.util.Set<UUID> BODY_SNAPSHOTS = new java.util.HashSet<>();
    private static final Map<UUID, Viewing> VIEWERS = new HashMap<>();
    private static boolean registered;
    private IdentityScannerService() { }

    private static IdentityScannerLedger ledger(Level level) {
        return LEDGERS.computeIfAbsent(level.dimension(), key -> new IdentityScannerLedger());
    }
    private static long now(Level level) { return GameUtils.getTicksFromGameStart(level); }
    private static boolean running(Player player) { return SREGameWorldComponent.KEY.get(player.level()).isRunning(); }
    private static boolean canOperate(ServerPlayer player) {
        return IdentityScannerAccess.canUse(player) && running(player) && GameUtils.isPlayerAliveAndSurvival(player)
                && !player.hasEffect(ModEffects.USED_BANED);
    }
    private static boolean currentlyDead(ServerLevel level, UUID uuid) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(uuid);
        return owner == null || !GameUtils.isPlayerAliveAndSurvival(owner);
    }
    private static IdentityScannerLedger.Identity identity(Player victim, SRERole role) {
        String name = victim.getGameProfile().getName();
        return new IdentityScannerLedger.Identity(victim.getUUID(), name.substring(0, Math.min(64, name.length())),
                role.identifier().toString(), role.color());
    }
    public static void registerEvents() {
        if (registered) return;
        registered = true;
        OnGameStarted.EVENT.register(level -> reset(level));
        OnGameEnd.EVENT.register((level, game) -> reset(level));
        ServerWorldEvents.UNLOAD.register((server, level) -> reset(level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { LEDGERS.clear(); VIEWERS.clear(); BODY_SNAPSHOTS.clear(); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VIEWERS.remove(handler.player.getUUID()));
        OnPlayerDeathWithBody.EVENT.register((victim, killer, reason, body) -> {
            if (!running(victim) || body == null) return;
            SRERole role = TMMRoles.getRole(PlayerBodyEntityComponent.KEY.get(body).playerRole);
            if (role != null) {
                ledger(victim.level()).recordDeath(identity(victim, role));
                BODY_SNAPSHOTS.add(victim.getUUID());
            }
        });
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, reason) -> {
            if (!running(victim) || !(victim.level() instanceof ServerLevel level)
                    || !currentlyDead(level, victim.getUUID())) return;
            // 每次真实死亡都更新快照；有尸体时优先使用其死亡职业。
            if (!BODY_SNAPSHOTS.remove(victim.getUUID())) {
                SRERole role = SREGameWorldComponent.KEY.get(level).getRole(victim);
                if (role != null) ledger(level).recordDeath(identity(victim, role));
            }
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(entity instanceof PlayerBodyEntity) || !player.getItemInHand(hand).is(ModItems.PORTABLE_IDENTITY_SCANNER))
                return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer) scanPortable(serverPlayer);
            return InteractionResult.SUCCESS;
        });
    }
    private static void reset(ServerLevel level) {
        LEDGERS.remove(level.dimension());
        VIEWERS.entrySet().removeIf(entry -> entry.getValue().dimension().equals(level.dimension()));
        level.players().forEach(player -> BODY_SNAPSHOTS.remove(player.getUUID()));
    }
    private static IdentityScannerBlockEntity machine(ServerPlayer player, BlockPos pos) {
        if (!IdentityScannerAccess.canUse(player) || !running(player) || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 36
                || !player.serverLevel().hasChunkAt(pos)) return null;
        if (!(player.serverLevel().getBlockEntity(pos) instanceof IdentityScannerBlockEntity machine)) return null;
        machine.ensureRound(ledger(player.level()).roundId());
        return machine;
    }
    public static void open(ServerPlayer player, BlockPos pos) {
        if (!IdentityScannerAccess.canUse(player)) {
            portableMessage(player, "neutral_forbidden");
            return;
        }
        IdentityScannerBlockEntity machine = machine(player, pos);
        if (machine == null) { message(player, "not_running"); return; }
        VIEWERS.put(player.getUUID(), new Viewing(player.level().dimension(), pos.immutable(), player.level().getGameTime()));
        send(player, machine);
    }
    /** 所有客户端请求都要求先通过真实方块交互打开；拒绝远程位置伪造和高频刷新。 */
    public static void handleRequest(ServerPlayer player, BlockPos pos, boolean scan) {
        Viewing viewing = VIEWERS.get(player.getUUID());
        if (viewing == null || !viewing.dimension().equals(player.level().dimension()) || !viewing.pos().equals(pos)) return;
        IdentityScannerBlockEntity machine = machine(player, pos);
        if (machine == null) { VIEWERS.remove(player.getUUID()); return; }
        long time = player.level().getGameTime();
        if (time - viewing.refreshedAt() < 4) return;
        VIEWERS.put(player.getUUID(), new Viewing(viewing.dimension(), viewing.pos(), time));
        if (scan) scanRandom(player, machine);
        send(player, machine);
    }
    private static void scanRandom(ServerPlayer player, IdentityScannerBlockEntity machine) {
        if (!canOperate(player)) { message(player, "cannot_use"); return; }
        long now = now(player.level());
        int remaining = machine.remaining(now);
        if (remaining > 0) { message(player, "cooldown", (remaining + 19) / 20); return; }
        IdentityScannerLedger ledger = ledger(player.level());
        var candidates = ledger.candidates(player.getUUID(), uuid -> currentlyDead(player.serverLevel(), uuid));
        if (candidates.isEmpty()) { message(player, "no_candidates"); return; }
        if (!MoneyUtils.hasBalance(player, 50)) { MoneyUtils.sendNotEnoughtMoneyMessage(player, 50); return; }
        var selected = candidates.get(player.getRandom().nextInt(candidates.size()));
        if (!MoneyUtils.cost(player, 50)) return;
        ledger.markScanned(player.getUUID(), selected.player());
        machine.scanned(selected, now);
        player.serverLevel().playSound(null, machine.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.6F, 1.2F);
    }
    private static void send(ServerPlayer player, IdentityScannerBlockEntity machine) {
        if (ServerPlayNetworking.canSend(player, IdentityScannerStateS2CPacket.ID))
            ServerPlayNetworking.send(player, new IdentityScannerStateS2CPacket(machine.getBlockPos(),
                    machine.remaining(now(player.level())), machine.history()));
    }
    public static void scanPortable(ServerPlayer player) {
        if (!IdentityScannerAccess.canUse(player)) {
            portableMessage(player, "neutral_forbidden");
            return;
        }
        if (!canOperate(player) || (!player.getMainHandItem().is(ModItems.PORTABLE_IDENTITY_SCANNER)
                && !player.getOffhandItem().is(ModItems.PORTABLE_IDENTITY_SCANNER))) return;
        IdentityScannerLedger ledger = ledger(player.level());
        int remaining = ledger.portableRemaining(player.getUUID(), now(player.level()));
        if (remaining > 0) { portableMessage(player, "cooldown", (remaining + 19) / 20); return; }
        var hit = ProjectileUtil.getHitResultOnViewVector(player, entity -> entity instanceof PlayerBodyEntity, 5.0D);
        if (!(hit instanceof EntityHitResult result) || !(result.getEntity() instanceof PlayerBodyEntity body)
                || body.isRemoved() || body.getPlayerUuid() == null
                || DoomedSinnerBodyEntity.isDoomedSinnerBody(body)
                || DisguisedBodyUtils.isUnreportableDisguisedBody(body)) { portableMessage(player, "invalid_body"); return; }
        var component = PlayerBodyEntityComponent.KEY.get(body);
        if (component.isFakeBody || !currentlyDead(player.serverLevel(), body.getPlayerUuid())
                || !player.hasLineOfSight(body)) { portableMessage(player, "invalid_body"); return; }
        SRERole role = TMMRoles.getRole(component.playerRole);
        if (role == null) { portableMessage(player, "invalid_body"); return; }
        String name = component.getOwnerName();
        if (name == null || name.isBlank()) name = body.getDisplayName().getString();
        // 随身扫描不写公共终端，也不消耗随机终端的个人去重名额。
        SRENetworkMessageUtils.sendTitleTime(player, 10, 80, 20);
        SRENetworkMessageUtils.sendSubtitle(player,
                Component.translatable("message.noellesroles.identity_scanner.result_target", Component.literal(name)));
        SRENetworkMessageUtils.sendTitle(player,
                Component.translatable("announcement.star.role." + role.identifier().getPath()).withColor(role.color()));
        ledger.portableScanned(player.getUUID(), now(player.level()));
        player.getCooldowns().addCooldown(ModItems.PORTABLE_IDENTITY_SCANNER, 120 * 20);
    }
    /** 首次成为验尸官时登记本局丢弃期限；离线、换道具或重新购买都不能重置。 */
    public static long coronerDropReadyAt(ServerPlayer player) {
        return running(player) ? ledger(player.level()).coronerDropReadyAt(player.getUUID(), now(player.level())) : -1;
    }
    private static void portableMessage(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.noellesroles.identity_scanner." + key, args), true);
    }
    private static void message(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable("message.noellesroles.identity_scanner." + key, args));
    }
}
