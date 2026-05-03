package io.wifi.events.day_night_fight.entity;

import io.wifi.events.day_night_fight.block.DNFTaskPointBlock;
import io.wifi.events.day_night_fight.cca.SREPlayerClueComponent;
import io.wifi.events.day_night_fight.clue.ClueSystem;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DNFTaskPointEntity extends Entity {
    private BlockPos sourcePos = BlockPos.ZERO;
    // 跟踪已经解锁线索的玩家，避免重复解锁
    private java.util.Set<java.util.UUID> unlockedPlayers = new java.util.HashSet<>();

    public DNFTaskPointEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    public static void spawnForBlock(ServerLevel level, BlockPos pos) {
        boolean exists = !level.getEntitiesOfClass(DNFTaskPointEntity.class, new AABB(pos).inflate(1.5),
                entity -> entity.isForBlock(pos)).isEmpty();
        if (exists) {
            return;
        }
        DNFTaskPointEntity entity = new DNFTaskPointEntity(DNFEntities.TASK_POINT, level);
        entity.setSourcePos(pos);
        entity.setPos(pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5);
        level.addFreshEntity(entity);
    }

    public boolean isForBlock(BlockPos pos) {
        return sourcePos.equals(pos);
    }

    public void setSourcePos(BlockPos sourcePos) {
        this.sourcePos = sourcePos.immutable();
    }

    public BlockPos getSourcePos() {
        return sourcePos;
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        this.setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide) {
            if (!(level().getBlockState(sourcePos).getBlock() instanceof DNFTaskPointBlock)) {
                discard();
                return;
            }
            setPos(sourcePos.getX() + 0.5, sourcePos.getY() + 1.15, sourcePos.getZ() + 0.5);
            
            // 检查附近是否有玩家并解锁线索
            checkAndUnlockCluesForNearbyPlayers();
        }
    }

    private void checkAndUnlockCluesForNearbyPlayers() {
        ServerLevel serverLevel = (ServerLevel) this.level();
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(serverLevel);
        
        // 只在游戏进行中处理线索解锁
        if (gameWorldComponent.getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) {
            return;
        }
        
        // 获取附近的玩家（3格范围内）
        AABB searchArea = this.getBoundingBox().inflate(3.0);
        java.util.List<ServerPlayer> nearbyPlayers = serverLevel.getEntitiesOfClass(ServerPlayer.class, searchArea);
        
        for (ServerPlayer player : nearbyPlayers) {
            // 跳过已解锁的玩家
            if (unlockedPlayers.contains(player.getUUID())) {
                continue;
            }
            
            // 检查玩家是否存活
            if (!io.wifi.events.day_night_fight.DNF.isDnfAlive(player)) {
                continue;
            }
            
            // 解锁线索
            unlockClueForPlayer(player);
            unlockedPlayers.add(player.getUUID());
            
            // 发送提示消息
            player.displayClientMessage(Component.translatable("message.dnf.clue.unlocked"), false);
        }
    }

    private void unlockClueForPlayer(ServerPlayer player) {
        // 获取任务点类型来决定线索内容
        DNFTaskPointBlock block = (DNFTaskPointBlock) player.level().getBlockState(sourcePos).getBlock();
        DNFTaskPointBlock.TaskPointType taskType = block.getTaskPointType();
        
        String clueTitle = "未知线索";
        String clueContent = "发现了重要的线索！";
        
        // 根据任务点类型设置不同的线索内容
        switch (taskType) {
            case CLEANING:
                clueTitle = "监狱清理线索";
                clueContent = "在清理监狱灰尘时，发现了一些可疑的痕迹。看起来有人在这里停留过很长时间，可能是为了隐藏什么。";
                break;
            case WEB:
                clueTitle = "图书馆线索";
                clueContent = "清理图书馆蜘蛛网时，在书架后面发现了一张被遗忘的纸条。上面写着一些模糊的字迹，似乎与实验有关。";
                break;
            case EXCHANGE:
                clueTitle = "兑换点线索";
                clueContent = "在兑换点附近，发现了一些奇怪的脚印和掉落的物品。这里可能经常有人秘密会面。";
                break;
        }
        
        // 使用现有的ClueSystem来创建和记录线索
        var entry = ClueSystem.spawnClueEntity((ServerLevel) player.level(), player.blockPosition(), clueTitle, clueContent);
        ClueSystem.recordClue(player, entry);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("SourcePos")) {
            sourcePos = BlockPos.of(tag.getLong("SourcePos"));
        }
        // 读取已解锁玩家的数据
        if (tag.contains("UnlockedPlayers")) {
            unlockedPlayers.clear();
            net.minecraft.nbt.ListTag unlockedList = tag.getList("UnlockedPlayers", 8);
            for (int i = 0; i < unlockedList.size(); i++) {
                unlockedPlayers.add(java.util.UUID.fromString(unlockedList.getString(i)));
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("SourcePos", sourcePos.asLong());
        // 保存已解锁玩家的数据
        net.minecraft.nbt.ListTag unlockedList = new net.minecraft.nbt.ListTag();
        for (java.util.UUID uuid : unlockedPlayers) {
            unlockedList.add(net.minecraft.nbt.StringTag.valueOf(uuid.toString()));
        }
        tag.put("UnlockedPlayers", unlockedList);
    }
}