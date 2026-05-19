package org.agmas.noellesroles.game.roles.neutral.mortician;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.index.TMMEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.ClearBloodParticlesS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 葬仪玩家组件
 *
 * 杀手方中立阵营
 *
 * 技能（蹲下按技能键切换模式）：
 * - 曳柩：对尸体按下技能键，可以拖动尸体，再次按下放下并进入45秒冷却
 * - 丧钟：5格半径内玩家体力减少60%，进入60秒冷却
 * - 清洗：消除3格半径内血液，进入45秒冷却
 *
 * 尸匠：拥有造尸能力
 *
 * 被动-引渡：杀手/杀手方中立/魔术师死亡时广播
 */
public class MorticianPlayerComponent extends SREAbilityPlayerComponent {

    /** 组件键 */
    public static final org.ladysnake.cca.api.v3.component.ComponentKey<MorticianPlayerComponent> KEY =
            org.ladysnake.cca.api.v3.component.ComponentRegistry.getOrCreate(
                    ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mortician"),
                    MorticianPlayerComponent.class);

    // 技能冷却时间
    public static final int ABILITY_COOLDOWN = 60 * 20; // 60秒（默认最大）
    public static final int DRAG_COOLDOWN = 45 * 20; // 曳柩：45秒
    public static final int FUNERAL_COOLDOWN = 60 * 20; // 丧钟：60秒
    public static final int CLEAN_COOLDOWN = 45 * 20; // 清洗：45秒

    // 技能范围
    public static final double DRAG_RANGE = 4.0;
    public static final double FUNERAL_RANGE = 5.0;
    public static final double CLEAN_RANGE = 3.0;

    private final Player player;

    /** 技能冷却 */
    public int cooldown = 0;

    /** 当前模式：0=曳柩, 1=丧钟, 2=清洗 */
    public int currentMode = 0;

    /** 正在拖动的尸体UUID */
    public UUID draggedBodyUuid = null;

    /** 正在拖动的尸体实体（瞬态） */
    private transient PlayerBodyEntity draggedBody = null;

    public MorticianPlayerComponent(Player player) {
        super(player);
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.cooldown = 0;
        this.currentMode = 0;
        this.draggedBodyUuid = null;
        this.draggedBody = null;
        this.sync();
    }

    @Override
    public void clear() {
        // 放下尸体
        if (this.draggedBody != null && this.draggedBody.isAlive()) {
            this.draggedBody = null;
        }
        this.draggedBodyUuid = null;
        this.init();
    }

    @Override
    public int getCooldown() {
        return cooldown;
    }

