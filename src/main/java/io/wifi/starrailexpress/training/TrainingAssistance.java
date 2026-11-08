package io.wifi.starrailexpress.training;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.command.StaminaCommand;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.init.ModEffects;
import java.util.HashSet;

/** 只租用本营主人的辅助效果；不覆盖职业/道具效果，也不向假人或其他营地发放。 */
final class TrainingAssistance {
    private final EffectLease stamina = new EffectLease(ModEffects.INFINITE_STAMINA);
    private final EffectLease speed = new EffectLease(MobEffects.MOVEMENT_SPEED);
    private ServerPlayer owner;
    private boolean staminaEnabled;

    void update(ServerPlayer player, TrainingSettings settings, long now) {
        if (owner != player) { clear(now); owner = player; }
        if (player == null) return;
        boolean alive = TrainingRules.isAlive(player);
        stamina.update(player, alive && settings.unlimitedStamina, now);
        speed.update(player, alive && settings.speedBoost, now);
        if (alive && settings.noCooldown) resetCooldowns(player);
        boolean enabled = alive && settings.unlimitedStamina;
        if (enabled && !staminaEnabled) {
            var role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
            float capacity = role == null ? 100 : role.getMaxSprintTime(player);
            if (capacity >= 0) StaminaCommand.setStamina(player, capacity * ModEffects.getStaminaCapacityMultiplier(player));
        }
        staminaEnabled = enabled;
    }

    void clear(long now) {
        if (owner != null) { stamina.clear(owner, now); speed.clear(owner, now); }
        owner = null;
        staminaEnabled = false;
    }

    /** 仅清除技能/物品冷却，不补充技能次数、持续时间、弹药或其他职业状态。 */
    static void resetCooldowns(ServerPlayer player) {
        var ability = SREAbilityPlayerComponent.KEY.get(player);
        if (ability.hasAnyCooldown()) ability.resetAllCooldowns();
        for (var item : new HashSet<>(player.getCooldowns().cooldowns.keySet())) player.getCooldowns().removeCooldown(item);
    }

    private static final class EffectLease {
        private static final int DURATION = 60;
        final Holder<MobEffect> effect;
        MobEffectInstance applied;
        long until;

        EffectLease(Holder<MobEffect> effect) { this.effect = effect; }

        void update(ServerPlayer player, boolean enabled, long now) {
            if (!enabled) { clear(player, now); return; }
            MobEffectInstance current = player.getEffect(effect);
            boolean owned = current == applied && current != null && matches(current.getAmplifier(), current.getDuration(),
                    current.isAmbient(), current.isVisible(), current.showIcon(), until, now);
            // 已有外部同级或更强效果就借用它，等待自然结束；不缩短、覆盖或定期移除它。
            if (current != null && !owned) return;
            if (owned && current.getDuration() > DURATION - 20) return;
            player.addEffect(new MobEffectInstance(effect, DURATION, 0, true, false, true));
            applied = player.getEffect(effect);
            until = now + DURATION;
        }

        void clear(ServerPlayer player, long now) {
            MobEffectInstance current = player.getEffect(effect);
            if (applied != null && current != null) {
                CompoundTag original = (CompoundTag) current.save();
                CompoundTag remaining = stripOwned(original, until, now);
                if (!original.equals(remaining)) {
                    player.removeEffect(effect);
                    if (remaining != null) {
                        remaining.putString("id", original.getString("id"));
                        MobEffectInstance restored = MobEffectInstance.load(remaining);
                        if (restored != null) player.addEffect(restored);
                    }
                }
            }
            applied = null;
        }
    }

    static boolean matches(int amplifier, int duration, boolean ambient, boolean visible, boolean icon, long until, long now) {
        return amplifier == 0 && duration > 0 && ambient && !visible && icon
                && Math.abs((long) duration - (until - now)) <= 2;
    }

    /** 从当前效果及隐藏链中仅剥离仍匹配本次短租约的节点；其他效果时长完全不变。 */
    static CompoundTag stripOwned(CompoundTag original, long until, long now) {
        CompoundTag copy = original.copy();
        CompoundTag hidden = original.contains("hidden_effect")
                ? stripOwned(original.getCompound("hidden_effect"), until, now) : null;
        if (hidden == null) copy.remove("hidden_effect"); else copy.put("hidden_effect", hidden);
        if (matches(copy.getInt("amplifier"), copy.getInt("duration"), copy.getBoolean("ambient"),
                !copy.contains("show_particles") || copy.getBoolean("show_particles"),
                copy.getBoolean("show_icon"), until, now)) return hidden;
        return copy;
    }
}
