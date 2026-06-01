package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.agmas.noellesroles.init.ModItems;

public class BenevolenceSwordHandler {

    /** san值扣除比例（20%） */
    private static final float SAN_DRAIN_RATIO = 0.2f;

    public static void register() {
        AttackEntityCallback.EVENT.register(BenevolenceSwordHandler::onEntityDamaged);
    }

    private static InteractionResult onEntityDamaged(Player attacker, Level level, InteractionHand hand, Entity entity,
            EntityHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.PASS;
        if (!(entity instanceof Player victim)) return InteractionResult.PASS;
        if (!GameUtils.isPlayerAliveAndSurvival(victim)) return InteractionResult.PASS;

        ItemStack main = attacker.getItemInHand(hand);
        if (!main.is(ModItems.BENEVOLENCE_SWORD)) return InteractionResult.PASS;

        // 造成 1 点伤害
        victim.hurt(attacker.damageSources().playerAttack(attacker), 1.0F);

        // 检查受击者是否是假心情，如果是则不扣除 san 值
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        SRERole victimRole = game.getRole(victim);
        if (victimRole == null || victimRole.getMoodType() != SRERole.MoodType.FAKE) {
            // 不是假心情，扣除 20% san 值
            SREPlayerMoodComponent victimMood = SREPlayerMoodComponent.KEY.get(victim);
            if (victimMood != null) {
                float currentMood = victimMood.getMood();
                victimMood.setMood(currentMood - SAN_DRAIN_RATIO);
            }
        }

        // 耐久消耗
        if (!attacker.isCreative()) {
            final InteractionHand usedHand = hand;
            main.hurtAndBreak(1, attacker,
                    usedHand == InteractionHand.MAIN_HAND ?
                            net.minecraft.world.entity.EquipmentSlot.MAINHAND :
                            net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }

        return InteractionResult.SUCCESS;
    }
}
