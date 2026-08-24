package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public class THKonpakuYoumuRole extends TouhouRole {

    public static final ResourceLocation BASE_ATTACK_DAMAGE_ID = ResourceLocation
            .withDefaultNamespace("base_attack_damage");
    public static final ResourceLocation BASE_ATTACK_SPEED_ID = ResourceLocation
            .withDefaultNamespace("base_attack_speed");

    public THKonpakuYoumuRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        final ArrayList<ShopEntry> SHOP = new ArrayList<>();
        SHOP.add(new ShopEntry(ModItems.YOUMU_SWORD.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
        return SHOP;
    }

    @Override
    public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer,
            ResourceLocation deathReason, boolean forceDeath) {
        super.onDeath(victim, spawnBody, killer, deathReason, forceDeath);
        if (!MCItemsUtils.hasItem(victim, ModItems.YOUMU_SWORD)) {
            victim.drop(io.wifi.starrailexpress.index.TMMItems.REVOLVER.getDefaultInstance().copy(), false);
        }
    }

    public static ItemAttributeModifiers createYoumuSwordAttributes() {

        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, (double) (7),
                                Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, -3f, Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, -1f, Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public void serverTick(ServerPlayer player) {
        final var cca = SREAbilityPlayerComponent.KEY.get(player);
        if (cca.status == 1) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                exitGhost(player);
                return;
            }
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        if (cca.status == 1) {
            tickGhost(player);
        }

    }

    private static boolean shouldGiveEffect(Player player, Holder<MobEffect> effect) {
        if (!player.hasEffect(effect))
            return true;
        if (player.getEffect(effect) == null)
            return true;
        if (player.getEffect(effect).getDuration() <= 50) {
            return true;
        }
        return false;
    }

    private void tickGhost(ServerPlayer player) {
        final var cca = SREAbilityPlayerComponent.KEY.get(player);
        if (cca.duration <= 0) {
            exitGhost(player);
        }
        if (player.level().getGameTime() % 40 == 4) {
            giveEffetcs(player);
        }
    }

    private static void giveEffetcs(ServerPlayer player) {

        if (shouldGiveEffect(player, MobEffects.INVISIBILITY)) {
            player.addEffect(ModEffects.of(MobEffects.INVISIBILITY, 40 * 20, 1, false, false, true));
        }
        if (shouldGiveEffect(player, ModEffects.USED_BANED)) {
            player.addEffect(ModEffects.of(ModEffects.USED_BANED, 40 * 20, 1, false, false, true));
        }
    }

    public static void enterGhost(ServerPlayer player) {
        final var cca = SREAbilityPlayerComponent.KEY.get(player);
        cca.status = 1;
        cca.duration = 30 * 20;
        cca.sync();

        player.displayClientMessage(Component
                .translatable("skill.noellesroles.konpaku_youmu.ghost.tip",
                        Component.translatable("skill.noellesroles.konpaku_youmu.ghost"))
                .withStyle(ChatFormatting.GREEN), true);
        giveEffetcs(player);
    }

    public static void exitGhost(ServerPlayer player) {
        final var cca = SREAbilityPlayerComponent.KEY.get(player);
        if (cca.status < 1) {
            player.displayClientMessage(Component
                    .translatable("skill.noellesroles.konpaku_youmu.ghost.leave.fail")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(ModEffects.USED_BANED);

        player.displayClientMessage(Component
                .translatable("skill.noellesroles.konpaku_youmu.ghost.tip",
                        Component.translatable("skill.noellesroles.konpaku_youmu.ghost.leave"))
                .withStyle(ChatFormatting.RED), true);
        cca.status = -1;
    }

}