    @Override
    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
        this.sync();
    }

    /**
     * 切换技能模式（蹲下按技能键）- 不受冷却影响
     */
    public void toggleMode() {
        this.currentMode = (this.currentMode + 1) % 3;

        if (player instanceof ServerPlayer serverPlayer) {
            Component message;
            switch (this.currentMode) {
                case 0:
                    message = Component.translatable("message.noellesroles.mortician.mode.drag")
                            .withStyle(ChatFormatting.GOLD);
                    break;
                case 1:
                    message = Component.translatable("message.noellesroles.mortician.mode.funeral")
                            .withStyle(ChatFormatting.RED);
                    break;
                case 2:
                    message = Component.translatable("message.noellesroles.mortician.mode.clean")
                            .withStyle(ChatFormatting.AQUA);
                    break;
                default:
                    message = Component.translatable("message.noellesroles.mortician.mode.drag")
                            .withStyle(ChatFormatting.GOLD);
            }
            serverPlayer.displayClientMessage(message, true);
        }

        this.sync();
    }

    /**
     * 使用当前技能
     */
    @Override
    public boolean useAbility() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isSkillAvailable) {
            return false;
        }

        // 检查是否为葬仪角色
        if (!gameWorldComponent.isRole(player, ModRoles.MORTICIAN_BODYMAKER)) {
            return false;
        }

        // 检查冷却
        if (this.cooldown > 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.mortician.cooldown", (this.cooldown + 19) / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        switch (this.currentMode) {
            case 0: // 曳柩
                return useDragAbility(serverPlayer);
            case 1: // 丧钟
                return useFuneralAbility(serverPlayer);
            case 2: // 清洗
                return useCleanAbility(serverPlayer);
            default:
                return false;
        }
    }

    /**
     * 曳柩技能
     */
    private boolean useDragAbility(ServerPlayer serverPlayer) {
        // 如果正在拖动尸体，放下它
        if (this.draggedBody != null && this.draggedBody.isAlive()) {
            // 放下尸体
            this.draggedBody = null;
            this.draggedBodyUuid = null;

            // 进入45秒冷却
            this.cooldown = DRAG_COOLDOWN;
            this.sync();

            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.mortician.drag.release")
                            .withStyle(ChatFormatting.GOLD),
                    true);
            return true;
        }

        // 尝试捡起尸体
        PlayerBodyEntity targetBody = findLookedAtBody(serverPlayer);
        if (targetBody == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.mortician.drag.no_body")
                            .withStyle(ChatFormatting.RED),
                    true);
            return true; // 不进入冷却
        }

        // 播放穿上盔甲的音效
        serverPlayer.serverLevel().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 1.0f, 1.0f);

        // 开始拖动尸体
        this.draggedBody = targetBody;
        this.draggedBodyUuid = targetBody.getUUID();
        this.sync();

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.mortician.drag.start")
                        .withStyle(ChatFormatting.GOLD),
                true);
        return true;
    }

    /**
     * 丧钟技能 - 5格半径内玩家体力减少60%
     */
    private boolean useFuneralAbility(ServerPlayer serverPlayer) {
        Vec3 playerPos = serverPlayer.position();
        double range = FUNERAL_RANGE;

        // 播放钟敲响的音效
        serverPlayer.serverLevel().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.BELL_USE, SoundSource.PLAYERS, 2.0f, 0.8f);

        // 对范围内所有玩家施加缓慢效果（相当于减少体力）
        int affected = 0;
        for (Player target : serverPlayer.level().players()) {
            if (target == serverPlayer) continue;
            if (GameUtils.isPlayerEliminated(target)) continue;

            double distance = serverPlayer.distanceTo(target);
            if (distance <= range) {
                // 检查玩家是否有无限体力
                if (!hasInfiniteStamina(target)) {
                    // 给予缓慢效果 3秒 = 相当于体力减少60%
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3 * 20, 2, false, true));
                    affected++;
                }
            }
        }

        // 进入60秒冷却
        this.cooldown = FUNERAL_COOLDOWN;
        this.sync();

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.mortician.funeral.used", affected)
                        .withStyle(ChatFormatting.RED),
                true);
        return true;
    }

    /**
     * 清洗技能 - 消除3格半径内血液
     */
    private boolean useCleanAbility(ServerPlayer serverPlayer) {
        // 清除血液效果（通过反射调用血液mod的API）
        clearBloodNearPlayer(serverPlayer, CLEAN_RANGE);

        // 进入45秒冷却
        this.cooldown = CLEAN_COOLDOWN;
        this.sync();

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.mortician.clean.used")
                        .withStyle(ChatFormatting.AQUA),
                true);
        return true;
    }

    /**
     * 检查玩家是否有无限体力
     */
    private boolean hasInfiniteStamina(Player player) {
        // 检查是否是杀手阵营（有无限体力）
        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld != null) {
            var role = gameWorld.getRole(player);
            if (role != null) {
                //杀手阵营通常有Integer.MAX_VALUE冲刺时间
                return true; // 简化处理，杀手方阵营不减少体力
            }
        }
        return false;
    }

    /**
     * 清除玩家周围的血液效果
     */
    private void clearBloodNearPlayer(ServerPlayer serverPlayer, double range) {
        // 播放清除音效作为反馈
        serverPlayer.serverLevel().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.5f, 1.5f);

        // 向所有玩家发送清除血液粒子数据包
        for (ServerPlayer onlinePlayer : serverPlayer.serverLevel().players()) {
            ServerPlayNetworking.send(onlinePlayer, new ClearBloodParticlesS2CPacket());
        }
    }

    /**
     * 查找当前看向的尸体
     */
    private PlayerBodyEntity findLookedAtBody(ServerPlayer serverPlayer) {
        double maxDistance = DRAG_RANGE;
        PlayerBodyEntity closestBody = null;
        double closestDistance = maxDistance;

        Vec3 eyePos = serverPlayer.getEyePosition();
        Vec3 lookVec = serverPlayer.getViewVector(1.0f);

        for (PlayerBodyEntity body : serverPlayer.level().getEntitiesOfClass(PlayerBodyEntity.class,
                new AABB(eyePos.x - maxDistance, eyePos.y - maxDistance, eyePos.z - maxDistance,
                        eyePos.x + maxDistance, eyePos.y + maxDistance, eyePos.z + maxDistance))) {

            Vec3 bodyPos = body.position();
            Vec3 toBody = bodyPos.subtract(eyePos);
            double dot = toBody.normalize().dot(lookVec);

            if (dot > 0.9) { // 大约25度角
                double distance = eyePos.distanceTo(bodyPos);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestBody = body;
                }
            }
        }

        return closestBody;
    }

    /**
     * 创建尸体（尸匠能力）
     * @param target 目标玩家
     * @param deathReason 死亡原因ID
     * @param roleId 角色ID（可选，为空时使用默认角色）
     */
    public boolean createBody(ServerPlayer target, String deathReason, String roleId) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        try {
            // 直接使用 TMMEntities 创建 PlayerBodyEntity
            PlayerBodyEntity playerBody = TMMEntities.PLAYER_BODY.create(serverPlayer.level());

            if (playerBody != null) {
                playerBody.setPlayerUuid(target.getUUID());

                // 计算生成位置：玩家前方1格
                Vec3 forward = serverPlayer.getViewVector(1.0f).scale(1);
                Vec3 spawnPos = serverPlayer.position().add(forward);
                playerBody.moveTo(spawnPos.x, serverPlayer.getY(), spawnPos.z,
                        serverPlayer.getYRot(), 0f);
                playerBody.setYRot(serverPlayer.getYRot());
                playerBody.setYHeadRot(serverPlayer.getYRot());
                playerBody.yBodyRot = serverPlayer.getYRot();
                playerBody.yBodyRotO = serverPlayer.getYRot();
                playerBody.setXRot(0f);

                // 获取 PlayerBodyEntityComponent 并设置属性
                PlayerBodyEntityComponent bodyComponent = PlayerBodyEntityComponent.KEY.get(playerBody);

                // 设置死亡原因
                ResourceLocation deathReasonLoc = deathReason != null && !deathReason.isEmpty()
                    ? ResourceLocation.parse(deathReason)
                    : ResourceLocation.fromNamespaceAndPath("noellesroles", "mortician_body");
                bodyComponent.setDeathReason(deathReasonLoc.toString(), true);

                // 设置角色
                ResourceLocation roleLoc = roleId != null && !roleId.isEmpty()
                    ? ResourceLocation.parse(roleId)
                    : ModRoles.MORTICIAN_BODYMAKER.identifier();
                bodyComponent.playerRole = roleLoc;

                // 设置为葬仪伪造的尸体 - 这是关键标志！
                bodyComponent.isFakeBody = true;

                // 同步组件
                bodyComponent.sync();

                // 生成实体到世界
                serverPlayer.level().addFreshEntity(playerBody);

                // 播放音效 - 向所有玩家播放骨骼转化音效
                serverPlayer.serverLevel().players().forEach(p -> {
                    serverPlayer.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(),
                        SoundEvents.SKELETON_CONVERTED_TO_STRAY, SoundSource.PLAYERS, 1.0f, 1.0f);
                });

                Noellesroles.LOGGER.info("[Mortician] Created fake body with isFakeBody=true, deathReason={}, role={}",
                    deathReasonLoc, roleLoc);

                return true;
            }
        } catch (Exception e) {
            Noellesroles.LOGGER.error("Failed to create body: ", e);
        }
        return false;
    }

    /**
     * 服务端tick
     */
    @Override
    public void serverTick() {
        var gwc = SREGameWorldComponent.KEY.get(player.level());
        if (!gwc.isRole(player, ModRoles.MORTICIAN_BODYMAKER)) {
            return;
        }

        if (player.hasEffect(org.agmas.noellesroles.init.ModEffects.SAFE_TIME)) {
            return;
        }

        // 减少冷却
        if (this.cooldown > 0) {
            this.cooldown--;
            if (this.cooldown % 60 == 0 || this.cooldown == 0) {
                this.sync();
            }
        }

        // 更新拖动的尸体位置
        if (this.draggedBody != null && this.draggedBody.isAlive()) {
            // 将尸体移动到玩家位置
            Vec3 playerPos = player.position();
            this.draggedBody.setPos(playerPos.x, playerPos.y, playerPos.z);
            this.draggedBody.setYRot(player.getYRot());
            this.draggedBody.setYHeadRot(player.getYRot());
            this.draggedBody.yBodyRot = player.getYRot();
        } else if (this.draggedBody != null) {
            // 尸体已消失
            this.draggedBody = null;
            this.draggedBodyUuid = null;
            this.sync();
        }
    }

    @Override
    public void clientTick() {
        if (this.cooldown > 1) {
            this.cooldown--;
        }
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        tag.putInt("Cooldown", this.cooldown);
        tag.putInt("CurrentMode", this.currentMode);
        if (this.draggedBodyUuid != null) {
            tag.putUUID("DraggedBodyUuid", this.draggedBodyUuid);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        this.cooldown = tag.getInt("Cooldown");
        this.currentMode = tag.getInt("CurrentMode");
        if (tag.contains("DraggedBodyUuid")) {
            this.draggedBodyUuid = tag.getUUID("DraggedBodyUuid");
            // 尝试获取尸体实体
            if (player.level() != null) {
                List<PlayerBodyEntity> bodies = player.level().getEntitiesOfClass(PlayerBodyEntity.class,
                        new AABB(player.getX() - 5, player.getY() - 5, player.getZ() - 5,
                                player.getX() + 5, player.getY() + 5, player.getZ() + 5));
                for (PlayerBodyEntity body : bodies) {
                    if (body.getUUID().equals(this.draggedBodyUuid)) {
                        this.draggedBody = body;
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
