package net.exmo.sre.sixtyseconds.client;

import io.wifi.starrailexpress.client.SREClient;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsConsumables;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/**
 * 末日60秒模式物品/方块的 tooltip：
 * <ul>
 *   <li><b>介绍描述</b>：所有 {@code sixty_seconds_*} 物品/方块显示 {@code tooltip.noellesroles.<id>} 说明（<b>任何时候</b>都显示，便于了解）。</li>
 *   <li><b>恢复值</b>：食物/水在本模式激活时追加「解渴 +X / 饱食 +Y」。</li>
 * </ul>
 * 参照 {@code io.wifi.starrailexpress.client.util.TMMItemTooltips}。
 */
public final class SixtySecondsTooltips {
    private SixtySecondsTooltips() {
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            appendDescription(stack, lines);
            if (SREClient.gameComponent == null || SixtySecondsMod.MODE == null
                    || SREClient.gameComponent.getGameMode() != SixtySecondsMod.MODE) {
                return;
            }
            int thirst = SixtySecondsConsumables.thirstRestoreOf(stack);
            if (thirst > 0) {
                lines.add(Component.translatable("tooltip.noellesroles.sixty_seconds.thirst", thirst)
                        .withStyle(ChatFormatting.AQUA));
                return;
            }
            int hunger = SixtySecondsConsumables.hungerRestoreOf(stack);
            if (hunger > 0) {
                lines.add(Component.translatable("tooltip.noellesroles.sixty_seconds.hunger", hunger)
                        .withStyle(ChatFormatting.GOLD));
            }
            // 负面食物标注中毒风险
            String poisonKey = poisonKeyOf(stack);
            if (poisonKey != null) {
                lines.add(Component.translatable("tooltip.noellesroles.sixty_seconds.poison_label")
                        .withStyle(ChatFormatting.RED));
                lines.add(Component.translatable(poisonKey).withStyle(ChatFormatting.DARK_RED));
            }
        });
    }

    /** 为 noellesroles 命名空间下的 {@code sixty_seconds_*} 物品/方块追加介绍描述（若定义了对应翻译键）。 */
    private static void appendDescription(net.minecraft.world.item.ItemStack stack,
            java.util.List<Component> lines) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!"noellesroles".equals(id.getNamespace()) || !id.getPath().startsWith("sixty_seconds_")) {
            return;
        }
        String key = "tooltip.noellesroles." + id.getPath();
        if (!I18n.exists(key)) {
            return;
        }
        // 按屏宽逐字换行（兼容无空格的中文），灰色显示
        String text = I18n.get(key);
        var font = Minecraft.getInstance().font;
        int max = 180;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (cur.length() > 0 && font.width(cur.toString() + c) > max) {
                lines.add(Component.literal(cur.toString()).withStyle(ChatFormatting.GRAY));
                cur.setLength(0);
            }
            cur.append(c);
        }
        if (cur.length() > 0) {
            lines.add(Component.literal(cur.toString()).withStyle(ChatFormatting.GRAY));
        }
    }

    /** 返回该食物在 60s 模式中的中毒风险 tooltip 翻译键（无中毒返回 null）。 */
    private static String poisonKeyOf(net.minecraft.world.item.ItemStack stack) {
        var item = stack.getItem();
        if (item == Items.ROTTEN_FLESH) return "tooltip.noellesroles.sixty_seconds.poison.rotten_flesh";
        if (item == Items.POISONOUS_POTATO) return "tooltip.noellesroles.sixty_seconds.poison.poisonous_potato";
        if (item == Items.PUFFERFISH) return "tooltip.noellesroles.sixty_seconds.poison.pufferfish";
        if (item == Items.SPIDER_EYE) return "tooltip.noellesroles.sixty_seconds.poison.spider_eye";
        if (item == Items.CHICKEN) return "tooltip.noellesroles.sixty_seconds.poison.chicken";
        if (item == Items.BEEF || item == Items.PORKCHOP
                || item == Items.MUTTON || item == Items.RABBIT)
            return "tooltip.noellesroles.sixty_seconds.poison.raw_meat";
        return null;
    }
}
