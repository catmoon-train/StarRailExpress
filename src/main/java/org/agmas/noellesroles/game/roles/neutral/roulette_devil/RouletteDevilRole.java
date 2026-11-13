package org.agmas.noellesroles.game.roles.neutral.roulette_devil;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.event.GameUtilityEvents;
import io.wifi.starrailexpress.event.OnGameStarted;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.OpenScreenPayload;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.neutral.RouletteDevilRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

/** 轮盘恶魔的技能与职业规则；房间及结算由专属会话管理，不影响普通赌桌。 */
public final class RouletteDevilRole extends CustomWinnerRole {
    public static final ResourceLocation INVENTORY = Noellesroles.id("roulette_devil_inventory");
    public static final ResourceLocation DEATH = Noellesroles.id("roulette_devil_duel");
    private static boolean registered;
    private static final List<Holder<MobEffect>> VOICES = List.of(ModEffects.VOICE_UNDERWATER,
            ModEffects.HEAVY_METAL_VOICE, ModEffects.VOICE_HELMET, ModEffects.VOICE_ROBOT);
    public RouletteDevilRole(ResourceLocation id) {
        super(id, 0x384B89, NormalRole.RoleType.INDEPENDENT_WIN_NEUTRAL, SRERole.MoodType.FAKE, 600, true);
        setRoleData(RouletteDevilRoleData::new);
        setNeutrals(true); setCanUseInstinctAndNightVision(true); setCanSeeTeammateKillerRole(false);
        setAllInstinctType(io.wifi.starrailexpress.api.InstinctType.KILLER_INSTINCT);
        setDefaultMax(1);
    }
    public static boolean isDevil(Player p) {
        return p != null && SREGameWorldComponent.KEY.get(p.level()).getRole(p) instanceof RouletteDevilRole;
    }
    public static boolean lowSan(Player p) {
        if (p == null) return false;
        var role = SREGameWorldComponent.KEY.get(p.level()).getRole(p);
        if (role == null || role.getMoodType() != MoodType.REAL || ModEffects.hasFullMoodLock(p)) return false;
        var mood = SREPlayerMoodComponent.KEY.get(p);
        return RouletteDevilRules.lowSan(mood.getMood(), mood.getMoodMax());
    }
    public static boolean canSee(Player viewer, Player demon) {
        if (viewer == demon) return true;
        var data = RoleData.getNullable(RouletteDevilRoleData.class, demon);
        return lowSan(viewer) || RouletteDevilSessions.opponents(viewer.getUUID(), demon.getUUID())
                || data != null && data.manifestUntil > demon.level().getGameTime();
    }
    public static void register() {
        if (registered) return;
        registered = true;
        RoleSkill.register(ModRoles.ROULETTE_DEVIL,
                RoleSkill.skill(Noellesroles.id("roulette_devil_seed"), "skill.noellesroles.roulette_devil.seed", ctx -> {
                    var target = ctx.getTargetAsPlayer();
                    if (target == null) { ctx.displayNoTargetMessage(); return false; }
                    return plant(ctx.player(), target);
                }).withTarget().cooldownSeconds(90).showOnHud(true).announceToSelf().build(),
                RoleSkill.skill(Noellesroles.id("roulette_devil_manifest"), "skill.noellesroles.roulette_devil.manifest", ctx -> {
                    var p = ctx.player(); var d = data(p);
                    if (d == null || !usable(p)) return false;
                    d.manifestUntil = p.level().getGameTime() + RouletteDevilRules.MANIFEST_TICKS;
                    d.sync(); RouletteDevilSessions.syncAll(p.getServer()); return true;
                }).cooldownSeconds(90).showOnHud(true).announceToSelf().build(),
                RoleSkill.skill(Noellesroles.id("roulette_devil_duel"), "skill.noellesroles.roulette_devil.duel", ctx -> {
                    if (!usable(ctx.player())) return false;
                    ServerPlayNetworking.send(ctx.player(), new OpenScreenPayload(INVENTORY));
                    return true;
                }).showOnHud(true).build());
        OnGameStarted.EVENT.register(level -> {
            int count = SREGameWorldComponent.KEY.get(level).getStartingPlayerCount();
            for (ServerPlayer p : level.players()) {
                var d = data(p);
                if (d == null) continue;
                d.startingPlayers = count; d.wins = 0; d.losses = 0; d.manifestUntil = 0;
                d.seeds.clear(); d.notified.clear(); d.refugee.reset(); d.refugeeReturn = new net.minecraft.nbt.CompoundTag(); d.sync();
            }
        });
        GameUtilityEvents.BEFORE_KILL.register((p, body, killer, reason, force) ->
                !GameUtils.isGameStarted || !isDevil(p) || RouletteDevilSessions.inDuel(p.getUUID())
                        || RouletteDevilSessions.settlingDeath(p.getUUID()));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof Player p) || !GameUtils.isGameStarted || !isDevil(p)
                        || RouletteDevilSessions.inDuel(p.getUUID()));
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
                !(entity instanceof Player p) || !GameUtils.isGameStarted || !isDevil(p)
                        || RouletteDevilSessions.inDuel(p.getUUID()));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, bound) -> {
            if (!GameUtils.isGameStarted || !isDevil(sender) || RouletteDevilSessions.inDuel(sender.getUUID())) return true;
            String glyphs = "░▒▓◆◇∴∵⍟⸸⛧";
            StringBuilder scrambled = new StringBuilder();
            int count = Math.clamp(message.signedContent().codePointCount(0, message.signedContent().length()), 8, 80);
            for (int i = 0; i < count; i++) scrambled.append(glyphs.charAt(sender.getRandom().nextInt(glyphs.length())));
            Component rendered = Component.literal("<").append(sender.getDisplayName()).append("> ")
                    .append(Component.literal(scrambled.toString()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.OBFUSCATED));
            for (ServerPlayer receiver : sender.serverLevel().players()) receiver.sendSystemMessage(rendered);
            return false;
        });
        RouletteDevilSessions.register();
    }
    public static RouletteDevilRoleData data(Player p) { return RoleData.getNullable(RouletteDevilRoleData.class, p); }
    public static boolean refugeeActive(Player p) {
        return pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent.KEY.get(p.level()).isAnyRevivals;
    }
    /** 由难民在全体快照保存之后调用，临时旁观不触发死亡与败局计数。 */
    public static void suspendForRefugee(ServerPlayer p) {
        var d = data(p);
        if (d != null && isDevil(p)) updateRefugee(p, d, true);
    }
    private static boolean updateRefugee(ServerPlayer p, RouletteDevilRoleData d, boolean active) {
        boolean pending = d.refugee.pending();
        var action = d.refugee.update(GameUtils.isGameStarted, active,
                p.isAlive() && GameUtils.isPlayerAliveAndSurvival(p), p.isSpectator(), p.gameMode.getGameModeForPlayer().getId());
        if (action == RouletteDevilRefugeeState.Action.SUSPEND) {
            RouletteDevilSessions.cancel(p.getUUID(), p.getServer());
            if (!pending) {
                var anchor = d.refugeeReturn;
                anchor.putString("Dimension", p.level().dimension().location().toString());
                anchor.putDouble("X", p.getX()); anchor.putDouble("Y", p.getY()); anchor.putDouble("Z", p.getZ());
                anchor.putFloat("Yaw", p.getYRot()); anchor.putFloat("Pitch", p.getXRot());
            }
            d.manifestUntil = 0; p.closeContainer(); p.stopUsingItem(); p.stopRiding(); p.stopSleeping();
            p.setCamera(p); removeVoices(p); p.setInvulnerable(false);
            p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            io.wifi.starrailexpress.compat.TrainVoicePlugin.resetPlayer(p.getUUID());
            tell(p, "refugee_suspended"); d.sync(); RouletteDevilSessions.syncAll(p.getServer());
        } else if (action == RouletteDevilRefugeeState.Action.RESUME) {
            // 完整回溯通常已恢复玩家；仅为旧版局部节点、掉线重连等路径兜底。
            var anchor = d.refugeeReturn;
            var id = ResourceLocation.tryParse(anchor.getString("Dimension"));
            var level = id == null ? null : p.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, id));
            p.setCamera(p); p.stopRiding(); p.setDeltaMovement(0, 0, 0); p.fallDistance = 0;
            if (level != null && anchor.contains("X"))
                p.teleportTo(level, anchor.getDouble("X"), anchor.getDouble("Y"), anchor.getDouble("Z"),
                        java.util.Set.of(), anchor.getFloat("Yaw"), anchor.getFloat("Pitch"));
            else GameUtils.teleportBackToRoom(p);
            p.setGameMode(net.minecraft.world.level.GameType.byId(d.refugee.returnGameType()));
            p.setInvulnerable(true); d.refugeeReturn = new net.minecraft.nbt.CompoundTag();
            io.wifi.starrailexpress.compat.TrainVoicePlugin.resetPlayer(p.getUUID());
            d.sync(); RouletteDevilSessions.syncAll(p.getServer());
        } else if (pending != d.refugee.pending()) {
            d.refugeeReturn = new net.minecraft.nbt.CompoundTag(); d.sync();
        }
        return active || d.refugee.pending();
    }
    private static boolean usable(ServerPlayer p) {
        var d = data(p);
        return d != null && GameUtils.isGameStarted && isDevil(p) && GameUtils.isPlayerAliveAndSurvival(p)
                && !refugeeActive(p) && !d.refugee.pending()
                && !RouletteDevilSessions.inDuel(p.getUUID()) && !ModEffects.isInAnyDomain(p);
    }
    public static boolean plant(ServerPlayer p, ServerPlayer target) {
        var d = data(p);
        if (d == null || !usable(p) || target == p || target.level() != p.level()
                || !GameUtils.isPlayerAliveAndSurvival(target) || p.distanceToSqr(target) > 64 || !p.hasLineOfSight(target)) return false;
        if (d.seeds.containsKey(target.getUUID())) { tell(p, "seed_exists"); return false; }
        d.seeds.put(target.getUUID(), p.level().getGameTime()); d.sync();
        p.displayClientMessage(Component.translatable("message.noellesroles.roulette_devil.seed_planted", target.getName()), true);
        RouletteDevilSessions.syncAll(p.getServer()); return true;
    }
    public static void tick(ServerPlayer p, RouletteDevilRoleData d) {
        if (updateRefugee(p, d, refugeeActive(p)) || !GameUtils.isGameStarted || !GameUtils.isPlayerAliveAndSurvival(p)) return;
        if (d.startingPlayers == 0) d.startingPlayers = SREGameWorldComponent.KEY.get(p.level()).getStartingPlayerCount();
        boolean duel = RouletteDevilSessions.inDuel(p.getUUID());
        p.setInvulnerable(!duel);
        long now = p.level().getGameTime();
        if (duel) removeVoices(p);
        else if (now % 10 == 0) for (var voice : VOICES) p.addEffect(new MobEffectInstance(voice, 30, 0, false, false, false));
        for (UUID id : List.copyOf(d.seeds.keySet())) {
            if (!d.mature(id, now) || d.notified.contains(id)) continue;
            ServerPlayer target = p.getServer().getPlayerList().getPlayer(id);
            if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) continue;
            d.notified.add(id); d.sync();
            p.displayClientMessage(Component.translatable("message.noellesroles.roulette_devil.seed_mature", target.getName()), false);
            title(target, "seed_title", "seed_subtitle");
            RoleUtils.playSound(target, SoundEvents.WITHER_SPAWN, net.minecraft.sounds.SoundSource.PLAYERS, .7f, .65f);
            RoleUtils.playSound(p, SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, .8f, .5f);
        }
        if (!duel && d.manifestUntil > now && now % 2 == 0) {
            for (ServerPlayer viewer : p.serverLevel().players()) {
                if (viewer == p || !GameUtils.isPlayerAliveAndSurvival(viewer) || viewer.distanceToSqr(p) > 64 * 64) continue;
                var direction = p.getEyePosition().subtract(viewer.getEyePosition());
                if (viewer.getLookAngle().dot(direction.normalize()) >= .5 && viewer.hasLineOfSight(p))
                    viewer.addEffect(new MobEffectInstance(ModEffects.ROULETTE_DEVIL_DREAD, 6, 0, false, false, false));
            }
        }
    }
    public static void removeVoices(ServerPlayer p) { for (var voice : VOICES) p.removeEffect(voice); }
    public static void tell(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("message.noellesroles.roulette_devil." + key), true);
    }
    public static void title(ServerPlayer p, String title, String subtitle) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.noellesroles.roulette_devil." + title).withStyle(ChatFormatting.DARK_PURPLE)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.noellesroles.roulette_devil." + subtitle)));
    }
    public static void declareVictory(ServerPlayer p) {
        if (!GameUtils.isGameStarted) return;
        var d = data(p);
        if (d == null || d.wins < d.required() || d.refugee.pending() || refugeeActive(p)) return;
        for (ServerPlayer viewer : p.getServer().getPlayerList().getPlayers()) {
            title(viewer, "victory_title", "victory_subtitle");
            viewer.sendSystemMessage(Component.translatable("message.noellesroles.roulette_devil.victory", p.getName(), d.wins).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
            RoleUtils.playSound(viewer, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, net.minecraft.sounds.SoundSource.MASTER, 1, .6f);
        }
        RoleUtils.customWinnerWin(p.serverLevel(), GameUtils.WinStatus.CUSTOM, ModRoles.ROULETTE_DEVIL.identifier().getPath(), OptionalInt.of(ModRoles.ROULETTE_DEVIL.color()));
    }
    @Override public boolean allowDeath(Player victim, Player killer, ResourceLocation reason, boolean body) {
        return !GameUtils.isGameStarted || RouletteDevilSessions.inDuel(victim.getUUID()) || RouletteDevilSessions.settlingDeath(victim.getUUID());
    }
    @Override public boolean afterShieldAllowDeath(Player victim, Player killer, ResourceLocation reason, boolean body) {
        return allowDeath(victim, killer, reason, body);
    }
    @Override public void onInit(MinecraftServer server, ServerPlayer p) {
        super.onInit(server, p);
        var d = data(p);
        if (d != null) d.startingPlayers = SREGameWorldComponent.KEY.get(p.level()).getStartingPlayerCount();
    }
    @Override public void onRemove(ServerPlayer p) {
        RouletteDevilSessions.cancel(p.getUUID(), p.getServer()); removeVoices(p); p.setInvulnerable(false);
    }
    @Override public GameUtils.WinStatus checkWin(ServerPlayer p, GameUtils.WinStatus original) {
        var d = data(p);
        return d != null && !d.refugee.pending() && !refugeeActive(p) && d.wins >= d.required()
                ? GameUtils.WinStatus.CUSTOM : GameUtils.WinStatus.NOT_MODIFY;
    }
    @Override public void win(ServerPlayer p) { declareVictory(p); }
    @Override public boolean didPlayerWin(ServerPlayer p, boolean original, GameUtils.WinStatus status) {
        var d = data(p);
        return status == GameUtils.WinStatus.CUSTOM && d != null && d.wins >= d.required()
                && identifier().getPath().equals(SREGameRoundEndComponent.KEY.get(p.level()).CustomWinnerID);
    }
}
