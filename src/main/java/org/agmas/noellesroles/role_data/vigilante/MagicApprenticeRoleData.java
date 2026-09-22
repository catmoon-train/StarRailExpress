package org.agmas.noellesroles.role_data.vigilante;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 魔法学徒独立的法术、魔力和领域状态。 */
public class MagicApprenticeRoleData extends SimpleRoleData {
    public enum Spell {
        FIREBALL(5, 20, true), FROSTBALL(5, 40, true), POISON(50, 20, true),
        KNOCKBACK(50, 15, false), FIRE_FIELD(75, 12, false), ICE_FIELD(65, 12, false);
        public final int cost;
        public final int range;
        public final boolean projectile;
        Spell(int cost, int range, boolean projectile) { this.cost = cost; this.range = range; this.projectile = projectile; }
    }

    public float mana = 100f;
    public Spell selectedSpell = Spell.FIREBALL;
    public int unlockedMask = 1;
    public int wandCooldownTicks;
    private int regenTicks;
    private final List<Field> fields = new ArrayList<>();

    public MagicApprenticeRoleData(RoleDataContext context) { super(context); }

    public float maxMana() { return 100f; }

    public boolean isUnlocked(Spell spell) { return (unlockedMask & (1 << spell.ordinal())) != 0; }

    public void unlock(Spell spell) { unlockedMask |= 1 << spell.ordinal(); sync(); }

    public void cycleSpell() {
        for (int i = 1; i <= Spell.values().length; i++) {
            Spell candidate = Spell.values()[(selectedSpell.ordinal() + i) % Spell.values().length];
            if (isUnlocked(candidate)) {
                selectedSpell = candidate;
                sync();
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable("message.noellesroles.magic_apprentice.switch_spell",
                            Component.translatable("hud.noellesroles.magic_apprentice.spell." + candidate.name().toLowerCase()))
                            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
                return;
            }
        }
    }

