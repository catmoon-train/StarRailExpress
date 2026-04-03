package org.agmas.noellesroles.roles.imitator;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import org.agmas.noellesroles.component.*;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.candlebearer.CandleBearerPlayerComponent;
import org.agmas.noellesroles.roles.ghost.GhostPlayerComponent;
import org.agmas.noellesroles.roles.noise_maker.NoiseMakerPlayerComponent;
import org.agmas.noellesroles.roles.recaller.RecallerPlayerComponent;
import org.agmas.noellesroles.roles.thief.ThiefPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Registry mapping role IDs to their ability execution logic.
 * This allows the Imitator to use copied abilities without relying on isRole checks.
 */
public class ImitatorSkillRegistry {
    private static final Map<ResourceLocation, BiConsumer<ServerPlayer, UUID>> IMITATABLE_SKILLS = new HashMap<>();

    public static boolean isImitatable(ResourceLocation roleId) {
        return IMITATABLE_SKILLS.containsKey(roleId);
    }

    public static boolean execute(ResourceLocation roleId, ServerPlayer player, @Nullable UUID target) {
        var handler = IMITATABLE_SKILLS.get(roleId);
        if (handler != null) {
            handler.accept(player, target);
            return true;
        }
        return false;
    }

    public static void registerAll() {
        // Phantom - invisibility
        IMITATABLE_SKILLS.put(ModRoles.PHANTOM_ID, (player, target) -> {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                    NoellesRolesConfig.HANDLER.instance().phantomInvisibilityDuration * 20, 0, true, false, true));
        });

        // Cleaner - clear items in radius
        IMITATABLE_SKILLS.put(ModRoles.CLEANER_ID, (player, target) -> {
            var items = player.level().getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(5.), (p) -> true);
            for (var it : items) {
                it.discard();
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F,
                    1.0F + player.level().random.nextFloat() * 0.1F - 0.05F);
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.cleaner.cleanned", items.size())
                    .withStyle(ChatFormatting.GOLD), true);
        });

        // Bomber - buy bomb
        IMITATABLE_SKILLS.put(ModRoles.BOMBER_ID, (player, target) -> {
            BomberPlayerComponent bomberComp = ModComponents.BOMBER.get(player);
            bomberComp.buyBomb();
        });

        // Noisemaker - use ability
        IMITATABLE_SKILLS.put(ModRoles.NOISEMAKER_ID, (player, target) -> {
            NoiseMakerPlayerComponent comp = ModComponents.NOISEMAKER.get(player);
            comp.useAbility();
        });

        // Ghost - use ability
        IMITATABLE_SKILLS.put(ModRoles.GHOST_ID, (player, target) -> {
            GhostPlayerComponent comp = GhostPlayerComponent.KEY.get(player);
            comp.useAbility();
        });

        // Candle Bearer - use ability
        IMITATABLE_SKILLS.put(ModRoles.CANDLE_BEARER_ID, (player, target) -> {
            CandleBearerPlayerComponent comp = CandleBearerPlayerComponent.KEY.get(player);
            comp.useAbility();
        });

        // Blood Feudist - toggle effects
        IMITATABLE_SKILLS.put(ModRoles.BLOOD_FEUDIST_ID, (player, target) -> {
            BloodFeudistPlayerComponent comp = ModComponents.BLOOD_FEUDIST.get(player);
            comp.toggleEffects();
        });

        // Recaller - mark/teleport
        IMITATABLE_SKILLS.put(ModRoles.RECALLER_ID, (player, target) -> {
            RecallerPlayerComponent comp = RecallerPlayerComponent.KEY.get(player);
            SREPlayerShopComponent shopComp = SREPlayerShopComponent.KEY.get(player);
            if (!comp.placed) {
                comp.setPosition();
            } else if (shopComp.balance >= 100) {
                shopComp.balance -= 100;
                shopComp.sync();
                comp.teleport();
            }
        });

        // Thief - use ability
        IMITATABLE_SKILLS.put(ModRoles.THIEF_ID, (player, target) -> {
            ThiefPlayerComponent comp = ThiefPlayerComponent.KEY.get(player);
            comp.useAbility();
        });

        // Clockmaker - use skill
        IMITATABLE_SKILLS.put(ModRoles.CLOCKMAKER_ID, (player, target) -> {
            ClockmakerPlayerComponent comp = ModComponents.CLOCKMAKER.get(player);
            comp.useSkill();
        });

        // Sea King - water AOE paralysis
        IMITATABLE_SKILLS.put(ModRoles.SEA_KING_ID, (player, target) -> {
            final double radius = 20.0D;
            final int duration = 5 * 20;

            AABB range = player.getBoundingBox().inflate(radius);
            for (ServerPlayer t : player.serverLevel().getEntitiesOfClass(
                    ServerPlayer.class, range,
                    p -> !p.getUUID().equals(player.getUUID()) && GameUtils.isPlayerAliveAndSurvival(p))) {
                if (player.distanceToSqr(t) > radius * radius) continue;
                if (!(t.isInWater() || t.isUnderWater())) continue;
                t.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, duration, 0, false, true, false));
                t.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0, false, true, false));
            }
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.TRIDENT_RETURN, SoundSource.MASTER, 5.0F, 1.0F);
        });
    }
}
