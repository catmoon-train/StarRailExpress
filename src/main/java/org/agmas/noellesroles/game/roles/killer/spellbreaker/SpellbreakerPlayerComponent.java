package org.agmas.noellesroles.game.roles.killer.spellbreaker;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.entity.SilenceTotemEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

public class SpellbreakerPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SpellbreakerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "spellbreaker"),
            SpellbreakerPlayerComponent.class);

    public static final int ABILITY_COOLDOWN = 130 * 20;
    public static final int ABILITY_DURATION = 25 * 20;
    public static final double ABILITY_RADIUS = 50.0D;
    public static final int POTION_DURATION = 15 * 20;
    public static final int HIT_SKILL_BAN_DURATION = 20 * 20;

    private final Player player;

    private UUID activeTotemUuid = null;

    public SpellbreakerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public static void registerEvents() {
//        AttackEntityCallback.EVENT.register((attacker, level, hand, entity, hitResult) -> {
//            if (level.isClientSide || !(attacker instanceof ServerPlayer serverAttacker)) {
//                return InteractionResult.PASS;
//            }
//            if (!(entity instanceof ServerPlayer target) || !GameUtils.isPlayerAliveAndSurvival(target)) {
//                return InteractionResult.PASS;
//            }
//
//            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
//            if (!gameWorld.isRole(serverAttacker, ModRoles.SPELLBREAKER)) {
//                return InteractionResult.PASS;
//            }
//
//            SpellbreakerPlayerComponent attackerComponent = KEY.get(serverAttacker);
//            if (attackerComponent.potionTicks <= 0 || !isNonKiller(target, gameWorld)) {
//                return InteractionResult.PASS;
//            }
//
//
//            target.displayClientMessage(
//                    Component.translatable("message.noellesroles.spellbreaker.next_skill_marked")
//                            .withStyle(ChatFormatting.LIGHT_PURPLE),
//                    true);
//            serverAttacker.displayClientMessage(
//                    Component.translatable("message.noellesroles.spellbreaker.hit_marked", target.getName())
//                            .withStyle(ChatFormatting.DARK_PURPLE),
//                    true);
//            return InteractionResult.PASS;
//        });
    }

    public void useAbility() {
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        if (!gameWorld.isRunning() || !gameWorld.isRole(player, ModRoles.SPELLBREAKER)
                || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
        if (ability.cooldown > 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("tip.noellesroles.cooldown", ability.cooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        int affected = 0;
        AABB area = player.getBoundingBox().inflate(ABILITY_RADIUS);
        for (ServerPlayer target : serverLevel.getEntitiesOfClass(ServerPlayer.class, area,
                p -> p != serverPlayer && GameUtils.isPlayerAliveAndSurvival(p))) {
            if (target.distanceToSqr(player) > ABILITY_RADIUS * ABILITY_RADIUS || !isNonKiller(target, gameWorld)) {
                continue;
            }

            target.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, ABILITY_DURATION, 0, false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, ABILITY_DURATION, 0, false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.DREAMCORE_FILTER, ABILITY_DURATION, 0, false, false, false));
            target.displayClientMessage(
                    Component.translatable("message.noellesroles.spellbreaker.area_silenced")
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    true);
            affected++;
        }

        ability.setCooldown(ABILITY_COOLDOWN);
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS,
                1.0F, 0.7F);
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.spellbreaker.ability_used", affected)
                        .withStyle(ChatFormatting.DARK_PURPLE),
                true);
    }



    public boolean consumePendingSkillFail(ServerPlayer serverPlayer) {
        if(!serverPlayer.hasEffect(ModEffects.NEXT_SKILL_BANED))return false;

        serverPlayer.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, HIT_SKILL_BAN_DURATION, 0, false, false, true));
        serverPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HIT_SKILL_BAN_DURATION, 0, false, false, true));
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.spellbreaker.skill_failed",HIT_SKILL_BAN_DURATION/20)
                        .withStyle(ChatFormatting.RED),
                true);
        serverPlayer.playNotifySound(SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F);
        sync();
        return true;
    }

    public void setActiveTotem(SilenceTotemEntity totem) {
        this.activeTotemUuid = totem.getUUID();
        sync();
    }

    public boolean isActiveTotem(UUID uuid) {
        return uuid != null && uuid.equals(activeTotemUuid);
    }

    public void clearActiveTotem(UUID uuid) {
        if (isActiveTotem(uuid)) {
            activeTotemUuid = null;
            sync();
        }
    }

    public void discardActiveTotem(ServerLevel level) {
        if (activeTotemUuid == null) {
            return;
        }
        Entity entity = level.getEntity(activeTotemUuid);
        if (entity != null) {
            entity.discard();
        }
        activeTotemUuid = null;
        sync();
    }

    public static boolean isNonKiller(Player target, SREGameWorldComponent gameWorld) {
        return gameWorld.getRole(target) != null && !gameWorld.canUseKillerFeatures(target);
    }

    @Override
    public void init() {

        activeTotemUuid = null;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    @Override
    public void serverTick() {

    }

    @Override
    public void clientTick() {

    }

    public void sync() {
        if (!player.level().isClientSide) {
            KEY.sync(player);
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {

        if (activeTotemUuid != null) {
            tag.putUUID("ActiveTotemUuid", activeTotemUuid);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {

        activeTotemUuid = tag.hasUUID("ActiveTotemUuid") ? tag.getUUID("ActiveTotemUuid") : null;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
