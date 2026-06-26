package org.agmas.noellesroles.game.roles.neutral.cupid;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.alchemy.PotionContents;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;

import java.util.UUID;

public class CupidPlayerComponent implements RoleComponent {
    public static final ComponentKey<CupidPlayerComponent> KEY = ModComponents.CUPID;

    private final Player player;
    private UUID markedPlayer;

    public CupidPlayerComponent(Player player) {
        this.player = player;
    }

    public static void registerEvents() {
        OnPlayerDeath.EVENT.register((victim, deathReason) -> {
            if (!(victim instanceof ServerPlayer deadPlayer)) {
                return;
            }
            clearMarksPointingTo(deadPlayer);
            CupidPlayerComponent ownComponent = KEY.get(deadPlayer);
            if (ownComponent != null) {
                ownComponent.clearMark();
            }
        });
        ModifierAssigned.EVENT.register((player, modifier) -> {
            if (modifier.equals(SEModifiers.LOVERS) && player instanceof ServerPlayer lover) {
                clearMarksPointingTo(lover);
            }
        });
    }

    public static boolean handleArrowHit(Arrow arrow, ServerPlayer shooter, ServerPlayer target) {
        if (!isRegenerationArrow(arrow)) {
            return false;
        }
        if (!SREGameWorldComponent.KEY.get(shooter.serverLevel()).isRole(shooter, ModRoles.CUPID)) {
            return false;
        }
        KEY.get(shooter).tryMarkOrLink(target);
        arrow.discard();
        return true;
    }

    private static boolean isRegenerationArrow(Arrow arrow) {
        PotionContents contents = arrow.getPotionContents();
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (effect.getEffect().equals(MobEffects.REGENERATION)) {
                return true;
            }
        }
        return false;
    }

    private static void clearMarksPointingTo(ServerPlayer target) {
        for (ServerPlayer player : target.serverLevel().players()) {
            CupidPlayerComponent component = KEY.get(player);
            if (component != null && target.getUUID().equals(component.markedPlayer)) {
                component.clearMark();
            }
        }
    }

    private void tryMarkOrLink(ServerPlayer target) {
        if (!(player instanceof ServerPlayer cupid)) {
            return;
        }
        if (target.equals(cupid) || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(target)) {
            return;
        }
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(cupid.serverLevel());
        if (modifiers.isModifier(target, SEModifiers.LOVERS) || LoversComponent.KEY.get(target).isLover()) {
            clearMarkIfTarget(target.getUUID());
            return;
        }

        ServerPlayer firstTarget = getValidMarkedPlayer(cupid);
        if (firstTarget == null) {
            setMarkedPlayer(target.getUUID());
            target.displayClientMessage(Component.translatable("message.noellesroles.cupid.marked")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        if (firstTarget.getUUID().equals(target.getUUID())) {
            return;
        }
        if (modifiers.isModifier(firstTarget, SEModifiers.LOVERS) || LoversComponent.KEY.get(firstTarget).isLover()) {
            setMarkedPlayer(target.getUUID());
            target.displayClientMessage(Component.translatable("message.noellesroles.cupid.marked")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }

        bindLovers(firstTarget, target, modifiers);
        clearMark();
        cupid.displayClientMessage(Component.translatable("message.noellesroles.cupid.linked",
                firstTarget.getDisplayName(), target.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
    }

    private ServerPlayer getValidMarkedPlayer(ServerPlayer cupid) {
        if (markedPlayer == null) {
            return null;
        }
        Player target = cupid.level().getPlayerByUUID(markedPlayer);
        if (!(target instanceof ServerPlayer serverTarget)
                || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(serverTarget)) {
            clearMark();
            return null;
        }
        return serverTarget;
    }

    private void bindLovers(ServerPlayer first, ServerPlayer second, WorldModifierComponent modifiers) {
        modifiers.addModifier(first.getUUID(), SEModifiers.LOVERS, false);
        modifiers.addModifier(second.getUUID(), SEModifiers.LOVERS, false);
        modifiers.sync();

        LoversComponent firstLover = LoversComponent.KEY.get(first);
        LoversComponent secondLover = LoversComponent.KEY.get(second);
        firstLover.setLover(second.getUUID());
        secondLover.setLover(first.getUUID());
        firstLover.sync();
        secondLover.sync();
        clearMarksPointingTo(first);
        clearMarksPointingTo(second);
    }

    private void clearMarkIfTarget(UUID target) {
        if (target != null && target.equals(markedPlayer)) {
            clearMark();
        }
    }

    public void setMarkedPlayer(UUID markedPlayer) {
        this.markedPlayer = markedPlayer;
        sync();
    }

    public void clearMark() {
        this.markedPlayer = null;
        sync();
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        markedPlayer = null;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (markedPlayer != null) {
            tag.putUUID("MarkedPlayer", markedPlayer);
        }
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        markedPlayer = tag.contains("MarkedPlayer") ? tag.getUUID("MarkedPlayer") : null;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToSyncNbt(tag, registryLookup);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromSyncNbt(tag, registryLookup);
    }
}
