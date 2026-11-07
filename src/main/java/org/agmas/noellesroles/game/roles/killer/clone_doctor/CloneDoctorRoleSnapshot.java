package org.agmas.noellesroles.game.roles.killer.clone_doctor;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.HashMap;
import java.util.Map;

/** 只回收临时职业初始化引入的属性和效果，不全清修饰符、装备或外部增益。 */
final class CloneDoctorRoleSnapshot {
    private record AttributeState(double base, Map<ResourceLocation, AttributeModifier> modifiers) { }
    private final Map<Holder<Attribute>, AttributeState> original = new HashMap<>();
    private final Map<Holder<Attribute>, AttributeState> initialized = new HashMap<>();
    private final Map<Holder<MobEffect>, MobEffectInstance> originalEffects = new HashMap<>();
    private final Map<Holder<MobEffect>, MobEffectInstance> initializedEffects = new HashMap<>();
    private final int startedAt;

    CloneDoctorRoleSnapshot(ServerPlayer player) {
        captureAttributes(player, original);
        player.getActiveEffects().forEach(effect -> originalEffects.put(effect.getEffect(), new MobEffectInstance(effect)));
        startedAt = player.tickCount;
    }

    void captureInitialized(ServerPlayer player) {
        captureAttributes(player, initialized);
        player.getActiveEffects().forEach(effect -> {
            MobEffectInstance previous = originalEffects.get(effect.getEffect());
            if (previous == null || previous.getAmplifier() != effect.getAmplifier()
                    || previous.getDuration() != effect.getDuration()) {
                initializedEffects.put(effect.getEffect(), effect);
            }
        });
    }

    private static void captureAttributes(ServerPlayer player, Map<Holder<Attribute>, AttributeState> destination) {
        for (var attribute : player.getAttributes().getSyncableAttributes()) {
            Map<ResourceLocation, AttributeModifier> modifiers = new HashMap<>();
            attribute.getModifiers().forEach(modifier -> modifiers.put(modifier.id(), modifier));
            destination.put(attribute.getAttribute(), new AttributeState(attribute.getBaseValue(), modifiers));
        }
    }

    void restore(ServerPlayer player) {
        initialized.forEach((type, after) -> {
            var attribute = player.getAttribute(type);
            if (attribute == null) return;
            AttributeState before = original.get(type);
            if (before == null) return;
            if (before.base != after.base && attribute.getBaseValue() == after.base) attribute.setBaseValue(before.base);
            after.modifiers.forEach((id, applied) -> {
                AttributeModifier previous = before.modifiers.get(id);
                if (!applied.equals(previous) && applied.equals(attribute.getModifier(id))) {
                    attribute.removeModifier(id);
                    if (previous != null) attribute.addTransientModifier(previous);
                }
            });
        });
        int elapsed = Math.max(0, player.tickCount - startedAt);
        initializedEffects.forEach((type, applied) -> {
            // 外部效果已经替换该对象时不做回滚，避免误删后来获得的增益。
            if (player.getEffect(type) != applied) return;
            player.removeEffect(type);
            MobEffectInstance previous = originalEffects.get(type);
            if (previous == null) return;
            int duration = previous.isInfiniteDuration() ? -1 : previous.getDuration() - elapsed;
            if (duration == -1 || duration > 0) player.addEffect(new MobEffectInstance(type, duration,
                    previous.getAmplifier(), previous.isAmbient(), previous.isVisible(), previous.showIcon()));
        });
    }
}
