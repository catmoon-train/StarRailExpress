package io.wifi.events.day_night_fight;


import com.mojang.authlib.minecraft.client.MinecraftClient;
import io.wifi.events.day_night_fight.cca.DNFPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * DNF时钟物品
 * 拿着时显示DNF的天数和时间
 */
public class DNFClockItem extends Item {
    public DNFClockItem(Properties properties) {
        super(properties);
    }



    /**
     * 获取时钟显示文本(用于HUD渲染)
     */
    public static Component getClockDisplayText(Player player) {
        if (player == null || player.level() == null) {
            return Component.literal("时钟");
        }

        Level world = player.level();
        
        // 检查是否是DNF模式
        if (!DNF.isDayNightFightMode(world)) {
            return Component.literal("时钟");
        }

        DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
        int day = component.getDnfDay() + 1; // 从1开始计数
        
        SRETrainWorldComponent.TimeOfDay timeOfDay = SRETrainWorldComponent.KEY.get(world).getTimeOfDay();
        
        String dayText = String.format("§e第%d天", day);
        String timeText = switch (timeOfDay) {
            case DAY -> "§f☀ 白天";
            case NIGHT -> "§9☾ 夜晚";
            case MIDNIGHT -> "§4☾ 午夜";
            case SUNDOWN -> "§6◐ 黄昏";
            case NOON -> "§a☀️正午";

        };

        return Component.literal(dayText + " " + timeText);
    }
}
