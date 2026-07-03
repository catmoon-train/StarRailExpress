package org.agmas.noellesroles.game.roles.neutral.mortician;

import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.ClearBloodParticlesS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
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
public class MorticianBodyMakerPlayerComponent extends SREAbilityPlayerComponent {

    /** 组件键 */
    public static final org.ladysnake.cca.api.v3.component.ComponentKey<MorticianBodyMakerPlayerComponent> KEY = org.ladysnake.cca.api.v3.component.ComponentRegistry
            .getOrCreate(
                    ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mortician_bodymaker"),
                    MorticianBodyMakerPlayerComponent.class);

    // 技能冷却时间
    public static final int ABILITY_COOLDOWN = 60 * 20; // 60秒（默认最大）
    public static final int DRAG_COOLDOWN = 45 * 20; // 曳柩：45秒
    public static final int FUNERAL_COOLDOWN = 60 * 20; // 丧钟：60秒
    public static final int CLEAN_COOLDOWN = 45 * 20; // 清洗：45秒

    // 技能范围
    public static final double DRAG_RANGE = 4.0;
    public static final double FUNERAL_RANGE = 5.0;
    public static final double CLEAN_RANGE = 4.0;

    private final Player player;

    /** 技能冷却 */
    public int cooldown = 0;

    /** 造尸冷却（独立于技能冷却） */
    public int bodyCreationCooldown = 0;
    public static final int BODY_CREATION_COOLDOWN = 180 * 20; // 造尸冷却180秒

    /** 当前模式：0=曳柩, 1=丧钟, 2=清洗 */
    public int currentMode = 0;

    /** 正在拖动的尸体UUID */
    public UUID draggedBodyUuid = null;

    /** 正在拖动的尸体实体（瞬态） */
    private transient PlayerBodyEntity draggedBody = null;

    public MorticianBodyMakerPlayerComponent(Player player) {
        super(player);
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.cooldown = 0;
        this.bodyCreationCooldown = 60 * 20; // 开局60秒冷却
        this.currentMode = 0;
        this.draggedBodyUuid = null;
        this.draggedBody = null;
        this.sync();
    }

    @Override
    public void clear() {
        // 放下尸体并恢复重力
        if (this.draggedBody != null && this.draggedBody.isAlive()) {
            this.draggedBody.setNoGravity(false);
            this.draggedBody = null;
        }
        this.draggedBodyUuid = null;
        this.init();
    }

    @Override
    public int getCooldown() {
        return Math.max(cooldown, bodyCreationCooldown);
    }

