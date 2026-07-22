package org.agmas.noellesroles.game.roles.innocence.salted_fish;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import org.agmas.noellesroles.content.entity.SaltedFishBodyEntity;
import org.agmas.noellesroles.init.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

public class SaltedFishPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SaltedFishPlayerComponent> KEY = ModComponents.SALTED_FISH;
    public static final ResourceLocation SKILL_ID = Noellesroles.id("salted_fish_sunbathe");

    public static final int ACTIVE_TICKS = 80 * 20;
    public static final int COOLDOWN_TICKS = 40 * 20;
    public static final int SIDE_INTERVAL_TICKS = 20 * 20;
    public static final int FLIP_TICKS = 20;

    private final Player player;
    public int activeTicks;
    public int cooldownTicks;
    public int flipTicks;
    public int side;
    public int previousSide;
    public float sunYaw;
    private UUID fakeBodyUuid;

    public SaltedFishPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    /**
     * 同步给同世界的所有玩家（默认只同步给本人）。
     * 翻身动画由旁观者客户端读取本组件的 {@link #flipTicks}/{@link #side} 等状态渲染假尸体，
     * 因此必须让别人也收到，否则「别人看不见咸鱼翻身动画」。
     */
    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return target.level() == player.level();
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        discardFakeBody();
        activeTicks = 0;
        cooldownTicks = 0;
        flipTicks = 0;
        side = 0;
        previousSide = 0;
        sunYaw = 0.0f;
        fakeBodyUuid = null;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public boolean isActive() {
        return activeTicks > 0;
    }

    public boolean useSkill(ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRole(sp, ModRoles.SALTED_FISH)) {
            return false;
        }
        if (activeTicks > 0) {
            // 躺下状态：按技能键主动站起
            finishActive(sp);
            return true;
        }
        if (cooldownTicks > 0) {
            sp.displayClientMessage(Component.translatable("message.sre.skill.cooldown",
                    String.format("%.1f", cooldownTicks / 20.0F)).withStyle(ChatFormatting.RED), true);
            return false;
        }

        activeTicks = ACTIVE_TICKS;
        flipTicks = 0;
        side = 0;
        previousSide = 0;
        updateSunYaw(sp.serverLevel());
        spawnFakeBody(sp);
        applyRestraints();
        sync();
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0f, 0.75f);
        sp.displayClientMessage(Component.translatable("message.noellesroles.salted_fish.start")
                .withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRunning() || !gameWorld.isRole(sp, ModRoles.SALTED_FISH)
                || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            if (activeTicks > 0) {
                discardFakeBody();
                activeTicks = 0;
                flipTicks = 0;
                sync();
            }
            tickCooldown();
            return;
        }

        if (activeTicks > 0) {
            tickActive(sp);
        } else {
            tickCooldown();
        }
    }

    private void tickActive(ServerPlayer sp) {
        int elapsed = ACTIVE_TICKS - activeTicks;
        if (elapsed > 0 && elapsed % SIDE_INTERVAL_TICKS == 0) {
            startFlip(sp);
        }

        updateSunYaw(sp.serverLevel());
        updateFakeBody(sp.serverLevel());
        applyRestraints();
        stopHorizontalMotion(sp);

        activeTicks--;
        if (flipTicks > 0) {
            flipTicks--;
        }

        if (activeTicks <= 0) {
            finishActive(sp);
            return;
        }

        // 每秒做一次纠偏同步即可；翻身的离散状态改变已在 startFlip/finishActive 里各同步一次，
        // 客户端 clientTick 会自行递减 flipTicks 播放动画。切勿每 tick 同步（会造成广播风暴）。
        if (elapsed % 20 == 0) {
            sync();
        }
    }

    private void tickCooldown() {
        if (cooldownTicks <= 0) {
            return;
        }
        cooldownTicks--;
        if (cooldownTicks == 0 || cooldownTicks % 200 == 0) {
            sync();
        }
    }

    private void startFlip(ServerPlayer sp) {
        previousSide = side;
        side = 1 - side;
        flipTicks = FLIP_TICKS;
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.SLIME_BLOCK_STEP, SoundSource.PLAYERS, 1.0f,
                1.35f);
        // 立即同步翻身的离散状态改变，确保所有旁观者客户端从同一起点播放翻身动画
        sync();
    }

    private void finishActive(ServerPlayer sp) {
        activeTicks = 0;
        flipTicks = 0;
        cooldownTicks = COOLDOWN_TICKS;
        discardFakeBody();
        stopHorizontalMotion(sp);
        SREAbilityPlayerComponent.KEY.get(sp).setSkillCooldown(SKILL_ID, COOLDOWN_TICKS);
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8f, 0.9f);
        sp.displayClientMessage(Component.translatable("message.noellesroles.salted_fish.end")
                .withStyle(ChatFormatting.AQUA), true);
        sync();
    }

    private void applyRestraints() {
        player.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 10, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 10, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, 10, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 10, 0, false, false, false));
    }

    private void stopHorizontalMotion(ServerPlayer sp) {
        Vec3 current = sp.getDeltaMovement();
        Vec3 stopped = new Vec3(0.0D, current.y, 0.0D);
        sp.setDeltaMovement(stopped);
        sp.hurtMarked = true;
    }

    private void updateSunYaw(ServerLevel level) {
        long time = Math.floorMod(level.getDayTime(), 24000L);
        sunYaw = Mth.wrapDegrees((time / 24000.0f) * 360.0f - 90.0f);
    }

    private void spawnFakeBody(ServerPlayer sp) {
        discardFakeBody();
        SaltedFishBodyEntity body = ModEntities.SALTED_FISH_BODY.create(sp.serverLevel());
        if (body == null) {
            return;
        }
        body.setPlayerUuid(sp.getUUID());
        // 关闭重力：位置每 tick 由 updateFakeBody 同步到本体，避免假尸体因自身重力下落而与本体分离。
        body.setNoGravity(true);
        body.moveTo(sp.getX(), sp.getY(), sp.getZ(), sunYaw, 0.0f);
        body.setYRot(sunYaw);
        body.setYHeadRot(sunYaw);
        body.setYBodyRot(sunYaw);
        body.yBodyRotO = sunYaw;
        body.setXRot(0.0f);
        sp.serverLevel().addFreshEntity(body);
        fakeBodyUuid = body.getUUID();
    }

    private PlayerBodyEntity getFakeBody(ServerLevel level) {
        if (fakeBodyUuid == null) {
            return null;
        }
        Entity entity = level.getEntity(fakeBodyUuid);
        if (entity instanceof SaltedFishBodyEntity body) {
            return body;
        }
        fakeBodyUuid = null;
        return null;
    }

    private void updateFakeBody(ServerLevel level) {
        PlayerBodyEntity body = getFakeBody(level);
        if (body == null) {
            return;
        }
        // 位置：每 tick 跟随本体，避免本体被推动/下落/传送后假尸体留在原地而分离。
        // 用 setPos 而非 moveTo，保留客户端插值，跟随更平滑。
        body.setPos(player.getX(), player.getY(), player.getZ());
        body.setDeltaMovement(0.0, 0.0, 0.0);
        // 朝向：始终对准太阳方向
        body.setYRot(sunYaw);
        body.setYHeadRot(sunYaw);
        body.setYBodyRot(sunYaw);
        body.yBodyRot = sunYaw;
        body.yBodyRotO = sunYaw;
        body.setXRot(0.0f);
    }

    private void discardFakeBody() {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerBodyEntity body = getFakeBody(level);
        if (body != null) {
            body.discard();
        }
        fakeBodyUuid = null;
    }

    public static boolean isSaltedFishFakeBody(Entity entity) {
        return entity instanceof SaltedFishBodyEntity;
    }

    public float getRenderRoll(float partialTick) {
        if (flipTicks <= 0) {
            return sideToRoll(side);
        }
        float from = sideToRoll(previousSide);
        float to = sideToRoll(side);
        // 翻面动画始终朝同一方向旋转，避免来回摆
        if (previousSide == 0 && side == 1) {
            to = 180.0f;
        } else if (previousSide == 1 && side == 0) {
            from = 180.0f;
            to = 360.0f;
        }
        float progress = Mth.clamp((FLIP_TICKS - flipTicks + partialTick) / (float) FLIP_TICKS, 0.0f, 1.0f);
        return Mth.lerp(progress, from, to);
    }

    public float getRenderBounce(float partialTick) {
        if (flipTicks <= 0) {
            return 0.0f;
        }
        float progress = Mth.clamp((FLIP_TICKS - flipTicks + partialTick) / (float) FLIP_TICKS, 0.0f, 1.0f);
        return Mth.sin(progress * Mth.PI) * 0.45f;
    }

    private static float sideToRoll(int side) {
        // 0 = 脸朝上（X 轴旋转 0°），1 = 脸朝下（X 轴旋转 180°）
        return side == 0 ? 0.0f : 180.0f;
    }

    @Override
    public void clientTick() {
        if (activeTicks > 0) {
            activeTicks--;
        }
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
        if (flipTicks > 0) {
            flipTicks--;
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("activeTicks", activeTicks);
        tag.putInt("cooldownTicks", cooldownTicks);
        tag.putInt("flipTicks", flipTicks);
        tag.putInt("side", side);
        tag.putInt("previousSide", previousSide);
        tag.putFloat("sunYaw", sunYaw);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        activeTicks = tag.getInt("activeTicks");
        cooldownTicks = tag.getInt("cooldownTicks");
        flipTicks = tag.getInt("flipTicks");
        side = tag.getInt("side");
        previousSide = tag.getInt("previousSide");
        sunYaw = tag.getFloat("sunYaw");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (fakeBodyUuid != null) {
            tag.putUUID("fakeBodyUuid", fakeBodyUuid);
        }
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        fakeBodyUuid = tag.hasUUID("fakeBodyUuid") ? tag.getUUID("fakeBodyUuid") : null;
    }
}
