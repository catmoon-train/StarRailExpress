package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.item.api.SREItemProperties.DropRevolverWhenDead;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.LeftClickKillable;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SkinUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

public class ScarletPerceptionSwordItem extends SwordItem implements LeftClickKillable, DropRevolverWhenDead {

    public ScarletPerceptionSwordItem(Properties properties) {
        super(Tiers.GOLD, properties);
    }

    @Override
    public void postHurtEnemy(ItemStack itemStack, LivingEntity livingEntity, LivingEntity le) {
    }

    @Override
    public InteractionResult onTryHurt(Player attacker, Entity targetE, ItemStack mainhandItem) {
        if (!(targetE instanceof Player target))
            return InteractionResult.FAIL;
        if (!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return InteractionResult.FAIL;
        }
        if (attacker.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.FAIL;
        }
        if (attacker.getAttackStrengthScale(0.75F) < 1F) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean onServerAttack(ServerPlayer attacker, ServerPlayer target, ItemStack mainhandItem) {
        if (!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        if (attacker.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        if (attacker.getAttackStrengthScale(0.75F) < 1F) {
            return false;
        }
        if (!attacker.isCreative()) {
            attacker.getCooldowns().addCooldown(this, GameConstants.getRevolverDefaultTicks());
        }
        GameUtils.killPlayer(target, true, attacker, SkinUtils.getItemTypeResourceLocation(this));
        attacker.level().playSound(null, attacker.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 3f, 1f);
        return true;
    }
}
