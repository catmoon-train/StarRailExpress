package org.agmas.noellesroles.game.roles.innocence.decree;

import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerMorph;
import io.wifi.starrailexpress.event.GameplayExtensionEvents;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnRoleSkillUse;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.morph.MorphAppearance;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.DecreeC2SPacket;
import org.agmas.noellesroles.packet.DecreeS2CPacket;
import org.agmas.noellesroles.role.ModRoles;

import java.util.*;

/** 律令的服务端权威领域；行为判定实时读距离，不依赖可被清除的药水标记。 */
public final class DecreeService {
    private static final Map<UUID, Domain> DOMAINS = new HashMap<>();
    private static boolean registered;
    private static final ThreadLocal<Boolean> DELAYED_RESOLUTION = ThreadLocal.withInitial(() -> false);

    /** 已排队的延迟结算不能因为之后新颁布禁杀律令而被追溯取消。 */
    public static void runDelayedDamage(Runnable damage) {
        boolean previous = DELAYED_RESOLUTION.get();
        DELAYED_RESOLUTION.set(true);
        try { damage.run(); }
        finally {
            if (previous) DELAYED_RESOLUTION.set(true);
            else DELAYED_RESOLUTION.remove();
        }
    }

    private static final class Domain {
        final ServerPlayer caster;
        final Decree decree;
        final long end;
        final Set<UUID> viewers = new HashSet<>();
        Domain(ServerPlayer caster, Decree decree) {
            this.caster = caster;
            this.decree = decree;
            end = caster.level().getGameTime() + decree.durationTicks();
        }
        boolean active() {
            return caster.level().getGameTime() < end && !caster.isRemoved()
                    && caster.server.getPlayerList().getPlayer(caster.getUUID()) == caster
                    && GameUtils.isPlayerAliveAndSurvival(caster)
                    && SREGameWorldComponent.KEY.get(caster.level()).isRole(caster, ModRoles.DECREE_MAGE)
                    && SREGameWorldComponent.KEY.get(caster.level()).isRunning();
        }
        boolean contains(Player player) {
            return player.level() == caster.level()
                    && decree.contains(player.distanceToSqr(caster), player == caster);
        }
    }

    private DecreeService() {}

