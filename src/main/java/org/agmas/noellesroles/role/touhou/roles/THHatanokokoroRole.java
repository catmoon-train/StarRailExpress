package org.agmas.noellesroles.role.touhou.roles;

import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.touhou.THMiscRoles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.ShopContent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

public class THHatanokokoroRole extends TouhouRole {

    public THHatanokokoroRole(ResourceLocation identifier, int color, RoleType roleType, MoodType moodType,
            int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, roleType, moodType, maxSprintTime, canSeeTime);
    }

    public static void registerSkills() {
        // 喜happiness：额外获得自己当前金币30%的金币，暂时无法使用商店，持续45秒
        RoleSkill.register(THMiscRoles.HATA_NO_KOKORO,
                RoleSkill.skill(SRE.id("hata_no_kokoro/happiness"), "skill.noellesroles.hata_no_kokoro.happiness",
                        (ctx) -> {
                            final var player = ctx.player();
                            var shopCca = SREPlayerShopComponent.KEY.get(player);
                            shopCca.addToBalance((int) (shopCca.balance * 0.3f));
                            player.addEffect(ModEffects.of(ModEffects.SHOP_BANNED, 45 * 20, 0, false, false, true));
                            return true;
                        }).charges(1)
                        .cooldownSeconds(60)
                        .recordReplay()
                        .showOnHud(true)
                        .announceToSelf()
                        .build(),
                RoleSkill.skill(SRE.id("hata_no_kokoro/anger"), "skill.noellesroles.hata_no_kokoro.anger",
                        (ctx) -> {
                            final var player = ctx.player();
                            player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, 30 * 20, 1, false, false, true));
                            player.addEffect(ModEffects.of(MobEffects.DIG_SPEED, 30 * 20, 1, false, false, true));
                            player.addEffect(ModEffects.of(ModEffects.NO_INSTINCT, 30 * 20, 1, false, false, true));
                            return true;
                        }).charges(1)
                        .cooldownSeconds(60)
                        .recordReplay()
                        .showOnHud(true)
                        .announceToSelf()
                        .build(),
                RoleSkill.skill(SRE.id("hata_no_kokoro/sorrow"), "skill.noellesroles.hata_no_kokoro.sorrow",
                        (ctx) -> {
                            final var player = ctx.player();
                            final var armorCca = SREArmorPlayerComponent.KEY.get(player);
                            armorCca.setTimedArmor(1, 30 * 20);
                            player.addEffect(ModEffects.of(ModEffects.NO_STAMINA, 30 * 20, 1, false, false, true));
                            return true;
                        }).charges(1)
                        .cooldownSeconds(60)
                        .recordReplay()
                        .showOnHud(true)
                        .announceToSelf()
                        .build(),
                RoleSkill.skill(SRE.id("hata_no_kokoro/joy"), "skill.noellesroles.hata_no_kokoro.joy",
                        (ctx) -> {
                            final var player = ctx.player();
                            {
                                for (int i = 0; i < 9; ++i) {
                                    ItemStack stack = player.getInventory().getItem(i);
                                    if (!stack.isEmpty()) {
                                        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
                                            player.getCooldowns().removeCooldown(stack.getItem());
                                        }
                                    }
                                }
                                for (var shops : ShopContent.getShopEntries(THMiscRoles.HATA_NO_KOKORO)) {
                                    var stack = shops.stack();
                                    if (stack != null && !stack.isEmpty()) {
                                        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
                                            player.getCooldowns().removeCooldown(stack.getItem());
                                        }
                                    }
                                }
                            }
                            return true;
                        }).charges(1)
                        .cooldownSeconds(120)
                        .recordReplay()
                        .showOnHud(true)
                        .announceToSelf()
                        .build());
    }
}
