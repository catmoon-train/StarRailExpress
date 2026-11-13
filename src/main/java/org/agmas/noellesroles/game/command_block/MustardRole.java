package org.agmas.noellesroles.game.command_block;

import java.util.Comparator;
import java.util.List;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.DynamicShopComponent;
import io.wifi.starrailexpress.cca.ParticipationComponent;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import io.wifi.starrailexpress.event.AllowPlayerPunching;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnKillPlayerTriggered;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.ShopEvents;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.MustardPuppetRoleData;
import org.agmas.noellesroles.role_data.killer.MustardRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

/** 芥末的复活、升级、召回与替伤逻辑。 */
public class MustardRole extends NormalRole {
    private static final String CORE_ID = "noellesroles:puppet_core";

    public MustardRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood,
            int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerEvents() {
        RoleSkill.register(ModRoles.MUSTARD, RoleSkill.skill(SRE.id("mustard_recall"),
                "skill.noellesroles.mustard.recall", context -> recall(context.player()))
                .cooldownSeconds(MustardRules.SKILL_COOLDOWN_SECONDS).announceToSelf().showOnHud(true).build());
        UseEntityCallback.EVENT.register(MustardRole::useCore);
        AllowPlayerPunching.EVENT.register(player -> !player.hasEffect(ModEffects.SAFE_TIME)
                && (isPuppet(player) || player.getMainHandItem().is(ModItems.PUPPET_HAND)));
        ResourceLocation meleePhase = SRE.id("mustard_melee");
        AttackEntityCallback.EVENT.addPhaseOrdering(meleePhase, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.register(meleePhase, MustardRole::attack);
        OnKillPlayerTriggered.EVENT.register((victim, body, killer, reason, forced) -> {
            if (!forced && victim instanceof ServerPlayer self
                    && !DreadlordRole.isTrain(reason)
                    && CommandBlockCombat.allowScriptDeath(victim, killer, reason, false)) {
                ServerPlayer puppet = takeGuard(self);
                if (puppet != null) {
                    GameUtils.killPlayer(puppet, body, killer, reason, false);
                    return TrueFalseResult.FALSE;
                }
            }
            return TrueFalseResult.PASS;
        });
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, reason) -> {
            if (victim instanceof ServerPlayer self) {
                if (isMustard(self)) endPuppets(self);
                if (isPuppet(self)) EntityDisguise.clear(self);
            }
        });
        ShopEvents.BEFORE_PAYMENT.register((buyer, entry, currency, price) -> {
            MustardRoleData data = RoleData.getNullable(MustardRoleData.class, buyer);
            if (data != null && entry.stack().is(ModItems.PUPPET_CORE)) {
                if (data.corePurchases < Integer.MAX_VALUE) data.corePurchases++;
                DynamicShopComponent.KEY.get(buyer).setSteppedPrice(CORE_ID, MustardRules.CORE_PRICE,
                        MustardRules.CORE_PRICE_STEP, true, data.corePurchases);
                data.sync();
            }
        });
        OnGameEnd.EVENT.register((level, game) -> {
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                if (isPuppet(player)) EntityDisguise.clear(player);
                MustardRoleData data = RoleData.getNullable(MustardRoleData.class, player);
                if (data != null) {
                    data.guardUntil = 0;
                    data.corePurchases = 0;
                    clearCorePrice(player);
                    data.sync();
                }
            }
        });
    }

    public static boolean isMustard(Player player) {
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.MUSTARD);
    }

    public static boolean isPuppet(Player player) {
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.MUSTARD_PUPPET);
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of(ModItems.PUPPET_CORE.getDefaultInstance());
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return List.of(new ShopEntry(ModItems.PUPPET_HAND.getDefaultInstance(), 100, ShopEntry.Type.WEAPON),
                new ShopEntry(ModItems.PUPPET_CORE.getDefaultInstance(), MustardRules.CORE_PRICE, ShopEntry.Type.TOOL));
    }

    private static InteractionResult useCore(Player actor, Level level, InteractionHand hand,
            Entity target, EntityHitResult hit) {
        ItemStack core = actor.getItemInHand(hand);
        if (!core.is(ModItems.PUPPET_CORE) || !isMustard(actor)) return InteractionResult.PASS;
        if (!GameUtils.isPlayerAliveAndSurvival(actor) || !canAct(actor)
                || actor.distanceToSqr(target) > 16.0D || !actor.hasLineOfSight(target)) {
            return InteractionResult.FAIL;
        }
        if (!(target instanceof PlayerBodyEntity) && !(target instanceof Player puppet && isPuppet(puppet))) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(actor instanceof ServerPlayer owner)) return InteractionResult.FAIL;
        if (target instanceof ServerPlayer puppet) {
            MustardPuppetRoleData data = RoleData.getNullable(MustardPuppetRoleData.class, puppet);
            if (!GameUtils.isPlayerAliveAndSurvival(puppet) || data == null || !owner.getUUID().equals(data.owner)) {
                return InteractionResult.FAIL;
            }
            if (data.upgrades < Integer.MAX_VALUE) data.upgrades++;
            data.sync();
            core.consume(1, owner);
            owner.displayClientMessage(Component.translatable("message.noellesroles.mustard.upgraded",
                    puppet.getDisplayName(), MustardRules.puppetDamage(data.upgrades)), true);
            return InteractionResult.CONSUME;
        }
        PlayerBodyEntity body = (PlayerBodyEntity) target;
        var bodyData = PlayerBodyEntityComponent.KEY.get(body);
        if (body.isRemoved() || bodyData.isFakeBody || bodyData.nonInteractable || body.getPlayerUuid() == null) {
            return InteractionResult.FAIL;
        }
        ServerPlayer revived = owner.server.getPlayerList().getPlayer(body.getPlayerUuid());
        if (revived == null || !revived.isSpectator() || revived.level() != level
                || SREAbilityPlayerComponent.KEY.get(revived).hasExited()
                || !ParticipationComponent.KEY.get(level).isParticipating(revived)) {
            owner.displayClientMessage(Component.translatable("message.noellesroles.mustard.invalid_body"), true);
            return InteractionResult.FAIL;
        }
        GameUtils.revivePlayer(revived, body.getX(), body.getY(), body.getZ());
        if (revived.isSpectator()) {
            owner.displayClientMessage(Component.translatable("message.noellesroles.mustard.invalid_body"), true);
            return InteractionResult.FAIL;
        }
        revived.getInventory().clearContent();
        RoleUtils.changeRole(revived, ModRoles.MUSTARD_PUPPET);
        MustardPuppetRoleData data = RoleData.getNullable(MustardPuppetRoleData.class, revived);
        if (data == null) return InteractionResult.FAIL;
        data.owner = owner.getUUID();
        data.sync();
        DreamHealthComponent.KEY.get(revived).init();
        revived.setHealth(revived.getMaxHealth());
        EntityDisguise.disguise(revived, EntityType.IRON_GOLEM, null, 0,
                player -> !GameUtils.isPlayerAliveAndSurvival(player) || !isPuppet(player));
        body.discard();
        core.consume(1, owner);
        SRE.REPLAY_MANAGER.recordPlayerRevival(revived.getUUID(), ModRoles.MUSTARD_PUPPET);
        RoleUtils.sendWelcomeAnnouncement(revived);
        revived.displayClientMessage(Component.translatable("message.noellesroles.mustard.revived",
                owner.getDisplayName()), false);
        return InteractionResult.CONSUME;
    }

    private static boolean canAct(Player player) {
        var game = SREGameWorldComponent.KEY.get(player.level());
        return game.isRunning() && game.isSkillAvailable
                && !io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(player.level()).isTimeFrozen()
                && !player.hasEffect(ModEffects.SAFE_TIME) && !player.hasEffect(ModEffects.USED_BANED)
                && !player.hasEffect(ModEffects.SKILL_BANED);
    }

    public static List<ServerPlayer> puppets(ServerPlayer owner) {
        return owner.server.getPlayerList().getPlayers().stream()
                .filter(player -> GameUtils.isPlayerAliveAndSurvival(player))
                .filter(player -> {
                    var data = RoleData.getNullable(MustardPuppetRoleData.class, player);
                    return data != null && owner.getUUID().equals(data.owner);
                }).toList();
    }

    public static boolean recall(ServerPlayer owner) {
        MustardRoleData data = RoleData.getNullable(MustardRoleData.class, owner);
        if (data == null || !GameUtils.isPlayerAliveAndSurvival(owner)) return false;
        List<ServerPlayer> puppets = puppets(owner);
        if (puppets.isEmpty()) {
            owner.displayClientMessage(Component.translatable("message.noellesroles.mustard.no_puppets"), true);
            return false;
        }
        for (ServerPlayer puppet : puppets) {
            puppet.teleportTo(owner.serverLevel(), owner.getX(), owner.getY(), owner.getZ(),
                    puppet.getYRot(), puppet.getXRot());
            puppet.fallDistance = 0;
        }
        data.guardUntil = GameUtils.getTicksFromGameStart(owner.level()) + MustardRules.GUARD_TICKS;
        data.sync();
        owner.displayClientMessage(Component.translatable("message.noellesroles.mustard.guard_ready"), true);
        return true;
    }

    /** 只有找到同维度存活傀儡时才消耗替伤，先消耗再转移，避免重入。 */
    private static ServerPlayer takeGuard(ServerPlayer owner) {
        MustardRoleData data = RoleData.getNullable(MustardRoleData.class, owner);
        if (data == null || !GameUtils.isPlayerAliveAndSurvival(owner)
                || !data.hasGuard(GameUtils.getTicksFromGameStart(owner.level()))) return null;
        ServerPlayer nearest = puppets(owner).stream().filter(p -> p.level() == owner.level())
                .min(Comparator.comparingDouble(owner::distanceToSqr)).orElse(null);
        if (nearest != null) {
            data.guardUntil = 0;
            data.sync();
        }
        return nearest;
    }

    public static boolean redirectNativeDamage(ServerPlayer owner, DamageSource source, float amount) {
        if (!(amount > 0.0F) || GameUtils.isForcedDeathInProgress()) return false;
        MustardRoleData data = RoleData.getNullable(MustardRoleData.class, owner);
        if (data != null && data.redirectedVirtualAt == owner.level().getGameTime()
                && data.redirectedVirtualTarget != null && amount <= 1.0F
                && source.getEntity() == data.redirectedVirtualAttacker) {
            ServerPlayer puppet = data.redirectedVirtualTarget;
            data.redirectedVirtualTarget = null;
            if (GameUtils.isPlayerAliveAndSurvival(puppet)) {
                puppet.invulnerableTime = 0;
                puppet.hurt(source, amount);
            }
            return true;
        }
        ServerPlayer puppet = takeGuard(owner);
        if (puppet == null) return false;
        puppet.invulnerableTime = 0;
        puppet.hurt(source, amount);
        return true;
    }

    public static boolean redirectVirtualDamage(ServerPlayer owner, ServerPlayer attacker, int amount,
            ResourceLocation reason, boolean ignoreVirtualShield, boolean pierceOtherShields) {
        if (amount <= 0 || GameUtils.isForcedDeathInProgress()) return false;
        ServerPlayer puppet = takeGuard(owner);
        if (puppet == null) return false;
        MustardRoleData data = RoleData.getNullable(MustardRoleData.class, owner);
        data.redirectedVirtualAt = owner.level().getGameTime();
        data.redirectedVirtualTarget = puppet;
        data.redirectedVirtualAttacker = attacker;
        DreamHealthComponent.KEY.get(puppet).hurt(attacker, amount, reason, ignoreVirtualShield, pierceOtherShields);
        return true;
    }

    public static void clearCorePrice(ServerPlayer owner) {
        DynamicShopComponent.KEY.get(owner).clearModifier(CORE_ID);
    }

    public static void endPuppets(ServerPlayer owner) {
        for (ServerPlayer puppet : puppets(owner)) {
            if (SREGameWorldComponent.KEY.get(puppet.level()).isRunning()) endPuppet(puppet);
            else EntityDisguise.clear(puppet);
        }
    }

    public static void endPuppet(ServerPlayer puppet) {
        EntityDisguise.clear(puppet);
        GameUtils.killPlayer(puppet, true, null, GameConstants.DeathReasons.GENERIC, true);
    }

    /** 傀儡和傀儡之手的命中统一取消原版攻击，只扣虚拟血量。 */
    private static InteractionResult attack(Player actor, Level level, InteractionHand hand,
            Entity target, EntityHitResult hit) {
        boolean puppet = isPuppet(actor);
        if (!puppet && !actor.getMainHandItem().is(ModItems.PUPPET_HAND)) return InteractionResult.PASS;
        if (actor.isSpectator() || !canAct(actor)) return InteractionResult.FAIL;
        if (!(target instanceof ServerPlayer) && !level.isClientSide) return InteractionResult.PASS;
        if (!(target instanceof Player victim) || !GameUtils.isPlayerAliveAndSurvival(victim)
                || actor == victim || actor.distanceToSqr(victim) > 16.0D
                || !actor.hasLineOfSight(victim) || victim.hasEffect(ModEffects.SAFE_TIME)
                || actor.getCooldowns().isOnCooldown(Items.BARRIER)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(actor instanceof ServerPlayer attacker)) return InteractionResult.FAIL;
        var data = RoleData.getNullable(MustardPuppetRoleData.class, actor);
        int damage = puppet && data != null ? MustardRules.puppetDamage(data.upgrades) : MustardRules.HAND_DAMAGE;
        actor.getCooldowns().addCooldown(Items.BARRIER, 10);
        actor.resetAttackStrengthTicker();
        boolean hurt = DreamHealthComponent.KEY.get(victim).hurt(attacker, damage,
                GameConstants.DeathReasons.GENERAL_ATTACK);
        if (hurt && puppet && data != null && data.upgrades > 0 && GameUtils.isPlayerAliveAndSurvival(victim)) {
            victim.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 20, 0, false, false, true));
            victim.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, 20, 0, false, false, true));
            victim.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 20, 0, false, false, true));
        }
        if (puppet) {
            actor.level().broadcastEntityEvent(actor, (byte) 4);
            actor.level().playSound(null, actor.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS_NO_ITEM_USED;
    }
}
