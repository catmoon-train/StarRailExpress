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

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 残疾患者：每 70 秒随机获得一种永久负面药水，并获得 100 金币。
 */
public class DisabledPatientRoleData extends SimpleRoleData {

    public static final int INTERVAL_TICKS = 70 * 20;
    public static final int COIN_REWARD = 100;
    public static final int EFFECT_DURATION = -1;

    public long nextGrantTick;
    public final Set<String> grantedIds = new LinkedHashSet<>();

    public DisabledPatientRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return p == this.player;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putLong("NextGrantTick", this.nextGrantTick);
        ListTag granted = new ListTag();
        for (String id : this.grantedIds) {
            granted.add(StringTag.valueOf(id));
        }
        tag.put("Granted", granted);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.nextGrantTick = getLongTag(tag, "NextGrantTick", 0L);
        this.grantedIds.clear();
        ListTag granted = tag.getList("Granted", Tag.TAG_STRING);
        for (int i = 0; i < granted.size(); i++) {
            String id = granted.getString(i);
            if (!id.isEmpty()) {
                this.grantedIds.add(id);
            }
        }
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer sp) {
            removeGrantedEffects(sp);
        }
        this.nextGrantTick = 0L;
        this.grantedIds.clear();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (gameWorld == null || !gameWorld.isRunning() || !gameWorld.isRole(sp, ModRoles.DISABLED_PATIENT)) {
            return;
        }

        long now = GameUtils.getTicksFromGameStart(sp.level());
        if (this.nextGrantTick <= 0L) {
            this.nextGrantTick = now + INTERVAL_TICKS;
            sync();
        }

        if (GameUtils.isPlayerAliveAndSurvival(sp)) {
            if (now >= this.nextGrantTick) {
                grantOnce(sp);
                this.nextGrantTick = now + INTERVAL_TICKS;
                sync();
            }
            if (sp.level().getGameTime() % 20 == 10) {
                reapplyGranted(sp);
            }
        }
    }

    private void grantOnce(ServerPlayer sp) {
        Disability disability = pickUnused(sp);
        if (disability != null) {
            this.grantedIds.add(disability.id);
            disability.apply(sp);
            sp.displayClientMessage(Component.translatable(
                    "message.noellesroles.disabled_patient.gained",
                    disability.displayName(),
                    COIN_REWARD).withStyle(ChatFormatting.GOLD), true);
        } else {
            sp.displayClientMessage(Component.translatable(
                    "message.noellesroles.disabled_patient.gained_gold_only",
                    COIN_REWARD).withStyle(ChatFormatting.GOLD), true);
        }
        MoneyUtils.addToBalance(sp, COIN_REWARD);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8f, 0.7f);
    }

    private Disability pickUnused(ServerPlayer sp) {
        List<Disability> candidates = new ArrayList<>();
        int totalWeight = 0;
        for (Disability disability : Disability.values()) {
            if (this.grantedIds.contains(disability.id) || disability.alreadyPresent(sp)) {
                continue;
            }
            candidates.add(disability);
            totalWeight += disability.weight;
        }
        if (candidates.isEmpty() || totalWeight <= 0) {
            return null;
        }
        int roll = sp.getRandom().nextInt(totalWeight);
        for (Disability disability : candidates) {
            roll -= disability.weight;
            if (roll < 0) {
                return disability;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private void reapplyGranted(ServerPlayer sp) {
        for (String id : this.grantedIds) {
            Disability disability = Disability.byId(id);
            if (disability != null) {
                disability.apply(sp);
            }
        }
    }

    private void removeGrantedEffects(ServerPlayer sp) {
        for (String id : this.grantedIds) {
            Disability disability = Disability.byId(id);
            if (disability != null) {
                disability.remove(sp);
            }
        }
    }

    public enum Disability {
        BLINDNESS("blindness", 10, spec(MobEffects.BLINDNESS, 0)),
        DARKNESS("darkness", 10, spec(MobEffects.DARKNESS, 0)),
        SLOWNESS("slowness", 10, spec(MobEffects.MOVEMENT_SLOWDOWN, 1)),
        MYOPIA("myopia", 10, spec(ModEffects.MYOPIA, 3)),
        LIMP("limp", 10, spec(ModEffects.LIMP, 0)),
        BLIND_VISION("blind_vision", 1, spec(ModEffects.BLIND_VISION, 0)),
        HAND_TREMOR("hand_tremor", 10, spec(ModEffects.HAND_TREMOR, 4)),
        MOTOR_DYSFUNCTION("motor_dysfunction", 10, spec(ModEffects.MOTOR_DYSFUNCTION, 0)),
        LOSS_OF_APPETITE("loss_of_appetite", 10, spec(ModEffects.LOSS_OF_APPETITE, 0)),
        COGNITIVE_BIAS("cognitive_bias", 10, spec(ModEffects.COGNITIVE_BIAS, 0)),
        MENTAL_DEAFNESS("mental_deafness", 10, spec(ModEffects.MENTAL_DEAFNESS, 0)),
        INTELLECT_DROP("intellect_drop", 10, spec(ModEffects.INTELLECT_DROP, 0)),
        APHRENIA("aphrenia", 10, spec(ModEffects.APHRENIA, 0)),
        ILLITERATE("illiterate", 10, spec(ModEffects.ILLITERATE, 0)),
        KEEN_HEARING("keen_hearing", 10, spec(ModEffects.KEEN_HEARING, 0)),
        HOARSE("hoarse", 10, spec(ModEffects.HEAVY_METAL_VOICE, 0)),
        MUTE("mute", 10, spec(ModEffects.VOICE_SILENCE, 0), spec(ModEffects.CHAT_BAN, 0)),
        COWARD("coward", 10, spec(ModEffects.COWARD, 0)),
        RAGE("rage", 10, spec(ModEffects.RAGE, 0)),
        COWARDICE("cowardice", 10, spec(ModEffects.COWARDICE, 0));

        public final String id;
        public final int weight;
        private final EffectSpec[] effects;

        Disability(String id, int weight, EffectSpec... effects) {
            this.id = id;
            this.weight = weight;
            this.effects = effects;
        }

        public static Disability byId(String id) {
            for (Disability disability : values()) {
                if (disability.id.equals(id)) {
                    return disability;
                }
            }
            return null;
        }

        public Component displayName() {
            return switch (this) {
                case MUTE -> Component.translatable("message.noellesroles.disabled_patient.effect.mute");
                case HOARSE -> Component.translatable("message.noellesroles.disabled_patient.effect.hoarse");
                default -> Component.translatable(this.effects[0].effect().value().getDescriptionId());
            };
        }

        public boolean alreadyPresent(ServerPlayer player) {
            for (EffectSpec spec : this.effects) {
                if (!player.hasEffect(spec.effect())) {
                    return false;
                }
            }
            return this.effects.length > 0;
        }

        public void apply(ServerPlayer player) {
            for (EffectSpec spec : this.effects) {
                var current = player.getEffect(spec.effect());
                if (current != null
                        && current.getAmplifier() >= spec.amplifier()
                        && (current.isInfiniteDuration() || current.getDuration() > 21)) {
                    continue;
                }
                player.addEffect(ModEffects.of(
                        spec.effect(),
                        EFFECT_DURATION,
                        spec.amplifier(),
                        true,
                        false,
                        true));
            }
        }

        public void remove(ServerPlayer player) {
            for (EffectSpec spec : this.effects) {
                player.removeEffect(spec.effect());
            }
        }

        private static EffectSpec spec(Holder<MobEffect> effect, int amplifier) {
            return new EffectSpec(effect, amplifier);
        }
    }

    private record EffectSpec(Holder<MobEffect> effect, int amplifier) {
    }
}
