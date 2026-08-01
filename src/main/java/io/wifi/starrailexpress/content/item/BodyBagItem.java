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

package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class BodyBagItem extends Item {
    public BodyBagItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity, InteractionHand hand) {
        if (entity instanceof PlayerBodyEntity body) {
            body.discard();
            if (!user.level().isClientSide) {
                user.level().playSound(null, body.getX(), body.getY() + .1f, body.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.5f, 1f + user.level().random.nextFloat() * .1f - .05f);
            }
            if (!user.isCreative()) {
                if (SRE.REPLAY_MANAGER != null) {
                    SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
                }
                user.getItemInHand(hand).shrink(1);
                user.getCooldowns().addCooldown(TMMItems.BODY_BAG,
                        GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.BODY_BAG, 40));
            }

            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
