package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role.bouns.roles.FatFishRole;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 小鱼干 —— 大肥鱼专属商店里的小零食。
 *
 * <p>吃下恢复一点体力（{@value #STAMINA_RESTORE}）；同时也是**可以拿去投喂大肥鱼**的食物
 * （{@code FatFishRole} 的投喂判定要求手里物品带 FOOD 组件）。
 *
 * <p>体力是两端各自模拟的（原版那套），所以恢复必须在客户端与服务端都执行 ——
 * 与 {@code ShilijiaItem} 同样的处理。
 */
public class SmallDriedFishItem extends Item {

    /** 一份小鱼干恢复的体力 */
    public static final float STAMINA_RESTORE = 40.0F;

    private static final FoodProperties FOOD = new FoodProperties.Builder()
            .nutrition(2).saturationModifier(0.2F).alwaysEdible().fast().build();

    public SmallDriedFishItem(Properties properties) {
        super(properties.food(FOOD));
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level,
            @NotNull LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (entity instanceof Player player) {
            FatFishRole.restoreStamina(player, STAMINA_RESTORE);
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
