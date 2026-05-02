package io.wifi.starrailexpress.mixin.client;

import io.wifi.events.day_night_fight.DNFClockItem;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

import static io.wifi.starrailexpress.cca.SRETrainWorldComponent.TimeOfDay.NOON;
import static io.wifi.starrailexpress.cca.SRETrainWorldComponent.TimeOfDay.SUNDOWN;

@Mixin(DNFClockItem.class)
public abstract class DNFClockTooltipMixin extends Item{
    public DNFClockTooltipMixin(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("message.dnf.clock.tooltip").withStyle(ChatFormatting.GRAY));

        // 如果有上下文,显示当前天数和时间
        if (context instanceof Item.TooltipContext itemContext && Minecraft.getInstance().level != null) {
            Level world =Minecraft.getInstance().level;
            SRETrainWorldComponent.TimeOfDay timeOfDay = SRETrainWorldComponent.KEY.get(world).getTimeOfDay();

            String timeText = switch (timeOfDay) {
                case DAY -> "§e白天";
                case NIGHT -> "§c夜晚";
                case MIDNIGHT -> "§4午夜";
                case SUNDOWN -> "§6黄昏";
                case NOON -> "§a正午";
            };

            tooltip.add(Component.literal("§7时间: " + timeText).withStyle(ChatFormatting.GRAY));
        }
    }
}
