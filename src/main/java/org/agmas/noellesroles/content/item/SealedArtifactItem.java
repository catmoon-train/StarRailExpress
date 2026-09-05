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

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * 封印物：强力收益与危险副作用并存。效果见 {@link SealedArtifactHandler}。
 */
public class SealedArtifactItem extends Item {
    private static final Set<String> USABLE = Set.of(
            "sealed_vanishing_cloak",
            "sealed_doorless_key",
            "sealed_last_match");

    private final Tier tier;
    private final String translationKey;

    public SealedArtifactItem(Properties properties, Tier tier, String translationKey) {
        super(properties);
        this.tier = tier;
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public boolean isUsable() {
        return USABLE.contains(translationKey);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slotId,
            boolean isSelected) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            SealedArtifactHandler.tick(player, stack, this);
        }
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player user,
            @NotNull InteractionHand hand) {
        if (!isUsable()) {
            return InteractionResultHolder.pass(user.getItemInHand(hand));
        }
        return SealedArtifactHandler.use(level, user, hand, this);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.sealed_artifact.tooltip.category")
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("item.noellesroles.sealed_artifact.tooltip.tier", tier.displayName())
                .withStyle(tier.formatting));
        tooltip.add(Component.translatable("item.noellesroles." + translationKey + ".tooltip.boon")
                .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("item.noellesroles." + translationKey + ".tooltip.curse")
                .withStyle(ChatFormatting.RED));
        if (isUsable()) {
            tooltip.add(Component.translatable("item.noellesroles.sealed_artifact.tooltip.use")
                    .withStyle(ChatFormatting.YELLOW));
        }
        tooltip.add(Component.translatable("item.noellesroles.sealed_artifact.tooltip.warning")
                .withStyle(ChatFormatting.GRAY));
    }

    public Tier getTier() {
        return tier;
    }

    public enum Tier {
        FRAGMENT("fragment", ChatFormatting.GRAY),
        RELIC("relic", ChatFormatting.BLUE),
        ANOMALY("anomaly", ChatFormatting.LIGHT_PURPLE),
        CALAMITY("calamity", ChatFormatting.GOLD);

        private final String translationKey;
        private final ChatFormatting formatting;

        Tier(String translationKey, ChatFormatting formatting) {
            this.translationKey = translationKey;
            this.formatting = formatting;
        }

        public Component displayName() {
            return Component.translatable("item.noellesroles.sealed_artifact.tier." + translationKey);
        }
    }
}
