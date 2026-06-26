package org.agmas.noellesroles.game.roles.killer.wizard;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.TriggerScreenEdgeEffectPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WizardPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<WizardPlayerComponent> KEY = ModComponents.WIZARD;

    public enum Spell {
        ARMOR,
        FROST,
        SHADOW,
        EXPLOSION
    }

    private final Player player;

    public float mana = 0f;
    public Spell selectedSpell = Spell.ARMOR;
    public boolean explosionArmed = false;
    public int potionShieldTicks = 0;
    public boolean armorUsed = false;
    public int frostCooldownTicks = 0;
    public int shadowCooldownTicks = 0;
    public int explosionCooldownTicks = 0;

    private boolean gaveStartingItems = false;
    private final List<ShieldExpiry> shieldExpiries = new ArrayList<>();
    private final Map<UUID, FireArrowMark> fireArrowMarks = new HashMap<>();

    public WizardPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    @Override
    public void init() {
        this.mana = 0f;
        this.selectedSpell = Spell.ARMOR;
        this.explosionArmed = false;
        this.potionShieldTicks = 0;
        this.armorUsed = false;
        this.frostCooldownTicks = 0;
        this.shadowCooldownTicks = 0;
        this.explosionCooldownTicks = 0;
        this.gaveStartingItems = false;
        this.shieldExpiries.clear();
        this.fireArrowMarks.clear();
        sync();
    }

    @Override
    public void clear() {
        this.mana = 0f;
        this.explosionArmed = false;
        this.potionShieldTicks = 0;
        this.armorUsed = false;
        this.frostCooldownTicks = 0;
        this.shadowCooldownTicks = 0;
        this.explosionCooldownTicks = 0;
        this.gaveStartingItems = false;
        this.shieldExpiries.clear();
        this.fireArrowMarks.clear();
        sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    private NoellesRolesConfig config() {
        return NoellesRolesConfig.HANDLER.instance();
    }

    public float maxMana() {
        return 500f;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRole(sp, ModRoles.WIZARD)) {
            return;
        }

        if (!gaveStartingItems && GameUtils.isPlayerAliveAndSurvival(sp)) {
            grantStartingItems(sp);
            this.mana = Math.min(maxMana(), config().wizardStartingMana);
            clearCoins(sp);
            gaveStartingItems = true;
            sync();
        }

        if (potionShieldTicks > 0) {
            potionShieldTicks--;
            if (potionShieldTicks == 0) {
                expirePotionShield(sp);
            }
        }

        boolean anyCooldownActive = frostCooldownTicks > 0 || shadowCooldownTicks > 0 || explosionCooldownTicks > 0;
        if (frostCooldownTicks > 0) frostCooldownTicks--;
        if (shadowCooldownTicks > 0) shadowCooldownTicks--;
        if (explosionCooldownTicks > 0) explosionCooldownTicks--;
        // sync once per second while any cooldown is counting down
        if (anyCooldownActive && sp.level().getGameTime() % 20 == 0) {
            sync();
        }

        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance != 0) {
            if (shop.balance > 0) {
                addMana(shop.balance * config().wizardManaPerCoin);
            }
            clearCoins(sp);
        }

        tickShieldExpiries(sp);
        tickFireArrowMarks(sp);
    }

    private void tickShieldExpiries(ServerPlayer sp) {
        if (shieldExpiries.isEmpty()) {
            return;
        }
        Iterator<ShieldExpiry> it = shieldExpiries.iterator();
        while (it.hasNext()) {
            ShieldExpiry se = it.next();
            se.ticksLeft--;
            Player target = sp.level().getPlayerByUUID(se.targetUuid);
            if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
                it.remove();
                continue;
            }
            if (se.ticksLeft <= 0) {
                io.wifi.starrailexpress.cca.SREArmorPlayerComponent armorComp =
                        io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY.get(target);
                if (armorComp.getArmor() > 0) {
                    armorComp.removeArmor();
                    spawnAura(target, ParticleTypes.SMOKE);
                }
                it.remove();
            }
        }
    }

    public void addMana(float amount) {
        this.mana = Mth.clamp(this.mana + amount, 0f, maxMana());
        sync();
    }

    public boolean hasMana(float amount) {
        return this.mana >= amount;
    }

    public void spendMana(float amount) {
        this.mana = Math.max(0f, this.mana - amount);
        sync();
    }

    private void clearCoins(ServerPlayer sp) {
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance != 0) {
            shop.balance = 0;
            shop.sync();
        }
    }

    public void cycleSpell() {
        Spell[] all = Spell.values();
        selectedSpell = all[(selectedSpell.ordinal() + 1) % all.length];
        sync();
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.switch_spell",
                    Component.translatable("hud.noellesroles.wizard.spell." + selectedSpell.name().toLowerCase()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    public void castSelectedSpell() {
        if (!(player instanceof ServerPlayer sp) || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            return;
        }
        switch (selectedSpell) {
            case ARMOR -> castArmorPrompt(sp);
            case FROST -> castFrost(sp);
            case SHADOW -> castShadow(sp);
            case EXPLOSION -> castExplosion(sp);
        }
    }

    private void castArmorPrompt(ServerPlayer sp) {
        if (armorUsed) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.armor_used")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!hasMana(config().wizardArmorMinMana)) {
            notEnoughMana(sp);
            return;
        }
        sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.armor_prompt")
                .withStyle(ChatFormatting.AQUA), true);
    }

    public boolean grantShieldTo(ServerPlayer caster, ServerPlayer target) {
        if (armorUsed || !hasMana(config().wizardArmorMinMana) || target == null || target == caster
                || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        spendMana(mana);
        armorUsed = true;
        io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY.get(target).giveArmor();
        shieldExpiries.removeIf(se -> se.targetUuid.equals(target.getUUID()));
        shieldExpiries.add(new ShieldExpiry(target.getUUID(),
                GameConstants.getInTicks(0, config().wizardShieldDurationSeconds)));
        spawnAura(target, ParticleTypes.ENCHANT);
        target.level().playSound(null, target.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6f, 1.6f);
        caster.displayClientMessage(Component.translatable("message.noellesroles.wizard.armor_done",
                target.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
        caster.getCooldowns().addCooldown(ModItems.WIZARD_STAFF,
                io.wifi.starrailexpress.game.GameConstants.ITEM_COOLDOWNS.get(
                        io.wifi.starrailexpress.index.TMMItems.KNIFE));
        return true;
    }

    private void castFrost(ServerPlayer sp) {
        if (frostCooldownTicks > 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.spell_cooldown",
                    Math.max(1, (frostCooldownTicks + 19) / 20)).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!hasMana(config().wizardFrostMinMana)) {
            notEnoughMana(sp);
            return;
        }
        spendMana(mana / 2f);
        frostCooldownTicks = GameConstants.getInTicks(0, config().wizardFrostCooldownSeconds);
        sync();
        int duration = GameConstants.getInTicks(0, config().wizardFrostSeconds);
        double range = config().wizardFrostRange;
        int affected = 0;
        for (Player p : sp.level().players()) {
            if (p == sp || !GameUtils.isPlayerAliveAndSurvival(p)) {
                continue;
            }
            if (p.distanceTo(sp) > range) {
                continue;
            }
            p.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, duration, 0, false, false, true));
            p.addEffect(new MobEffectInstance(ModEffects.TURN_BANED, duration, 0, false, false, true));
            spawnAura(p, ParticleTypes.SNOWFLAKE);
            affected++;
        }
        castFx(sp, SoundEvents.GLASS_BREAK, ParticleTypes.SNOWFLAKE);
        sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.frost_cast", affected)
                .withStyle(ChatFormatting.AQUA), true);
    }

    private void castShadow(ServerPlayer sp) {
        SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(sp.level());
        if (!blackout.isBlackoutActive()) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.shadow_blackout_only")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (shadowCooldownTicks > 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.spell_cooldown",
                    Math.max(1, (shadowCooldownTicks + 19) / 20)).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!hasMana(config().wizardShadowCost)) {
            notEnoughMana(sp);
            return;
        }
        spendMana(config().wizardShadowCost);
        shadowCooldownTicks = GameConstants.getInTicks(0, config().wizardShadowCooldownSeconds);
        sync();
        extendShadowDarkness(sp, GameConstants.getInTicks(0, config().wizardShadowSeconds));
        castFx(sp, SoundEvents.WITHER_AMBIENT, ParticleTypes.SMOKE);
        sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.shadow_cast")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    private void extendShadowDarkness(ServerPlayer sp, int extension) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        for (Player p : sp.level().players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                continue;
            }
            SRERole role = gameWorld.getRole(p);
            if (role != null && role.canUseKiller()) {
                continue;
            }
            int blindness = p.hasEffect(MobEffects.BLINDNESS) ? p.getEffect(MobEffects.BLINDNESS).getDuration() : 0;
            int darkness = p.hasEffect(MobEffects.DARKNESS) ? p.getEffect(MobEffects.DARKNESS).getDuration() : 0;
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blindness + extension, 0, false, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, darkness + extension, 0, false, false, false));
        }
    }

    private void castExplosion(ServerPlayer sp) {
        if (explosionArmed) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.explosion_already")
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        if (explosionCooldownTicks > 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.spell_cooldown",
                    Math.max(1, (explosionCooldownTicks + 19) / 20)).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!hasMana(config().wizardExplosionMinMana)) {
            notEnoughMana(sp);
            return;
        }
        spendMana(mana * Math.max(0, Math.min(100, config().wizardExplosionManaPercentCost)) / 100f);
        explosionArmed = true;
        explosionCooldownTicks = GameConstants.getInTicks(0, config().wizardExplosionCooldownSeconds);
        sync();
        castFx(sp, SoundEvents.FIRECHARGE_USE, ParticleTypes.LAVA);
        sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.explosion_armed")
                .withStyle(ChatFormatting.RED), true);
    }

    public void castStaff(ServerPlayer sp) {
        if (explosionArmed) {
            explosionArmed = false;
            sync();
            WizardSpells.castNineRingFireball(this, sp);
            sp.getCooldowns().addCooldown(ModItems.WIZARD_STAFF, 60);
        } else {
            WizardSpells.castFireArrow(this, sp);
        }
    }

    public void onFireArrowHit(ServerPlayer caster, ServerPlayer target) {
        FireArrowMark mark = fireArrowMarks.computeIfAbsent(target.getUUID(), ignored -> new FireArrowMark());
        if (mark.deathTicks > 0) {
            return;
        }
        mark.hits++;
        int needed = config().wizardFireArrowHitsToKill;
        if (mark.hits >= needed) {
            mark.deathTicks = GameConstants.getInTicks(0, config().wizardFireArrowDeathDelaySeconds);
            target.displayClientMessage(Component.translatable("message.noellesroles.wizard.fire_arrow_hit")
                    .withStyle(ChatFormatting.RED), true);
            // 致命标记：受害者屏幕边缘泛起浓重暗红，强化“被暗杀”的临场感
            sendScreenEdge(target, 0.85f);
            // 施法者获得明确的击杀反馈（含目标名字）
            caster.displayClientMessage(Component.translatable("message.noellesroles.wizard.fire_arrow_kill_caster",
                    target.getDisplayName(),
                    Math.max(1, config().wizardFireArrowDeathDelaySeconds)).withStyle(ChatFormatting.DARK_RED), true);
        } else {
            target.displayClientMessage(Component.translatable("message.noellesroles.wizard.fire_arrow_stack",
                    mark.hits, needed).withStyle(ChatFormatting.GOLD), true);
            // 非致命命中：较淡的暗红警示
            sendScreenEdge(target, 0.45f);
            // 施法者获得命中反馈（含目标名字与当前层数）
            caster.displayClientMessage(Component.translatable("message.noellesroles.wizard.fire_arrow_hit_caster",
                    target.getDisplayName(), mark.hits, needed).withStyle(ChatFormatting.GOLD), true);
        }
        sync();
    }

    /** 向受害者发送屏幕边缘暗红效果，渲染“被暗杀”的临场压迫感。 */
    private void sendScreenEdge(ServerPlayer target, float intensity) {
        ServerPlayNetworking.send(target,
                new TriggerScreenEdgeEffectPayload(0x780000, 700L, intensity));
    }

    private void tickFireArrowMarks(ServerPlayer caster) {
        if (fireArrowMarks.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, FireArrowMark>> it = fireArrowMarks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, FireArrowMark> entry = it.next();
            Player target = caster.level().getPlayerByUUID(entry.getKey());
            if (!(target instanceof ServerPlayer serverTarget) || !GameUtils.isPlayerAliveAndSurvival(target)) {
                it.remove();
                continue;
            }
            FireArrowMark mark = entry.getValue();
            if (mark.deathTicks <= 0) {
                continue;
            }
            mark.deathTicks--;
            if (mark.deathTicks <= 0) {
                GameUtils.killPlayer(serverTarget, true, caster, Noellesroles.id("wizard_fire_arrow"));
                caster.getCooldowns().addCooldown(ModItems.WIZARD_STAFF,
                        io.wifi.starrailexpress.game.GameConstants.ITEM_COOLDOWNS.get(
                                io.wifi.starrailexpress.index.TMMItems.KNIFE));
                it.remove();
            }
        }
    }

    public boolean usePotion(ServerPlayer sp) {
        if (sp.getCooldowns().isOnCooldown(ModItems.WIZARD_POTION)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.potion_cd",
                    Math.max(1, Math.round(sp.getCooldowns().getCooldownPercent(ModItems.WIZARD_POTION, 0f)
                            * config().wizardPotionCooldown))).withStyle(ChatFormatting.RED), true);
            return false;
        }
        sp.getCooldowns().addCooldown(ModItems.WIZARD_POTION, GameConstants.getInTicks(0, config().wizardPotionCooldown));
        addMana(config().wizardPotionManaGain);
        io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY.get(sp).addArmor();
        potionShieldTicks = GameConstants.getInTicks(0, config().wizardPotionImmuneSeconds);
        sync();
        castFx(sp, SoundEvents.BREWING_STAND_BREW, ParticleTypes.WITCH);
        sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.potion_used")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    public void onPotionShieldBroken() {
        if (potionShieldTicks <= 0) {
            return;
        }
        potionShieldTicks = 0;
        spendMana(config().wizardPotionManaGain);
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.potion_shield_broken")
                    .withStyle(ChatFormatting.RED), true);
        }
        sync();
    }

    private void expirePotionShield(ServerPlayer sp) {
        io.wifi.starrailexpress.cca.SREArmorPlayerComponent armor =
                io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY.get(sp);
        if (armor.getArmor() > 0) {
            armor.removeArmor();
        }
        sync();
    }

    private void grantStartingItems(ServerPlayer sp) {
        sp.addItem(ModItems.WIZARD_STAFF.getDefaultInstance().copy());
        sp.addItem(ModItems.WIZARD_POTION.getDefaultInstance().copy());
    }

    private void notEnoughMana(ServerPlayer sp) {
        sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.no_mana")
                .withStyle(ChatFormatting.RED), true);
    }

    private void castFx(ServerPlayer sp, net.minecraft.sounds.SoundEvent sound,
            net.minecraft.core.particles.ParticleOptions particle) {
        if (sp.level() instanceof ServerLevel sl) {
            sl.playSound(null, sp.blockPosition(), sound, SoundSource.PLAYERS, 1.2f, 1.0f);
            Vec3 eye = sp.getEyePosition();
            sl.sendParticles(particle, eye.x, eye.y, eye.z, 40, 0.6, 0.6, 0.6, 0.1);
        }
    }

    private void spawnAura(Player target, net.minecraft.core.particles.ParticleOptions particle) {
        if (target.level() instanceof ServerLevel sl) {
            sl.sendParticles(particle, target.getX(), target.getY() + 1.0, target.getZ(),
                    20, 0.4, 0.8, 0.4, 0.05);
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putFloat("mana", this.mana);
        tag.putInt("selectedSpell", this.selectedSpell.ordinal());
        tag.putBoolean("explosionArmed", this.explosionArmed);
        tag.putInt("potionShieldTicks", this.potionShieldTicks);
        tag.putBoolean("armorUsed", this.armorUsed);
        tag.putInt("frostCooldownTicks", this.frostCooldownTicks);
        tag.putInt("shadowCooldownTicks", this.shadowCooldownTicks);
        tag.putInt("explosionCooldownTicks", this.explosionCooldownTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.mana = tag.getFloat("mana");
        this.selectedSpell = Spell.values()[Math.floorMod(tag.getInt("selectedSpell"), Spell.values().length)];
        this.explosionArmed = tag.getBoolean("explosionArmed");
        this.potionShieldTicks = tag.getInt("potionShieldTicks");
        this.armorUsed = tag.getBoolean("armorUsed");
        this.frostCooldownTicks = tag.getInt("frostCooldownTicks");
        this.shadowCooldownTicks = tag.getInt("shadowCooldownTicks");
        this.explosionCooldownTicks = tag.getInt("explosionCooldownTicks");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    private static final class ShieldExpiry {
        final UUID targetUuid;
        int ticksLeft;

        ShieldExpiry(UUID targetUuid, int ticksLeft) {
            this.targetUuid = targetUuid;
            this.ticksLeft = ticksLeft;
        }
    }

    private static final class FireArrowMark {
        int hits;
        int deathTicks;
    }
}
