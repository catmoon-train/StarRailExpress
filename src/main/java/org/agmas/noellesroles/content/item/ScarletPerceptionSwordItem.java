/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.item.api.SREItemProperties.DropRevolverWhenDead;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.LeftClickKillable;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SkinUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.Random;

public class ScarletPerceptionSwordItem extends SwordItem implements LeftClickKillable, DropRevolverWhenDead {
    static Random random = new Random();
    public static final ResourceLocation BASE_ATTACK_KNOCKBACK_ID = ResourceLocation
            .withDefaultNamespace("base_knockback");

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
        if ((!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(target))
                && !attacker.isCreative()) {
            return InteractionResult.FAIL;
        }
        if (attacker.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.FAIL;
        }
        if (attacker.getAttackStrengthScale(0.75F) < 1F) {
            return InteractionResult.FAIL;
        }
        if (attacker instanceof ServerPlayer serverAttacker && target instanceof ServerPlayer serverTarget) {
            if (!serverAttacker.isCreative()) {
                serverAttacker.getCooldowns().addCooldown(this, GameConstants.getRevolverDefaultTicks());
            }
            GameUtils.killPlayer(serverTarget, true, serverAttacker, SkinUtils.getItemTypeResourceLocation(this));
            serverAttacker.level().playSound(null, serverAttacker.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK,
                    SoundSource.PLAYERS, 3f, random.nextFloat(0f, 2f));
        }
        return InteractionResult.PASS;
    }

    public static ItemAttributeModifiers createAttributes(Tier tier, float i, float f) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, (double) ((float) i + tier.getAttackDamageBonus()),
                                Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, (double) f, Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_KNOCKBACK,
                        new AttributeModifier(BASE_ATTACK_KNOCKBACK_ID, (double) 6f, Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public boolean onServerAttack(ServerPlayer serverAttacker, ServerPlayer serverTarget, ItemStack mainhandItem) {
        return true;
    }
}
