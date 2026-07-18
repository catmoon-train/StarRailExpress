package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvPart;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 一枚房车配件模块。物品本身只承载「它对应哪种 {@link SixtySecondsRvPart}」，
 * 安装/卸载逻辑在房车管理界面（{@code SixtySecondsRvMenu}）里消耗/返还本物品完成。
 */
public class SixtySecondsRvPartItem extends Item {
    private final SixtySecondsRvPart part;

    public SixtySecondsRvPartItem(SixtySecondsRvPart part, Properties properties) {
        super(properties);
        this.part = part;
    }

    public SixtySecondsRvPart part() {
        return part;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // 说明文案统一放 lang：tooltip.noellesroles.<id>（缺省则不显示，不报错）
        tooltip.add(Component.translatable("tooltip.noellesroles." + registryId())
                .withStyle(ChatFormatting.GRAY));
    }

    private String registryId() {
        return "sixty_seconds_rv_" + part.id();
    }
}
