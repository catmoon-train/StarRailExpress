package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.init.ModItems;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;

public class RiotShieldHandler {
    public static void register() {
        AllowPlayerDeathWithKiller.EVENT.register(RiotShieldHandler::allowDeath);
    }

    public static boolean allowDeath(Player victim, Player attacker,
            ResourceLocation deathReason) {
        if (attacker == null)
            return true;
        if ((attacker.isSpectator())
                || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(victim))
            return true;
        if (!deathReason.equals(GameConstants.DeathReasons.REVOLVER)
                && !deathReason.equals(GameConstants.DeathReasons.ARROW)
                && !deathReason.equals(GameConstants.DeathReasons.DERRINGER)
                && !deathReason.equals(GameConstants.DeathReasons.TRIDENT)
                && !deathReason.equals(GameConstants.DeathReasons.KNIFE)
                && !deathReason.equals(GameConstants.DeathReasons.BAT)
                && !deathReason.equals(SRE.TMMId("bat"))
                && !deathReason.equals(GameConstants.DeathReasons.GRENADE))
            return true;
        // 如果受害者正在举盾且主手/副手持有我们的防暴盾，则阻挡并损坏盾
        if (!victim.isUsingItem())
            return true;

        ItemStack main = victim.getMainHandItem();
        ItemStack off = victim.getOffhandItem();
        ItemStack shieldStack = null;
        boolean mainHand = false;
        if (main != null && main.is(ModItems.RIOT_SHIELD)) {
            shieldStack = main;
            mainHand = true;
        } else if (off != null && off.is(ModItems.RIOT_SHIELD)) {
            shieldStack = off;
            mainHand = false;
        }

        if (shieldStack == null)
            return true;

        // 检查是否来自玩家正面
        Vec3 look = victim.getLookAngle();
        Vec3 dir = attacker.position().subtract(victim.position());
        dir = new Vec3(dir.x, 0, dir.z);
        double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (len == 0)
            return true;
        dir = dir.scale(1.0 / len);
        Vec3 l2 = new Vec3(look.x, 0, look.z);
        double llen = Math.sqrt(l2.x * l2.x + l2.z * l2.z);
        if (llen == 0)
            return true;
        l2 = l2.scale(1.0 / llen);
        double dot = l2.x * dir.x + l2.z * dir.z;
        if (dot < Math.cos(Math.toRadians(60.0))) {
            // 不是正面（允许一定夹角）
            return true;
        }

        // 阻挡：播放 entity.iron_golem.damage 音效并损坏盾
        victim.level().playSound(null, victim.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 1.0F,
                1.0F);
        if (!victim.isCreative()) {
            shieldStack.hurtAndBreak(1, victim, mainHand ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                    : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }

        return false;
    }

    /**
     * 检查玩家是否正在举防暴盾牌
     */
    public static boolean isBlockingWithRiotShield(Player player) {
        if (!player.isUsingItem()) {
            return false;
        }
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return (main != null && main.is(ModItems.RIOT_SHIELD)) || 
               (off != null && off.is(ModItems.RIOT_SHIELD));
    }

    /**
     * 获取举盾提示消息
     */
    public static Component getShieldBlockingMessage() {
        return Component.translatable("message.noellesroles.shield_blocking")
                .withStyle(ChatFormatting.YELLOW);
    }
}
