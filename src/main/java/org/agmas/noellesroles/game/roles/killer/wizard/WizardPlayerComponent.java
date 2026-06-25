package org.agmas.noellesroles.game.roles.killer.wizard;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
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
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * 巫师组件（杀手阵营）。
 *
 * <p>核心机制：
 * <ul>
 *   <li><b>魔素</b>：以 bossbar 显示的特殊充能。巫师所有金币收入（击杀 + 自然恢复）都转化为魔素，巫师本身不使用金币。</li>
 *   <li><b>法杖</b>（{@link WizardStaffItem}）：右键蓄力释放魔法火焰箭（穿透、命中后 3 秒死亡），左键击退。</li>
 *   <li><b>魔药</b>（{@link WizardPotionItem}）：使用后进入冷却，获得大量魔素，并在 60 秒内免疫一次致命攻击。</li>
 *   <li><b>魔法池</b>：潜行 + 技能键在四个法术间切换；技能键释放当前法术。</li>
 *   <li>法术：盔甲护身 / 冰霜震慑 / 笼罩暗影 / Explosion! —— 详见各 cast 方法。</li>
 * </ul>
 */
public class WizardPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<WizardPlayerComponent> KEY = ModComponents.WIZARD;

    public enum Spell {
        ARMOR,      // 盔甲护身
        FROST,      // 冰霜震慑
        SHADOW,     // 笼罩暗影
        EXPLOSION   // Explosion!
    }

    private final Player player;

    /** 魔素（特殊充能）。 */
    public float mana = 0f;
    /** 当前选中的法术。 */
    public Spell selectedSpell = Spell.ARMOR;
    /** Explosion! 是否已就绪（下一次法杖施放释放九环火球术）。 */
    public boolean explosionArmed = false;

    /** 魔药冷却（tick）。 */
    public int potionCooldown = 0;
    /** 魔药提供的“免疫一次攻击”剩余 tick；> 0 表示可免疫一次致命攻击。 */
    public int attackImmuneTicks = 0;

    /** 笼罩暗影：是否在关灯期间持续施放（持续到关灯结束）。 */
    private boolean shadowSustaining = false;

    /** 是否已发放开局道具。 */
    private boolean gaveStartingItems = false;

    /** 火焰箭命中后延迟死亡队列。 */
    private final List<PendingDeath> pendingDeaths = new ArrayList<>();

    /** 魔素 bossbar（非序列化）。 */
    private transient ServerBossEvent manaBar = null;

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
        this.potionCooldown = 0;
        this.attackImmuneTicks = 0;
        this.shadowSustaining = false;
        this.gaveStartingItems = false;
        this.pendingDeaths.clear();
        sync();
    }

    @Override
    public void clear() {
        removeManaBar();
        this.mana = 0f;
        this.explosionArmed = false;
        this.attackImmuneTicks = 0;
        this.shadowSustaining = false;
        this.pendingDeaths.clear();
        sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    private NoellesRolesConfig config() {
        return NoellesRolesConfig.HANDLER.instance();
    }

    public float maxMana() {
        return config().wizardMaxMana;
    }

    // ==================== 每 tick ====================

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        boolean isWizard = gameWorld.isRole(sp, ModRoles.WIZARD);
        if (!isWizard) {
            removeManaBar();
            return;
        }

        // 开局发放法杖与魔药
        if (!gaveStartingItems && GameUtils.isPlayerAliveAndSurvival(sp)) {
            grantStartingItems(sp);
            gaveStartingItems = true;
        }

        if (potionCooldown > 0) {
            potionCooldown--;
        }
        if (attackImmuneTicks > 0) {
            attackImmuneTicks--;
        }

        // 金币 -> 魔素：清空商店余额并转化
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance > 0) {
            addMana(shop.balance * config().wizardManaPerCoin);
            shop.balance = 0;
            shop.sync();
        }
        // 自然恢复（被动魔素）
        if (sp.level().getGameTime() % 20 == 0) {
            addMana(config().wizardPassiveManaPerSecond);
        }

        // 笼罩暗影：关灯期间持续施放
        tickShadowSustain(sp);

        // 火焰箭延迟死亡
        tickPendingDeaths(sp);

        // 魔素 bossbar
        updateManaBar(sp);
    }

    private void tickShadowSustain(ServerPlayer sp) {
        if (!shadowSustaining) {
            return;
        }
        SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(sp.level());
        if (!blackout.isBlackoutActive()) {
            shadowSustaining = false;
            return;
        }
        // 每秒额外消耗魔素维持失明
        if (sp.level().getGameTime() % 20 == 0) {
            if (mana < config().wizardShadowBlackoutDrainPerSecond) {
                shadowSustaining = false;
                return;
            }
            spendMana(config().wizardShadowBlackoutDrainPerSecond);
            applyShadowBlindness(sp, GameConstants.getInTicks(0, 2));
        }
    }

    private void tickPendingDeaths(ServerPlayer sp) {
        if (pendingDeaths.isEmpty()) {
            return;
        }
        Iterator<PendingDeath> it = pendingDeaths.iterator();
        while (it.hasNext()) {
            PendingDeath pd = it.next();
            pd.ticksLeft--;
            Player target = sp.level().getPlayerByUUID(pd.targetUuid);
            if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
                it.remove();
                continue;
            }
            if (target.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 1.0, target.getZ(),
                        6, 0.3, 0.5, 0.3, 0.02);
            }
            if (pd.ticksLeft <= 0) {
                GameUtils.killPlayer(target, true, sp, Noellesroles.id("wizard_fire_arrow"));
                it.remove();
            }
        }
    }

    // ==================== 魔素 ====================

    public void addMana(float amount) {
        this.mana = Math.min(maxMana(), this.mana + amount);
        sync();
    }

    public boolean hasMana(float amount) {
        return this.mana >= amount;
    }

    public void spendMana(float amount) {
        this.mana = Math.max(0f, this.mana - amount);
        sync();
    }

    private void updateManaBar(ServerPlayer sp) {
        if (manaBar == null) {
            manaBar = new ServerBossEvent(manaBarTitle(), BossEvent.BossBarColor.PURPLE,
                    BossEvent.BossBarOverlay.PROGRESS);
        }
        // 确保（重生后 ServerPlayer 实例会变）当前实例可见；addPlayer 对已存在者是幂等的
        manaBar.addPlayer(sp);
        manaBar.setName(manaBarTitle());
        manaBar.setProgress(Math.max(0f, Math.min(1f, mana / maxMana())));
    }

    private Component manaBarTitle() {
        return Component.translatable("hud.noellesroles.wizard.mana",
                (int) mana, (int) maxMana(),
                Component.translatable("hud.noellesroles.wizard.spell." + selectedSpell.name().toLowerCase()));
    }

    private void removeManaBar() {
        if (manaBar != null) {
            manaBar.removeAllPlayers();
            manaBar = null;
        }
    }

    // ==================== 法术切换/释放 ====================

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

    /** 盔甲护身：提示玩家在背包中选择目标，实际护盾发放由 {@link WizardShieldC2SPacket} 处理。 */
    private void castArmorPrompt(ServerPlayer sp) {
        if (!hasMana(config().wizardArmorCost)) {
            notEnoughMana(sp);
            return;
        }
        sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.armor_prompt")
                .withStyle(ChatFormatting.AQUA), true);
    }

    /** 由网络包调用：给目标一次护盾，消耗魔素。 */
    public boolean grantShieldTo(ServerPlayer caster, ServerPlayer target) {
        if (!hasMana(config().wizardArmorCost) || target == null
                || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        spendMana(config().wizardArmorCost);
        io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY.get(target).giveArmor();
        spawnAura(target, ParticleTypes.ENCHANT);
        target.level().playSound(null, target.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6f, 1.6f);
        caster.displayClientMessage(Component.translatable("message.noellesroles.wizard.armor_done",
                target.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    /** 冰霜震慑：消耗魔素，使附近除巫师外的所有玩家无法移动/转视角。 */
    private void castFrost(ServerPlayer sp) {
        if (!hasMana(config().wizardFrostCost)) {
            notEnoughMana(sp);
            return;
        }
        spendMana(config().wizardFrostCost);
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

    /** 笼罩暗影：使除杀手外的玩家失明 6 秒；关灯状态下额外消耗魔素持续至关灯结束。 */
    private void castShadow(ServerPlayer sp) {
        if (!hasMana(config().wizardShadowCost)) {
            notEnoughMana(sp);
            return;
        }
        spendMana(config().wizardShadowCost);
        applyShadowBlindness(sp, GameConstants.getInTicks(0, config().wizardShadowSeconds));
        SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(sp.level());
        if (blackout.isBlackoutActive()) {
            shadowSustaining = true;
        }
        castFx(sp, SoundEvents.WITHER_AMBIENT, ParticleTypes.SMOKE);
        sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.shadow_cast")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    private void applyShadowBlindness(ServerPlayer sp, int duration) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        for (Player p : sp.level().players()) {
            if (p == sp || !GameUtils.isPlayerAliveAndSurvival(p)) {
                continue;
            }
            SRERole role = gameWorld.getRole(p);
            if (role != null && role.canUseKiller()) {
                continue; // 除杀手外
            }
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0, false, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, duration, 0, false, false, false));
        }
    }

    /** Explosion!：消耗魔素就绪，下一次法杖施放释放九环火球术。 */
    private void castExplosion(ServerPlayer sp) {
        if (explosionArmed) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.explosion_already")
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        if (!hasMana(config().wizardExplosionCost)) {
            notEnoughMana(sp);
            return;
        }
        spendMana(config().wizardExplosionCost);
        explosionArmed = true;
        sync();
        castFx(sp, SoundEvents.FIRECHARGE_USE, ParticleTypes.LAVA);
        sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.explosion_armed")
                .withStyle(ChatFormatting.RED), true);
    }

    // ==================== 法杖施放（由物品调用） ====================

    /** 法杖蓄力释放：被 {@link WizardStaffItem#releaseUsing} 调用。 */
    public void castStaff(ServerPlayer sp) {
        if (explosionArmed) {
            explosionArmed = false;
            sync();
            WizardSpells.castNineRingFireball(this, sp);
        } else {
            WizardSpells.castFireArrow(this, sp);
        }
    }

    /** 注册一个火焰箭命中：3 秒后死亡。 */
    public void markFireArrowHit(ServerPlayer target) {
        for (PendingDeath pd : pendingDeaths) {
            if (pd.targetUuid.equals(target.getUUID())) {
                return; // 已标记
            }
        }
        pendingDeaths.add(new PendingDeath(target.getUUID(),
                GameConstants.getInTicks(0, config().wizardFireArrowDeathSeconds)));
        target.displayClientMessage(Component.translatable("message.noellesroles.wizard.fire_arrow_hit")
                .withStyle(ChatFormatting.RED), true);
    }

    // ==================== 魔药 ====================

    /** 由魔药物品调用：进入冷却、获得大量魔素、60s 内免疫一次攻击。 */
    public boolean usePotion(ServerPlayer sp) {
        if (potionCooldown > 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.potion_cd",
                    potionCooldown / 20).withStyle(ChatFormatting.RED), true);
            return false;
        }
        potionCooldown = GameConstants.getInTicks(0, config().wizardPotionCooldown);
        addMana(config().wizardPotionManaGain);
        attackImmuneTicks = GameConstants.getInTicks(0, config().wizardPotionImmuneSeconds);
        sync();
        castFx(sp, SoundEvents.BREWING_STAND_BREW, ParticleTypes.WITCH);
        sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.potion_used")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    /** 消耗一次攻击免疫（由死亡拦截事件调用）。 */
    public boolean consumeAttackImmunity() {
        if (attackImmuneTicks > 0) {
            attackImmuneTicks = 0;
            sync();
            if (player instanceof ServerPlayer sp) {
                castFx(sp, SoundEvents.TOTEM_USE, ParticleTypes.TOTEM_OF_UNDYING);
                sp.displayClientMessage(Component.translatable("message.noellesroles.wizard.immune_triggered")
                        .withStyle(ChatFormatting.GOLD), true);
            }
            return true;
        }
        return false;
    }

    // ==================== 工具 ====================

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

    // ==================== NBT ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putFloat("mana", this.mana);
        tag.putInt("selectedSpell", this.selectedSpell.ordinal());
        tag.putBoolean("explosionArmed", this.explosionArmed);
        tag.putInt("potionCooldown", this.potionCooldown);
        tag.putInt("attackImmuneTicks", this.attackImmuneTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.mana = tag.getFloat("mana");
        this.selectedSpell = Spell.values()[Math.floorMod(tag.getInt("selectedSpell"), Spell.values().length)];
        this.explosionArmed = tag.getBoolean("explosionArmed");
        this.potionCooldown = tag.getInt("potionCooldown");
        this.attackImmuneTicks = tag.getInt("attackImmuneTicks");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    private static final class PendingDeath {
        final UUID targetUuid;
        int ticksLeft;

        PendingDeath(UUID targetUuid, int ticksLeft) {
            this.targetUuid = targetUuid;
            this.ticksLeft = ticksLeft;
        }
    }
}
