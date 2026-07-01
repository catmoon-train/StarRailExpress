package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class TMMRoles {
    public static final Map<ResourceLocation, SRERole> ROLES = new HashMap<>();
    public static final List<ComponentKey<? extends RoleComponent>> COMPONENT_KEYS = new ArrayList<>();
    public static final SRERole DISCOVERY_CIVILIAN = registerRole(
            new NormalRole(SRE.id("discovery_civilian"), 0x5CFF4A, false, false, SRERole.MoodType.NONE, -1, true))
            .setCanPickUpRevolver(false).setNeutrals(true).setCanBeRandomedByOtherRoles(false).setOtherModeRole(true);
    public static final SRERole CIVILIAN = registerRole(new NormalRole(SRE.id("civilian"), 0x36E51B, true, false,
            SRERole.MoodType.REAL, GameConstants.getInTicks(0, 10), false));
    public static final SRERole VIGILANTE = registerRole(new NormalRole(SRE.id("vigilante"), 0x1B8AE5, true, false,
            SRERole.MoodType.REAL, GameConstants.getInTicks(0, 10), false) {
        @Override
        public List<ItemStack> getDefaultItems() {
            return List.of(new ItemStack(TMMItems.REVOLVER).copy());
        }
    }.setVigilanteTeam(true).setDefaultMax(0).setCanSetSpawnInfoInConfig(false));
    public static final SRERole KILLER = registerRole(
            new NormalRole(SRE.id("killer"), 0xC13838, false, true, SRERole.MoodType.FAKE, -1, true));
    public static final SRERole LOOSE_END = registerRole(
            new ExtraEffectRole(SRE.id("loose_end"), 0x9F0000, false, false, SRERole.MoodType.NONE, -1, false,
                    new MobEffectInstance(
                            MobEffects.MOVEMENT_SPEED,
                            30 * 20, // 持续时间 60s（tick）
                            2, // 等级（0 = 速度 I）
                            true, // ambient（环境效果，如信标）
                            false, // showParticles（显示粒子）
                            true // showIcon（显示图标）
                    )) {
                @Override
                public void onKill(Player victim, boolean spawnBody, @Nullable Player killer,
                        ResourceLocation deathReason) {
                    super.onKill(victim, spawnBody, killer, deathReason);
                    if (killer == null)
                        return;
                    if (!killer.hasEffect(MobEffects.WEAVING))
                        return;
                    CrosshairaddonsCompat.onAttack(victim);
                    if (killer != null && killer.level() instanceof ServerLevel serverLevel) {
                        specialEffect(victim, killer, serverLevel);
                    }
                }

                private static void specialEffect(Player victim, @NonNull Player killer, ServerLevel serverLevel) {
                    Vec3 victimPos = victim.position();
                    Vec3 killerPos = killer.position();

                    // 1. 自身小特效 - 在击杀者周围生成红色粒子环绕
                    for (int i = 0; i < 20; i++) {
                        double angle = (Math.PI * 2 * i) / 20;
                        double offsetX = Math.cos(angle) * 1.5;
                        double offsetZ = Math.sin(angle) * 1.5;
                        serverLevel.sendParticles(
                                ParticleTypes.CRIMSON_SPORE,
                                killerPos.x + offsetX, killerPos.y + 1.5, killerPos.z + offsetZ,
                                1, 0.1, 0.1, 0.1, 0.0);
                    }

                    // 2. 击中人的夸张特效 - 在受害者位置生成爆炸和粒子爆发
                    // CRIT粒子（暴击效果）
                    serverLevel.sendParticles(
                            ParticleTypes.CRIT,
                            victimPos.x, victimPos.y + 1, victimPos.z,
                            15, 0.5, 0.5, 0.5, 0.3);

                    // SOUL_FIRE_FLAME（灵魂火焰）- 增加恐怖感
                    serverLevel.sendParticles(
                            ParticleTypes.SOUL_FIRE_FLAME,
                            victimPos.x, victimPos.y + 0.5, victimPos.z,
                            15, 0.4, 0.6, 0.4, 0.05);

                    // LARGE_SMOKE（大烟雾）
                    serverLevel.sendParticles(
                            ParticleTypes.LARGE_SMOKE,
                            victimPos.x, victimPos.y + 0.8, victimPos.z,
                            10, 0.3, 0.4, 0.3, 0.02);

                    // 3. 播放音效 - 多层音效叠加
                    // 主音效：刀刺声
                    serverLevel.playSound(null, victimPos.x, victimPos.y, victimPos.z,
                            TMMSounds.ITEM_KNIFE_STAB, SoundSource.PLAYERS, 1.5f, 0.8f);

                    // 回声效果：锁链击中声
                    serverLevel.playSound(null, victimPos.x, victimPos.y, victimPos.z,
                            SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.0f, 1.2f);

                    // 环境层：恶魂尖叫（增加恐怖氛围）
                    serverLevel.playSound(null, victimPos.x, victimPos.y, victimPos.z,
                            SoundEvents.GHAST_SCREAM, SoundSource.PLAYERS, 0.6f, 0.7f);
                }
            })
            .setCanSeeTime(true).setCanUseInstinct(true).setCanBeRandomedByOtherRoles(false);

    public static SRERole registerRole(SRERole role, String... flags) {
        return registerRole(role.addFlag(flags));
    }

    public static SRERole registerRole(SRERole role) {
        ROLES.put(role.identifier(), role);
        if (role.getComponentKey() != null) {
            COMPONENT_KEYS.add(role.getComponentKey());
        }
        return role;
    }

    public static void addRoleComponents(ComponentKey<? extends RoleComponent> componentKeyToAdd) {
        COMPONENT_KEYS.add(componentKeyToAdd);
    }

    public static HashSet<String> getAllFlags() {
        HashSet<String> filters = new HashSet<>();
        for (var it : ROLES.values()) {
            filters.addAll(it.getFlags());
        }
        return filters;
    }

    public static SRERole getRole(ResourceLocation id) {
        return ROLES.getOrDefault(id, null);
    }
}
