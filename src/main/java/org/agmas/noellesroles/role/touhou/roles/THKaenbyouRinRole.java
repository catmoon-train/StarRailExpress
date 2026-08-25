package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public class THKaenbyouRinRole extends TouhouRole {
    public static final int COLLECT_COOLDOWN = 20 * 10;

    public THKaenbyouRinRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries(@Nullable Player player) {
        ArrayList<ShopEntry> SHOP = new ArrayList<>();
        SHOP.add(new KaenbyouRinShopEntry(TMMItems.CROWBAR.getDefaultInstance(), 3, ShopEntry.Type.TOOL));
        SHOP.add(new KaenbyouRinShopEntry(TMMItems.REVOLVER.getDefaultInstance(), 6, ShopEntry.Type.TOOL));
        SHOP.add(new KaenbyouRinShopEntry(FunnyItems.SHISIYE.getDefaultInstance(), 10, ShopEntry.Type.TOOL));
        SHOP.add(new KaenbyouRinShopEntry(TMMItems.KNIFE.getDefaultInstance(), 12, ShopEntry.Type.TOOL));
        SHOP.add(new KaenbyouRinShopEntry(TMMItems.DEFENSE_VIAL.getDefaultInstance(), 14, ShopEntry.Type.TOOL));
        SHOP.add(new KaenbyouRinShopEntry(TMMItems.BAT.getDefaultInstance(), 20, ShopEntry.Type.TOOL));
        return SHOP;
    }

    public static class KaenbyouRinShopEntry extends ShopEntry {
        final int boneNeeds;

        public KaenbyouRinShopEntry(ItemStack stack, int priceOfBones, Type type) {
            super(stack, 0, type);
            this.boneNeeds = priceOfBones;
        }

        public int priceOfBones() {
            return boneNeeds;
        }

        @Override
        public boolean canBuy(Player player) {
            if (MCItemsUtils.countItem(player, Items.BONE) >= boneNeeds) {
                return true;
            } else {
                setFailedMessage(Component.translatable("message.noellesroles.kaenbyou_rin.buy.not_enough"));
                return false;
            }
        }

        public ItemStack getTrueStack() {
            return super.stack();
        }

        @Override
        public ItemStack stack() {
            var it = super.stack();
            var lores = it.getOrDefault(DataComponents.LORE, new ItemLore(List.of()));
            List<Component> loreLines = new ArrayList<>();
            loreLines.addAll(lores.lines());
            loreLines.addFirst(Component
                    .translatable("message.noellesroles.kaenbyou_rin.buy.lore",
                            Component.literal(String.valueOf(boneNeeds)).withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.LIGHT_PURPLE).withStyle(style->style.withItalic(false)));
            it.set(DataComponents.LORE, new ItemLore(loreLines));
            return it;
        }

        @Override
        public boolean onBuy(Player player) {
            if (MCItemsUtils.countItem(player, Items.BONE) >= boneNeeds) {
                if (MCItemsUtils.insertStackInFreeSlot(player, super.stack())) {
                    MCItemsUtils.clearItem(player, Items.BONE, boneNeeds);
                    return true;
                }
                return false;
            } else {
                setFailedMessage(Component.translatable("message.noellesroles.kaenbyou_rin.buy.not_enough"));
            }
            return false;
        }
    }

    public static void collectBody(Player player, PlayerBodyEntity body) {
        SREAbilityPlayerComponent.KEY.get(player).setCooldown(COLLECT_COOLDOWN);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.kaenbyou_rin.collected")
                        .withStyle(ChatFormatting.LIGHT_PURPLE),
                true);

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.5F);
        UUID targetUid = body.getPlayerUuid();
        int count = 1;
        if (targetUid != null) {
            var target = player.level().getPlayerByUUID(targetUid);
            if (target != null) {
                final var gamecca = SREGameWorldComponent.getInstance(target);
                final SRERole role = gamecca.getRole(targetUid);
                if (role == null) {
                    count = 1;
                } else if (SREGameWorldComponent.isKillerTeamRoleStatic(role)) {
                    count = 2;
                } else if (role.isNeutrals()) {
                    count = 3;
                }
            }
        }
        ItemStack bone = Items.BONE.getDefaultInstance();
        bone.set(DataComponents.MAX_STACK_SIZE, 99);
        bone.setCount(count);
        bone.set(DataComponents.ITEM_NAME, Component.translatable("item_name.noellesroles.kaenbyou_rin.bone"));
        MCItemsUtils.insertOrDropItem(player, bone);
    }

}
