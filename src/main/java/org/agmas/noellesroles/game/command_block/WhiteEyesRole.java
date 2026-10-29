package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.game.command_block.item.GazeRays;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/**
 * 白瞳：闪光只灼到正在看你的人，你自己也会被闪到。
 * 瞳针只能补还在失明里的人。眼睛每隔一会儿会自己亮。
 */
public class WhiteEyesRole extends NormalRole {
    public static final ResourceLocation DEATH_NEEDLE = org.agmas.noellesroles.Noellesroles.id("white_eyes_needle");
    public static final double FLASH_RANGE = 14.0D;
    public static final int VICTIM_BLIND = 70;
    public static final int SELF_BLIND = 20;

    public WhiteEyesRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood,
            int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.WHITE_EYES, RoleSkill.skill(
                SRE.id("white_eyes_flash"),
                "skill.noellesroles.white_eyes.flash",
                WhiteEyesRole::flash)
                .cooldownSeconds(30).announceToSelf().showOnHud(true).build());
    }

    private static boolean flash(RoleSkill.RoleSkillContext context) {
        ServerPlayer self = context.player();
        if (self.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        int hit = 0;
        for (ServerPlayer other : self.serverLevel().players()) {
            if (other == self || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            if (self.distanceToSqr(other) > FLASH_RANGE * FLASH_RANGE) {
                continue;
            }
            if (!GazeRays.lookingAt(other, self, 0.45D)) {
                continue;
            }
            other.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, VICTIM_BLIND, 0, false, false, true));
            other.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, true));
            hit++;
        }
        self.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SELF_BLIND, 0, false, false, true));
        LegendFx.ring(self.serverLevel(), self.position().add(0.0D, 1.0D, 0.0D), 3.2F, LegendFx.FLASH);
        self.serverLevel().playSound(null, self.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F,
                1.6F);
        self.displayClientMessage(Component.translatable(
                hit == 0 ? "message.noellesroles.white_eyes.miss" : "message.noellesroles.white_eyes.hit", hit)
                .withStyle(ChatFormatting.WHITE), true);
        return true;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(ModItems.EYE_NEEDLE.getDefaultInstance(), 90, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
        return shop;
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of();
    }
}