    @Override
    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
        this.sync();
    }

    /**
     * 切换技能模式（蹲下按技能键）
     * 拖动尸体时不允许切换
     */
    public void toggleMode() {
        if (this.draggedBody != null && this.draggedBody.isAlive()) {
            return;
        }

        this.currentMode = (this.currentMode + 1) % 3;

        if (player instanceof ServerPlayer serverPlayer) {
            Component modeName;
            switch (this.currentMode) {
                case 0:
                    modeName = Component.translatable("message.noellesroles.mortician_bodymaker.mode.drag")
                            .withStyle(ChatFormatting.GOLD);
                    break;
                case 1:
                    modeName = Component.translatable("message.noellesroles.mortician_bodymaker.mode.funeral")
                            .withStyle(ChatFormatting.RED);
                    break;
                case 2:
                    modeName = Component.translatable("message.noellesroles.mortician_bodymaker.mode.clean")
                            .withStyle(ChatFormatting.AQUA);
                    break;
                default:
                    modeName = Component.translatable("message.noellesroles.mortician_bodymaker.mode.drag")
                            .withStyle(ChatFormatting.GOLD);
            }
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
                    Component
                            .translatable("message.noellesroles.mortician_bodymaker.cooldown",
                                    (this.cooldown + 19) / 20)
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
            // 将尸体放在玩家前方一格
            Vec3 lookVec = serverPlayer.getLookAngle();
            Vec3 dropPos = serverPlayer.position().add(lookVec.x, 0, lookVec.z);
            this.draggedBody.setPos(dropPos.x, dropPos.y, dropPos.z);
            // 恢复尸体重力
            this.draggedBody.setNoGravity(false);
            // 放下尸体
            this.draggedBody = null;
            this.draggedBodyUuid = null;

            // 进入45秒冷却
            this.cooldown = DRAG_COOLDOWN;
            this.sync();

            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.mortician_bodymaker.drag.release")
                            .withStyle(ChatFormatting.GOLD),
                    true);
            return true;
        }

        // 尝试捡起尸体
        PlayerBodyEntity targetBody = findLookedAtBody(serverPlayer);
        if (targetBody == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.mortician_bodymaker.drag.no_body")
                            .withStyle(ChatFormatting.RED),
                    true);
            return true; // 不进入冷却
        }

        // 播放穿上盔甲的音效
        serverPlayer.serverLevel().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 1.0f, 1.0f);

        // 取消尸体重力，开始拖动到头顶
        targetBody.setNoGravity(true);
        this.draggedBody = targetBody;
        this.draggedBodyUuid = targetBody.getUUID();
        this.sync();

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.mortician_bodymaker.drag.start")
                        .withStyle(ChatFormatting.GOLD),
                true);
        return true;
    }

    /**
     * 丧钟技能 - 5格半径内玩家体力减少60%（基于每个玩家的最大体力值），最低减少至0
     */
    private boolean useFuneralAbility(ServerPlayer serverPlayer) {
        Vec3 playerPos = serverPlayer.position();
        double range = FUNERAL_RANGE;

        // 播放钟敲响的音效
        serverPlayer.serverLevel().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2.0f, 0.8f);

        // 对范围内所有玩家直接减少体力的60%（基于各自最大体力值），最低减少至0
        int affected = 0;
        for (Player target : serverPlayer.level().players()) {
            if (target == serverPlayer)
                continue;
            if (GameUtils.isPlayerEliminated(target))
                continue;

            double distance = serverPlayer.distanceTo(target);
            if (distance <= range) {
                // 检查玩家是否有无限体力
                if (!hasInfiniteStamina(target)) {
                    // 获取目标玩家的最大体力值
                    var gameWorld = SREGameWorldComponent.KEY.get(target.level());
                    var targetRole = gameWorld != null ? gameWorld.getRole(target) : null;
                    float maxStamina = targetRole != null ? (float) targetRole.getMaxSprintTime(target) : 100f;

                    if (maxStamina > 0 && maxStamina < Integer.MAX_VALUE) {
                        if (target instanceof PlayerStaminaGetter staminaGetter) {
                            float currentStamina = staminaGetter.starrailexpress$getStamina();
                            float reduceAmount = maxStamina * 0.6f;
                            float newStamina = Math.max(0f, currentStamina - reduceAmount);
                            staminaGetter.starrailexpress$setStamina(newStamina);
                        }
                    }
                    affected++;
                }
            }
        }

        // 进入60秒冷却
        this.cooldown = FUNERAL_COOLDOWN;
        this.sync();

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.mortician_bodymaker.funeral.used", affected)
                        .withStyle(ChatFormatting.RED),
                true);
        return true;
    }

    /**
     * 清洗技能 - 消除4格半径内血液
     */
    private boolean useCleanAbility(ServerPlayer serverPlayer) {
        // 清除血液效果（通过反射调用血液mod的API）
        clearBloodNearPlayer(serverPlayer, CLEAN_RANGE);

        // 进入45秒冷却
        this.cooldown = CLEAN_COOLDOWN;
        this.sync();

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.mortician_bodymaker.clean.used")
                        .withStyle(ChatFormatting.AQUA),
                true);
        return true;
    }

    /**
     * 检查玩家是否有无限体力（与职业定义一致：maxSprintTime == Integer.MAX_VALUE）
     */
    private boolean hasInfiniteStamina(Player player) {
        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld != null) {
            var role = gameWorld.getRole(player);
            if (role != null && role.getMaxSprintTime(player) == Integer.MAX_VALUE) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清除玩家周围的血液效果（仅清除范围内的血粒子）
     */
    private void clearBloodNearPlayer(ServerPlayer serverPlayer, double range) {
        // 播放清除音效作为反馈
        serverPlayer.serverLevel().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.5f, 1.5f);

        // 向所有玩家发送清除血液粒子数据包（附带位置和范围）
        for (ServerPlayer onlinePlayer : serverPlayer.serverLevel().players()) {
            ServerPlayNetworking.send(onlinePlayer,
                    new ClearBloodParticlesS2CPacket(serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                            range));
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
            if (org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity.isDoomedSinnerBody(body)) {
                continue;
            }

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
     * 检查造尸是否可用
     */
    public boolean canCreateBody() {
        return this.bodyCreationCooldown <= 0;
    }

    /**
     * 创建尸体（尸匠能力）
     * 
     * @param target      目标玩家
     * @param deathReason 死亡原因ID
     */
    public boolean createBody(ServerPlayer target, String deathReason) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        // 检查造尸冷却
        if (this.bodyCreationCooldown > 0) {
            return false;
        }

        try {
            PlayerBodyEntity playerBody = TMMEntities.PLAYER_BODY.create(serverPlayer.level());

            if (playerBody != null) {
                playerBody.setPlayerUuid(target.getUUID());

                Vec3 forward = serverPlayer.getViewVector(1.0f).scale(1);
                Vec3 spawnPos = serverPlayer.position().add(forward);
                playerBody.moveTo(spawnPos.x, serverPlayer.getY(), spawnPos.z,
                        serverPlayer.getYRot(), 0f);
                playerBody.setYRot(serverPlayer.getYRot());
                playerBody.setYHeadRot(serverPlayer.getYRot());
                playerBody.yBodyRot = serverPlayer.getYRot();
                playerBody.yBodyRotO = serverPlayer.getYRot();
                playerBody.setXRot(0f);

                PlayerBodyEntityComponent bodyComponent = PlayerBodyEntityComponent.KEY.get(playerBody);
                bodyComponent.setOwnerName(target.getScoreboardName(), false);
                ResourceLocation deathReasonLoc = deathReason != null && !deathReason.isEmpty()
                        ? ResourceLocation.parse(deathReason)
                        : ResourceLocation.fromNamespaceAndPath("noellesroles", "mortician_bodymaker");
                bodyComponent.setDeathReason(deathReasonLoc.toString(), true);

                bodyComponent.playerRole = ModRoles.MORTICIAN_BODYMAKER.identifier();
                bodyComponent.isFakeBody = true;

                bodyComponent.sync();
                serverPlayer.level().addFreshEntity(playerBody);

                // 进入造尸冷却
                this.bodyCreationCooldown = BODY_CREATION_COOLDOWN;
                this.sync();

                serverPlayer.serverLevel().players().forEach(p -> {
                    serverPlayer.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(),
                            SoundEvents.SKELETON_CONVERTED_TO_STRAY, SoundSource.PLAYERS, 1.0f, 1.0f);
                });

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
            // 不再是葬仪角色，强制解除拖动状态并恢复重力
            if (this.draggedBody != null) {
                if (this.draggedBody.isAlive())
                    this.draggedBody.setNoGravity(false);
                this.draggedBody = null;
                this.draggedBodyUuid = null;
                this.sync();
            }
            return;
        }

        // 旁观者模式立刻解除拖动
        if (player.isSpectator()) {
            if (this.draggedBody != null) {
                if (this.draggedBody.isAlive())
                    this.draggedBody.setNoGravity(false);
                this.draggedBody = null;
                this.draggedBodyUuid = null;
                this.sync();
            }
            return;
        }

        // 玩家死亡时立即解除拖动（必须在SAFE_TIME检查之前，否则死亡后可能被跳过）
        if (!player.isAlive()) {
            if (this.draggedBody != null) {
                if (this.draggedBody.isAlive())
                    this.draggedBody.setNoGravity(false);
                this.draggedBody = null;
                this.draggedBodyUuid = null;
                this.sync();
            }
            return;
        }

        if (player.hasEffect(org.agmas.noellesroles.init.ModEffects.SAFE_TIME)) {
            return;
        }

        // 玩家死亡时自动解除拖动 — 已移至SAFE_TIME检查之前确保可靠性

        // 减少技能冷却
        if (this.cooldown > 0) {
            this.cooldown--;
            if (this.cooldown % 60 == 0 || this.cooldown == 0) {
                this.sync();
            }
        }

        // 减少造尸冷却
        if (this.bodyCreationCooldown > 0) {
            this.bodyCreationCooldown--;
            if (this.bodyCreationCooldown % 60 == 0 || this.bodyCreationCooldown == 0) {
                this.sync();
            }
        }

        // 更新拖动的尸体位置（举在头顶2.1格高处，做出类似举起三叉戟的动作）
        if (this.draggedBody != null && this.draggedBody.isAlive()) {
            Vec3 playerPos = player.position();
            this.draggedBody.setPos(playerPos.x, playerPos.y + 2.1, playerPos.z);
            this.draggedBody.setYRot(player.getYRot());
            this.draggedBody.setYHeadRot(player.getYRot());
            this.draggedBody.yBodyRot = player.getYRot();
            // 通用物证·拖痕：记录该尸体被拖动过（首次记录即同步）
            io.wifi.starrailexpress.cca.SREGameWorldComponent forensicGw = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY
                    .get(player.level());
            if (forensicGw != null && this.draggedBody.getPlayerUuid() != null) {
                forensicGw.markCorpseDragged(this.draggedBody.getPlayerUuid());
            }
        } else if (this.draggedBody != null) {
            // 尸体已消失，自动解除拖动并使技能进入冷却
            this.draggedBody = null;
            this.draggedBodyUuid = null;
            this.cooldown = DRAG_COOLDOWN;
            this.sync();
        }
    }

    @Override
    public void clientTick() {
        if (this.cooldown > 1) {
            this.cooldown--;
        }
        if (this.bodyCreationCooldown > 1) {
            this.bodyCreationCooldown--;
        }
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        tag.putInt("Cooldown", this.cooldown);
        tag.putInt("BodyCreationCooldown", this.bodyCreationCooldown);
        tag.putInt("CurrentMode", this.currentMode);
        if (this.draggedBodyUuid != null) {
            tag.putUUID("DraggedBodyUuid", this.draggedBodyUuid);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        this.cooldown = tag.getInt("Cooldown");
        this.bodyCreationCooldown = tag.getInt("BodyCreationCooldown");
        this.currentMode = tag.getInt("CurrentMode");
        if (tag.contains("DraggedBodyUuid")) {
            this.draggedBodyUuid = tag.getUUID("DraggedBodyUuid");
            if (player.level() != null) {
                List<PlayerBodyEntity> bodies = player.level().getEntitiesOfClass(PlayerBodyEntity.class,
                        new AABB(player.getX() - 5, player.getY() - 5, player.getZ() - 5,
                                player.getX() + 5, player.getY() + 5, player.getZ() + 5));
                for (PlayerBodyEntity body : bodies) {
                    if (org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity.isDoomedSinnerBody(body)) {
                        continue;
                    }
                    if (body.getUUID().equals(this.draggedBodyUuid)) {
                        this.draggedBody = body;
                        break;
                    }
                }
            }
        } else {
            this.draggedBodyUuid = null;
            this.draggedBody = null;
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
