/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.killer.DreamRoleData;

public class RiotShieldHandler {
    /** 格挡判定允许的最大夹角：攻击者需位于受害者正面 120° 扇区内。 */
    private static final double BLOCK_FACING_COS = Math.cos(Math.toRadians(60.0));

    public static void register() {
        AllowPlayerDeathWithKiller.EVENT.register(RiotShieldHandler::allowDeath);
    }

    /**
     * 可格挡的死亡原因白名单（防暴盾牌与武士刀共用）。
     * <p>修改这里即可同时改变防暴盾牌和武士刀的可格挡死亡原因。
     *
     * <p>可格挡的远程/投掷武器：
     * <ul>
     * <li>短管霰弹枪（{@code noellesroles:short_shotgun}）</li>
     * <li>飞斧（{@code noellesroles:throwing_axe}，回退 {@code throwing_axe_hit}）</li>
     * <li>飞刀（{@code noellesroles:throwing_knife}，回退 {@code throwing_knife_hit}）</li>
     * <li>手里剑（{@code noellesroles:ninja_shuriken}）</li>
     * <li>爆炸弩（{@code noellesroles:firework_crossbow}）</li>
     * </ul>
     *
     * <p>走原版攻击路线的武器（见 {@link #isVanillaRouteDeathReason}）也计入白名单，
     * 但它们平时不会进入死亡管线，<b>必须在各自的伤害结算处调用
     * {@link #tryBlockAttack(Player, Player)}</b> 才能逐次格挡；白名单只兜住
     * 「虚拟血量归零的致死一击」。
     */
    public static boolean isBlockableDeathReason(ResourceLocation deathReason) {
        return deathReason.equals(GameConstants.DeathReasons.REVOLVER)
                || deathReason.equals(GameConstants.DeathReasons.ARROW)
                || deathReason.equals(GameConstants.DeathReasons.DERRINGER)
                || deathReason.equals(GameConstants.DeathReasons.TRIDENT)
                || deathReason.equals(GameConstants.DeathReasons.KNIFE)
                || deathReason.equals(GameConstants.DeathReasons.BAT)
                || deathReason.equals(SRE.TMMId("bat"))
                || deathReason.equals(GameConstants.DeathReasons.GRENADE)
                // 短管霰弹枪
                || deathReason.equals(GameConstants.DeathReasons.SHORT_SHOTGUN)
                // 飞斧（实体优先用物品自身注册 id 作为死因，回退到固定 id）
                || deathReason.equals(Noellesroles.id("throwing_axe"))
                || deathReason.equals(Noellesroles.id("throwing_axe_hit"))
                // 飞刀（同上：物品 id + 回退 id）
                || deathReason.equals(Noellesroles.id("throwing_knife"))
                || deathReason.equals(GameConstants.DeathReasons.THROWING_KNIFE_HIT)
                // 手里剑
                || deathReason.equals(Noellesroles.id("ninja_shuriken"))
                // 爆炸弩
                || deathReason.equals(GameConstants.DeathReasons.FIREWORK_CROSSBOW)
                // 走原版攻击路线的武器
                || isVanillaRouteDeathReason(deathReason);
    }

    /**
     * 走原版攻击路线、伤害落在虚拟血量（{@code DreamHealthComponent}）上的武器死因：
     * Dream 铁斧 / Dream 钻石剑 / Dream 重锤 / 武士刀。
     *
     * <p>这些武器每次攻击都绕过死亡管线直接扣虚拟血量，只有虚拟血量归零判死时
     * 才会带着这些死因走到死亡拦截。因此逐次格挡必须由武器自身在扣血前调用
     * {@link #tryBlockAttack(Player, Player)}（防暴盾牌与武士刀都适用）。
     */
    public static boolean isVanillaRouteDeathReason(ResourceLocation deathReason) {
        return deathReason.equals(DreamRoleData.DEATH_REASON_DREAM_AXE)
                || deathReason.equals(DreamDiamondSwordItem.DEATH_REASON)
                || deathReason.equals(DreamMaceItem.DEATH_REASON)
                || deathReason.equals(KatanaItem.DEATH_REASON);
    }

    /**
     * 死亡管线拦截：死因在白名单内且受害者正举盾朝向攻击者时，取消死亡。
     *
     * @return true 允许死亡 / false 已被格挡（取消死亡）
     */
    public static boolean allowDeath(Player victim, Player attacker,
            ResourceLocation deathReason) {
        if (!isBlockableDeathReason(deathReason)) {
            return true;
        }
        return !consumeBlock(victim, attacker);
    }

    /**
     * 供<b>走原版攻击路线</b>的武器（Dream 铁斧 / 钻石剑 / 重锤 / 武士刀、
     * 短管霰弹枪、爆炸弩等）在结算伤害<b>之前</b>调用。
     *
     * <p>这些武器的伤害落在虚拟血量上、不会在每次攻击时进入死亡管线，
     * 因此必须由武器自身在扣血前询问防暴盾牌。
     *
     * <p>与 {@link #allowDeath} 判定完全一致：必须正在举盾（主手或副手）、
     * 攻击者位于正面 120° 扇区内；格挡成功播放音效并消耗 1 点耐久
     * （耐久为 1 时盾牌即碎），创造模式不消耗耐久。
     *
     * @return true 已被格挡，调用方应放弃本次攻击的全部伤害
     */
    public static boolean tryBlockAttack(Player victim, Player attacker) {
        return consumeBlock(victim, attacker);
    }

    /**
     * 格挡核心：检查受害者是否正举防暴盾牌正面朝向攻击者，
     * 成立则播放格挡音效并消耗 1 点耐久。
     *
     * @return true 本次攻击已被格挡
     */
    private static boolean consumeBlock(Player victim, Player attacker) {
        if (attacker == null)
            return false;
        if (attacker.isSpectator()
                || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(victim))
            return false;
        // 如果受害者正在举盾且主手/副手持有我们的防暴盾，则阻挡并损坏盾
        if (!victim.isUsingItem())
            return false;

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
            return false;

        // 检查是否来自玩家正面
        Vec3 look = victim.getLookAngle();
        Vec3 dir = attacker.position().subtract(victim.position());
        dir = new Vec3(dir.x, 0, dir.z);
        double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (len == 0)
            return false;
        dir = dir.scale(1.0 / len);
        Vec3 l2 = new Vec3(look.x, 0, look.z);
        double llen = Math.sqrt(l2.x * l2.x + l2.z * l2.z);
        if (llen == 0)
            return false;
        l2 = l2.scale(1.0 / llen);
        double dot = l2.x * dir.x + l2.z * dir.z;
        if (dot < BLOCK_FACING_COS) {
            // 不是正面（允许一定夹角）
            return false;
        }

        // 阻挡：播放 entity.iron_golem.damage 音效并损坏盾
        victim.level().playSound(null, victim.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 1.0F,
                1.0F);
        if (!victim.isCreative()) {
            shieldStack.hurtAndBreak(1, victim, mainHand ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }

        return true;
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
