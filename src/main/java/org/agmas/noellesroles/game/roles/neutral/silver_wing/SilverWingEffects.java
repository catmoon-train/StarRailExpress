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

package org.agmas.noellesroles.game.roles.neutral.silver_wing;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.OnShieldBroken;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.BreakArmorPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 银翼效果落地：电磁脉冲命中、机械小鸟光环与爆炸。
 */
public final class SilverWingEffects {
    public static final ResourceLocation EMP_SKILL_ID = SRE.id("silver_wing_emp");

    private SilverWingEffects() {
    }

    public static void applyEmp(ServerPlayer target) {
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            return;
        }
        int itemBanTicks = SilverWingRules.ticks(SilverWingRules.EMP_ITEM_BAN_SECONDS);
        int slowTicks = SilverWingRules.ticks(SilverWingRules.EMP_SLOWNESS_SECONDS);
        target.addEffect(ModEffects.of(ModEffects.USED_BANED, itemBanTicks, 0, false, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowTicks,
                SilverWingRules.EMP_SLOWNESS_AMPLIFIER, false, false, true));
    }

    public static void applyEmpUseCooldownIfReady(ServerPlayer thrower) {
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(thrower);
        boolean onCooldown = ability.getSkillState(EMP_SKILL_ID).cooldown > 0;
        if (SilverWingRules.shouldApplyEmpUseCooldown(onCooldown)) {
            ability.setSkillCooldown(EMP_SKILL_ID, SilverWingRules.ticks(SilverWingRules.EMP_USE_COOLDOWN_SECONDS));
        }
    }

    public static void applyBirdAura(ServerPlayer target) {
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0, false, false, true));
    }

    public static void applyBirdExplosion(ServerPlayer target, Player owner, RandomSource random, Vec3 explosionPos) {
        if (!GameUtils.isPlayerAliveAndSurvival(target) || target == owner) {
            return;
        }
        int blindnessTicks = SilverWingRules.ticks(SilverWingRules.BIRD_BLINDNESS_SECONDS);
        int itemBanTicks = SilverWingRules.ticks(SilverWingRules.BIRD_ITEM_BAN_SECONDS);
        int skillBanTicks = SilverWingRules.ticks(SilverWingRules.BIRD_SKILL_BAN_SECONDS);
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blindnessTicks, 0, false, false, true));
        target.addEffect(ModEffects.of(ModEffects.USED_BANED, itemBanTicks, 0, false, false, false));
        target.addEffect(ModEffects.of(ModEffects.SKILL_BANED, skillBanTicks, 0, false, false, false));

        SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(target);
        mood.addMood(-SilverWingRules.BIRD_MOOD_DRAIN);

        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(target);
        shop.setBalance(SilverWingRules.deductGold(shop.balance));

        SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(target);
        if (armor.hasArmor() && SilverWingRules.shouldBreakShield(random.nextFloat())) {
            // hasArmor() 已经保证至少有一层可被消耗的护盾，这里消耗后必定触发破盾反馈
            armor.triggerAndRemoveArmor(1);
            playShieldBreakFeedback(target, owner);
            OnShieldBroken.EVENT.invoker().onShieldBroken(target, owner);
        }

        DamageSource source = owner instanceof ServerPlayer attacker
                ? target.damageSources().playerAttack(attacker)
                : target.damageSources().explosion(null, owner);
        target.hurt(source, SilverWingRules.BIRD_HURT_DAMAGE);

        double[] knock = SilverWingRules.horizontalKnockback(
                target.getX() - explosionPos.x, target.getZ() - explosionPos.z);
        target.push(knock[0], SilverWingRules.BIRD_KNOCKBACK_Y, knock[1]);
        target.hurtMarked = true;
        target.connection.send(new ClientboundSetEntityMotionPacket(target.getId(), target.getDeltaMovement()));
    }

    /**
     * 破盾反馈：与 {@link io.wifi.starrailexpress.api.GameMode} 的破盾流程保持一致 ——
     * 受害者本人听到破盾音效，破盾者收到 {@link BreakArmorPayload}（音效 + 屏幕提示）。
     */
    private static void playShieldBreakFeedback(ServerPlayer victim, Player breaker) {
        victim.playNotifySound(TMMSounds.ITEM_PSYCHO_ARMOUR, SoundSource.MASTER, 5.0F, 1.0F);
        if (breaker instanceof ServerPlayer serverBreaker && serverBreaker != victim) {
            ServerPlayNetworking.send(serverBreaker,
                    new BreakArmorPayload(victim.getX(), victim.getY(), victim.getZ()));
        }
    }
}
