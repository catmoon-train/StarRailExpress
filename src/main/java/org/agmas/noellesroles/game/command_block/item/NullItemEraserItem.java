package org.agmas.noellesroles.game.command_block.item;

import java.util.Comparator;
import java.util.List;

import org.agmas.noellesroles.game.command_block.NullFx;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** NULL 的一次性消除器：删除八格内最近的一整个掉落物实体，无目标不消耗。 */
public class NullItemEraserItem extends Item {
    public static final double RANGE = 8.0D;

    public NullItemEraserItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer self) || !GameUtils.isPlayerAliveAndSurvival(self)
                || !SREGameWorldComponent.KEY.get(level).isRole(self, ModRoles.ENTITY_NULL)
                || self.hasEffect(ModEffects.SAFE_TIME) || self.hasEffect(ModEffects.USED_BANED)
                || self.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        ItemEntity nearest = level.getEntitiesOfClass(ItemEntity.class, self.getBoundingBox().inflate(RANGE),
                item -> item.isAlive() && self.distanceToSqr(item) <= RANGE * RANGE).stream()
                .min(Comparator.comparingDouble(self::distanceToSqr)).orElse(null);
        if (nearest == null) {
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.no_item"), true);
            return InteractionResultHolder.fail(stack);
        }
        NullFx.erase(self.serverLevel(), nearest.position());
        nearest.discard();
        stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.null_item_eraser.desc"));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