    public boolean castSelectedSpell(ServerPlayer caster) {
        if (!GameUtils.isPlayerAliveAndSurvival(caster) || !isCurrentRole(caster)
                || caster.getCooldowns().isOnCooldown(ModItems.APPRENTICE_WAND)) return false;
        Spell spell = selectedSpell;
        if (!isUnlocked(spell)) return false;
        if (mana < spell.cost) {
            caster.displayClientMessage(Component.translatable("message.noellesroles.magic_apprentice.no_mana", spell.cost)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        boolean success = switch (spell) {
            case FIREBALL -> castFireball(caster);
            case FROSTBALL -> castFrostball(caster);
            case POISON -> castPoison(caster);
            case KNOCKBACK -> castKnockback(caster);
            case FIRE_FIELD -> castField(caster, true);
            case ICE_FIELD -> castField(caster, false);
        };
        if (success) {
            mana = Mth.clamp(mana - spell.cost, 0f, maxMana());
            sync();
            caster.level().playSound(null, caster.blockPosition(), SoundEvents.BLAZE_SHOOT,
                    SoundSource.PLAYERS, .8f, spell == Spell.FROSTBALL ? 1.6f : 1.0f);
        }
        return success;
    }

    private boolean castFireball(ServerPlayer caster) {
        ServerPlayer target = rayTarget(caster, 20, true);
        drawRay(caster, ParticleTypes.FLAME, 20, 16);
        if (target != null) {
            GameUtils.killPlayer(target, true, caster, GameConstants.DeathReasons.FLAMETHROWER_BURNED);
        }
        return true;
    }

    private boolean castFrostball(ServerPlayer caster) {
        ServerPlayer target = rayTarget(caster, 40, true);
        drawRay(caster, ParticleTypes.SNOWFLAKE, 40, 24);
        if (target != null) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 4, false, true, true));
        }
        return true;
    }

    private boolean castPoison(ServerPlayer caster) {
        ServerPlayer target = rayTarget(caster, 20, false);
        drawRay(caster, new DustParticleOptions(new Vector3f(0.04f, 0.36f, 0.16f), 1.0f), 20, 16);
        if (target != null) SREPlayerPoisonComponent.KEY.get(target).setPoisonTicks(240, caster.getUUID());
        return true;
    }

    private boolean castKnockback(ServerPlayer caster) {
        ServerPlayer aimed = rayTarget(caster, 15, true);
        if (aimed == null) {
            caster.displayClientMessage(Component.translatable("message.noellesroles.magic_apprentice.need_target")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        Vec3 origin = caster.getEyePosition();
        Vec3 direction = caster.getViewVector(1).normalize();
        int affected = 0;
        for (Player target : caster.level().players()) {
            if (target == caster || !GameUtils.isPlayerAliveAndSurvival(target)) continue;
            Vec3 relative = target.position().add(0, target.getBbHeight() / 2, 0).subtract(origin);
            double distance = relative.length();
            if (distance > 15 || direction.dot(relative.normalize()) < .72) continue;
            if (target.hurt(caster.damageSources().playerAttack(caster), 1.0f)) {
                target.push(direction.x * 4.0, .15, direction.z * 4.0);
                target.hurtMarked = true;
                affected++;
            }
        }
        drawRay(caster, ParticleTypes.CLOUD, 15, 12);
        return affected > 0;
    }

    private boolean castField(ServerPlayer caster, boolean fire) {
        ServerPlayer target = rayTarget(caster, 12, true);
        if (target == null) {
            caster.displayClientMessage(Component.translatable("message.noellesroles.magic_apprentice.need_target")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        fields.add(new Field(target.position(), fire));
        return true;
    }

    private ServerPlayer rayTarget(ServerPlayer caster, double range, boolean respectWalls) {
        Vec3 start = caster.getEyePosition();
        Vec3 direction = caster.getViewVector(1).normalize();
        Vec3 end = start.add(direction.scale(range));
        // 以视线起点和终点构造搜索盒；原先从玩家脚下的碰撞盒扩展，抬头/低头时会漏掉视线中的玩家。
        ServerPlayer target = null;
        Vec3 targetHit = null;
        double closestDistance = Double.MAX_VALUE;
        for (Player candidate : caster.level().players()) {
            if (!(candidate instanceof ServerPlayer player) || player == caster
                    || !GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            var hit = player.getBoundingBox().inflate(0.35).clip(start, end);
            if (hit.isEmpty()) continue;
            double distance = start.distanceToSqr(hit.get());
            if (distance < closestDistance) {
                closestDistance = distance;
                target = player;
                targetHit = hit.get();
            }
        }
        if (target == null || targetHit == null) return null;
        if (respectWalls) {
            var block = caster.level().clip(new net.minecraft.world.level.ClipContext(start, targetHit,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
            if (block.getType() == HitResult.Type.BLOCK
                    && block.getLocation().distanceToSqr(start) + .04 < closestDistance) return null;
        }
        return target;
    }

    private void drawRay(ServerPlayer caster, net.minecraft.core.particles.ParticleOptions particle, double range, int count) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        Vec3 start = caster.getEyePosition();
        Vec3 direction = caster.getViewVector(1).normalize();
        for (int i = 1; i <= count; i++) {
            Vec3 pos = start.add(direction.scale(range * i / count));
            level.sendParticles(particle, pos.x, pos.y, pos.z, 2, .03, .03, .03, 0);
        }
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp) || !isCurrentRole(sp)) return;
        if (wandCooldownTicks > 0) wandCooldownTicks--;
        if (++regenTicks >= 600) {
            regenTicks = 0;
            mana = Mth.clamp(mana + 15f, 0f, maxMana());
            sync();
        }
        tickFields(sp);
        if (sp.level().getGameTime() % 20 == 0) sync();
    }

    private void tickFields(ServerPlayer caster) {
        Iterator<Field> iterator = fields.iterator();
        while (iterator.hasNext()) {
            Field field = iterator.next();
            field.remaining--;
            if (caster.level() instanceof ServerLevel level && caster.level().getGameTime() % 4 == 0) {
                level.sendParticles(field.fire ? ParticleTypes.FLAME : ParticleTypes.SNOWFLAKE,
                        field.center.x, field.center.y + 1, field.center.z, 10, 1.8, 1.5, 1.8, .01);
            }
            AABB box = new AABB(field.center.x - 2, field.center.y, field.center.z - 2,
                    field.center.x + 2, field.center.y + 3, field.center.z + 2);
            for (Player target : caster.level().getEntitiesOfClass(Player.class, box,
                    p -> GameUtils.isPlayerAliveAndSurvival(p))) {
                if (target.distanceToSqr(field.center.x, target.getY(), field.center.z) > 4.1) continue;
                if (field.fire) {
                    target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 40));
                    int burning = field.burning.merge(target.getUUID(), 1, Integer::sum);
                    if (burning >= 100) {
                        GameUtils.killPlayer(target, true, caster, GameConstants.DeathReasons.FLAMETHROWER_BURNED);
                        field.burning.remove(target.getUUID());
                    }
                } else {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 2, false, true, true));
                }
            }
            if (field.remaining <= 0) iterator.remove();
        }
    }

    private boolean isCurrentRole(ServerPlayer player) {
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.MAGIC_APPRENTICE);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putFloat("mana", mana);
        tag.putInt("selectedSpell", selectedSpell.ordinal());
        tag.putInt("unlockedMask", unlockedMask);
        tag.putInt("wandCooldownTicks", wandCooldownTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        mana = Mth.clamp(tag.getFloat("mana"), 0f, maxMana());
        selectedSpell = Spell.values()[Math.floorMod(tag.getInt("selectedSpell"), Spell.values().length)];
        unlockedMask = tag.getInt("unlockedMask") | 1;
        wandCooldownTicks = tag.getInt("wandCooldownTicks");
    }

    private static final class Field {
        final Vec3 center;
        final boolean fire;
        int remaining = 100;
        final Map<UUID, Integer> burning = new HashMap<>();
        Field(Vec3 center, boolean fire) { this.center = center; this.fire = fire; }
    }
}
