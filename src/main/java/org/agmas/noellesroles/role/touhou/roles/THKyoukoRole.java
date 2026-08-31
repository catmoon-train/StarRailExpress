package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/*
# 幽谷响子
商店购买：
- 响符「Mountain Echo」
购买后立刻使用，使周围玩家发光5s 100金币
- 响符「Mountain Echo Scramble」
购买后立刻使用，使周围玩家发光7s且无法使用道具喝移动 375 金币
- 响符「Power Resonance」
购买后立刻使用，使下一发子弹可以造成射击落点附近的玩家10s发光和10s减速 50金币
 */
public class THKyoukoRole extends TouhouRole {

    public THKyoukoRole(ResourceLocation identifier, int color, RoleType roleType, MoodType moodType, int maxSprintTime,
            boolean canSeeTime) {
        super(identifier, color, roleType, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> arr = new ArrayList<>();

        // "item_stack.noellesroles.kyouko.echo":"响符「Mountain Echo」",
        // "item_stack.noellesroles.kyouko.echo_scramble":"响符「Mountain Echo Scramble」",
        // "item_stack.noellesroles.kyouko.power":"响符「Power Resonance」",
        arr.add(new ShopEntry(
                createItemStackWithName(Items.PAPER, Component.translatable("item_stack.noellesroles.kyouko.echo"),
                        Component.translatable("item_stack.noellesroles.kyouko.echo.lore")),
                150, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(Player player) {
                for (final var p : player.level().players()) {
                    if (!GameUtils.isPlayerAliveAndSurvival(p))
                        continue;
                    if (p.distanceToSqr(player) > 6 * 6)
                        continue;
                    if (p.getUUID().equals(player.getUUID()))
                        continue;
                    p.addEffect(ModEffects.of(MobEffects.GLOWING, 5 * 20, 1, false, false, true));
                }
                return true;
            }
        });

        
        arr.add(new ShopEntry(
                createItemStackWithName(Items.MAP, Component.translatable("item_stack.noellesroles.kyouko.echo_scramble"),
                        Component.translatable("item_stack.noellesroles.kyouko.echo_scramble.lore")),
                400, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(Player player) {
                for (final var p : player.level().players()) {
                    if (!GameUtils.isPlayerAliveAndSurvival(p))
                        continue;
                    if (p.distanceToSqr(player) > 6 * 6)
                        continue;
                    if (p.getUUID().equals(player.getUUID()))
                        continue;
                    p.addEffect(ModEffects.of(MobEffects.GLOWING, 7 * 20, 1, false, false, true));
                    p.addEffect(ModEffects.of(ModEffects.USED_BANED, 7 * 20, 1, false, false, true));
                    p.addEffect(ModEffects.of(ModEffects.MOVE_BANED, 7 * 20, 1, false, false, true));
                }
                return true;
            }
        });

        
        arr.add(new ShopEntry(
                createItemStackWithName(Items.IRON_SWORD, Component.translatable("item_stack.noellesroles.kyouko.power"),
                        Component.translatable("item_stack.noellesroles.kyouko.power.lore")),
                100, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(Player player) {
                for (final var p : player.level().players()) {
                    if (!GameUtils.isPlayerAliveAndSurvival(p))
                        continue;
                    if (p.distanceToSqr(player) > 3 * 3)
                        continue;
                    if (p.getUUID().equals(player.getUUID()))
                        continue;
                    p.addEffect(ModEffects.of(MobEffects.GLOWING, 5 * 20, 1, false, false, true));
                    p.addEffect(ModEffects.of(MobEffects.MOVEMENT_SLOWDOWN, 5 * 20, 0, false, false, true));
                }
                return true;
            }
        });
        return arr;
    }

    private static ItemStack createItemStackWithName(Item item, Component name, @Nullable Component lore) {
        return createItemStackWithName(item, name, List.of(lore));
    }

    private static ItemStack createItemStackWithName(Item item, Component name, @Nullable List<Component> lore) {
        var stack = item.getDefaultInstance();
        stack.set(DataComponents.ITEM_NAME, name);
        if (lore != null) {
            var lores = new ArrayList<Component>();
            for (var t : lore) {
                lores.add(Component.literal("")
                        .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)).append(t));
            }
            stack.set(DataComponents.LORE, new ItemLore(lores));
        }
        return stack;
    }

}
