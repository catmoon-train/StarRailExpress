package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.game.forensic.ForensicCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.game.roles.innocent.great_detective.DetectiveClue;
import org.agmas.noellesroles.utils.RoleUtils;

/**
 * 客户端：把一条 {@link DetectiveClue} 渲染成本地化的可读文本。
 * 文本本地化在客户端进行，遵循"禁止硬编码字符串、用 translatable"的约定。
 */
public final class DeductionClueText {

    private DeductionClueText() {
    }

    public static Component render(DetectiveClue clue) {
        switch (clue.type()) {
            case WEAPON -> {
                ForensicCategory cat = parseCategory(clue.value());
                return Component.translatable("screen.noellesroles.great_detective.clue.weapon",
                        Component.translatable(cat.langKey).withStyle(ChatFormatting.WHITE));
            }
            case ROLE -> {
                ResourceLocation id = ResourceLocation.tryParse(clue.value());
                SRERole role = id == null ? null : RoleUtils.getRole(id);
                Component name = role != null ? RoleUtils.getRoleName(role) : Component.literal(clue.value());
                return Component.translatable("screen.noellesroles.great_detective.clue.role",
                        name.copy().withStyle(ChatFormatting.WHITE));
            }
            case MODIFIER -> {
                SREModifier mod = findModifier(clue.value());
                Component name = mod != null ? RoleUtils.getModifierName(mod) : Component.literal(clue.value());
                return Component.translatable("screen.noellesroles.great_detective.clue.modifier",
                        name.copy().withStyle(ChatFormatting.WHITE));
            }
            case NAME -> {
                return Component.translatable("screen.noellesroles.great_detective.clue.name",
                        Component.literal(clue.value()).withStyle(ChatFormatting.WHITE));
            }
            case ROOM -> {
                return Component.translatable("screen.noellesroles.great_detective.clue.room",
                        Component.literal(clue.value()).withStyle(ChatFormatting.WHITE));
            }
        }
        return Component.literal(clue.value());
    }

    private static ForensicCategory parseCategory(String value) {
        try {
            return ForensicCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            return ForensicCategory.UNKNOWN;
        }
    }

    private static SREModifier findModifier(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return null;
        }
        for (SREModifier m : HMLModifiers.MODIFIERS) {
            if (m != null && m.identifier().equals(rl)) {
                return m;
            }
        }
        return null;
    }
}