    public static void register() {
        if (registered) return;
        registered = true;
        RoleSkill.register(ModRoles.DECREE_MAGE, DecreeMageRole.skill());
        ServerPlayNetworking.registerGlobalReceiver(DecreeC2SPacket.ID,
                (payload, context) -> context.server().execute(() -> DecreeMageRole.cast(context.player(), payload.decreeId())));
        ServerTickEvents.END_WORLD_TICK.register(DecreeService::tick);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Domain domain = DOMAINS.remove(handler.player.getUUID());
            if (domain != null) end(domain);
            DecreeEffects.update(handler.player, Map.of());
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> DOMAINS.clear());
        OnGameEnd.EVENT.register((level, game) -> clear(level));
        io.wifi.starrailexpress.event.ItemGameplayEvents.ALLOW_GUN_FIRE.register((player, target) -> !blocksGun(player));
        io.wifi.starrailexpress.event.ItemGameplayEvents.ALLOW_KNIFE_ATTACK.register((player, target) ->
                !isRestricted(player, Decree.KNIFE) && !isRestricted(player, Decree.ITEM_USE));
        io.wifi.starrailexpress.event.GameUtilityEvents.ALLOW_REVIVE.register((player, destination) ->
                !blocksRevival(player) && (destination == null || !isRestrictedAt(player.level(), destination, Decree.REVIVAL)));
        OnRoleSkillUse.BEFORE.register((player, role) -> !isRestricted(player, Decree.SKILL));
        AllowPlayerMorph.EVENT.register((player, appearance) -> appearance.equals(MorphAppearance.NONE)
                || !isRestricted(player, Decree.MORPH));
        io.wifi.starrailexpress.event.AllowPlayerDisguise.EVENT.register((player, state) ->
                state.equals(io.wifi.starrailexpress.disguise.EntityDisguiseState.NONE) || !isRestricted(player, Decree.MORPH));
        GameplayExtensionEvents.ALLOW_KILL_AFTER_ATTRIBUTION.register((victim, body, killer, reason, forced) ->
                killer == null || DELAYED_RESOLUTION.get() || isDelayedDamage(reason.getPath()) || !isRestricted(killer, Decree.MURDER));
        UseItemCallback.EVENT.register((player, level, hand) -> blocksUse(player, player.getItemInHand(hand))
                ? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand)));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> blocksUse(player, player.getItemInHand(hand))
                ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> blocksUse(player, player.getItemInHand(hand))
                ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                isRestricted(player, Decree.KNIFE) && isKnife(player.getItemInHand(hand))
                        ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    /** 已有毒、流血、燃烧、延迟爆炸不受短时禁止杀人律令追溯影响。 */
    public static boolean isDelayedDamage(String reason) {
        return reason.contains("poison") || reason.contains("bleed") || reason.contains("burn")
                || reason.contains("fire_tick") || reason.contains("c4_explosion") || reason.contains("grenade")
                || reason.contains("infection") || reason.contains("curse") || reason.contains("delayed")
                || reason.equals("wizard_fire_arrow");
    }

    public static boolean isRestricted(Player player, Decree decree) {
        if (player == null || player.level().isClientSide || DOMAINS.isEmpty()) return false;
        for (Domain domain : DOMAINS.values()) {
            if (domain.decree == decree && domain.active() && domain.contains(player)) return true;
        }
        return false;
    }

    public static boolean blocksUse(Player player, ItemStack stack) {
        return isRestricted(player, Decree.ITEM_USE)
                || (isRestricted(player, Decree.KNIFE) && isKnife(stack))
                || (isRestricted(player, Decree.GUN) && isGun(stack));
    }

    public static boolean isKnife(ItemStack stack) {
        return stack.getItem() instanceof io.wifi.starrailexpress.content.item.KnifeItem;
    }

    public static boolean isGun(ItemStack stack) {
        return stack.is(io.wifi.starrailexpress.index.tag.TMMItemTags.GUNS)
                || stack.getItem() instanceof io.wifi.starrailexpress.content.item.RevolverItem
                || stack.getItem() instanceof io.wifi.starrailexpress.content.item.SniperRifleItem
                || stack.getItem() instanceof org.agmas.noellesroles.content.item.ZeroOneFiveGunItem
                || stack.getItem() instanceof org.agmas.noellesroles.content.item.TrapperNetGunItem;
    }

    public static boolean blocksGun(Player player) {
        return isRestricted(player, Decree.GUN) || isRestricted(player, Decree.ITEM_USE);
    }

    public static boolean blocksTeleport(Player... players) {
        for (Player player : players) if (isRestricted(player, Decree.TELEPORT)) return true;
        return false;
    }

    public static boolean blocksRevival(Player... players) {
        for (Player player : players) {
            if (isRestricted(player, Decree.REVIVAL)) return true;
            if (player instanceof ServerPlayer serverPlayer) {
                var body = org.agmas.noellesroles.component.DefibrillatorComponent.findPlayerBodyEntity(serverPlayer);
                if (body != null && isRestrictedAt(body.level(), body.position(), Decree.REVIVAL)) return true;
            }
        }
        return false;
    }

    public static boolean isRestrictedAt(net.minecraft.world.level.Level level, net.minecraft.world.phys.Vec3 pos, Decree decree) {
        if (level.isClientSide) return false;
        for (Domain domain : DOMAINS.values()) {
            if (domain.decree == decree && domain.caster.level() == level && domain.active()
                    && decree.contains(domain.caster.position().distanceToSqr(pos), false)) return true;
        }
        return false;
    }

    public static void cast(ServerPlayer caster, Decree decree) {
        Domain previous = DOMAINS.remove(caster.getUUID());
        if (previous != null) end(previous);
        Domain domain = new Domain(caster, decree);
        DOMAINS.put(caster.getUUID(), domain);
        updateViewers(domain, true);
        tickEffects(caster.serverLevel());
    }

    private static void tick(ServerLevel level) {
        Iterator<Domain> iterator = DOMAINS.values().iterator();
        while (iterator.hasNext()) {
            Domain domain = iterator.next();
            if (domain.caster.level() != level) continue;
            if (!domain.active()) {
                end(domain);
                iterator.remove();
            } else updateViewers(domain, false);
        }
        tickEffects(level);
    }

    private static void updateViewers(Domain domain, boolean impact) {
        Set<UUID> current = new HashSet<>();
        int remaining = (int) Math.max(0, domain.end - domain.caster.level().getGameTime());
        for (ServerPlayer player : domain.caster.serverLevel().players()) {
            if (player != domain.caster && !domain.contains(player)) continue;
            current.add(player.getUUID());
            if (impact || !domain.viewers.contains(player.getUUID()) || player.level().getGameTime() % 20 == 0) {
                ServerPlayNetworking.send(player, new DecreeS2CPacket(domain.caster.getUUID(), domain.decree.id(), remaining, impact));
            }
        }
        for (UUID id : domain.viewers) if (!current.contains(id)) sendEnd(domain, id);
        domain.viewers.clear();
        domain.viewers.addAll(current);
    }

    private static void sendEnd(Domain domain, UUID id) {
        ServerPlayer viewer = domain.caster.server.getPlayerList().getPlayer(id);
        if (viewer != null) ServerPlayNetworking.send(viewer,
                new DecreeS2CPacket(domain.caster.getUUID(), domain.decree.id(), 0, false));
    }

    private static void end(Domain domain) {
        for (UUID id : domain.viewers) sendEnd(domain, id);
    }

    public static void clear(ServerLevel level) {
        DOMAINS.values().removeIf(domain -> {
            if (domain.caster.level() != level) return false;
            end(domain);
            return true;
        });
        for (ServerPlayer player : level.players()) {
            DecreeEffects.update(player, Map.of());
            player.removeEffect(ModEffects.DECREE_RANGE);
        }
    }

    private static void tickEffects(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            Map<Holder<MobEffect>, Integer> wanted = new HashMap<>();
            int remaining = 0;
            for (Domain domain : DOMAINS.values()) {
                if (domain.caster.level() != level || (player != domain.caster && !domain.contains(player))) continue;
                remaining = Math.max(remaining, (int) (domain.end - level.getGameTime()));
                if (domain.decree == Decree.LIGHT && player == domain.caster) continue;
                switch (domain.decree) {
                    case MOVEMENT -> wanted.put(MobEffects.MOVEMENT_SLOWDOWN, 3);
                    case SPEECH -> {
                        wanted.put(ModEffects.VOICE_SILENCE, 0);
                        wanted.put(ModEffects.CHAT_BAN, 0);
                    }
                    case INVENTORY -> wanted.put(ModEffects.INVENTORY_BANED, 0);
                    case SKILL -> wanted.put(ModEffects.SKILL_BANED, 0);
                    case ITEM_USE -> wanted.put(ModEffects.USED_BANED, 0);
                    case LIGHT -> {
                        wanted.put(MobEffects.DARKNESS, 0);
                        wanted.put(MobEffects.BLINDNESS, 0);
                    }
                    case BLINDNESS -> {
                        player.removeEffect(MobEffects.DARKNESS);
                        player.removeEffect(MobEffects.BLINDNESS);
                    }
                    default -> { }
                }
            }
            if (remaining > 0) {
                var marker = player.getEffect(ModEffects.DECREE_RANGE);
                if (marker == null || Math.abs(marker.getDuration() - remaining) > 2) {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(ModEffects.DECREE_RANGE, remaining, 0, false, false, true));
                }
            } else player.removeEffect(ModEffects.DECREE_RANGE);
            // 清明优先于重叠领域中的禁光，效果托管不再补回刚清除的失明。
            if (isRestricted(player, Decree.BLINDNESS)) {
                wanted.remove(MobEffects.DARKNESS);
                wanted.remove(MobEffects.BLINDNESS);
            }
            DecreeEffects.update(player, wanted);
            if (isRestricted(player, Decree.SPRINT)) player.setSprinting(false);
            if (isRestricted(player, Decree.ITEM_USE)) player.stopUsingItem();
            if (isRestricted(player, Decree.INVENTORY) && player.containerMenu != player.inventoryMenu) player.closeContainer();
        }
    }

    public static void blocked(ServerPlayer player) {
        if (player.tickCount % 20 == 0) player.displayClientMessage(
                Component.translatable("message.noellesroles.decree.blocked"), true);
    }
}
