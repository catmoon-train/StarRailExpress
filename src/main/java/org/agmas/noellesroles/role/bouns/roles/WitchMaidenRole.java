package org.agmas.noellesroles.role.bouns.roles;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.MCItemsUtils;

import io.wifi.starrailexpress.api.EggRole;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class WitchMaidenRole extends EggRole {

    public WitchMaidenRole(ResourceLocation identifier, int color, RoleType roleType, MoodType moodType,
            int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, roleType, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of(ModItems.REIMU_GOHEI.getDefaultInstance());
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        var cc = ModItems.DEALER_PACKAGE.getDefaultInstance();
        cc.set(DataComponents.ITEM_NAME, Component.translatable("item_stack.noellesroles.witch_maiden"));
        shop.add(new ShopEntry(cc, 100, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(Player player) {
                int random = player.getRandom().nextInt(0, 100);
                if (random < 5) {
                    MCItemsUtils.insertOrDropItem(player, ModItems.DANMUKU.getDefaultInstance());
                    player.displayClientMessage(
                            Component.translatable("item_stack.witch_maiden.best").withStyle(ChatFormatting.GREEN),
                            true);
                } else if (random < 15) {
                    MCItemsUtils.insertOrDropItem(player, TMMItems.WEAK_DEFENSE_VIAL.getDefaultInstance());
                    player.displayClientMessage(
                            Component.translatable("item_stack.witch_maiden.good").withStyle(ChatFormatting.AQUA),
                            true);
                } else if (random < 65) {
                    player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, 12 * 20, 2, false, false, true));
                    player.displayClientMessage(Component.translatable("item_stack.witch_maiden.just_so_so")
                            .withStyle(ChatFormatting.YELLOW), true);
                } else if (random < 85) {
                    player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SLOWDOWN, 12 * 20, 2, false, false, true));
                    player.displayClientMessage(Component.translatable("item_stack.witch_maiden.bad")
                            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                } else {
                    player.addEffect(ModEffects.of(ModEffects.MOVE_BANED, 8 * 20, 2, false, false, true));
                    player.displayClientMessage(
                            Component.translatable("item_stack.witch_maiden.worst").withStyle(ChatFormatting.RED),
                            true);
                }
                player.getCooldowns().addCooldown(ModItems.DEALER_PACKAGE, 90 * 20);
                return true;
            }
        });
        return shop;
    }
}
