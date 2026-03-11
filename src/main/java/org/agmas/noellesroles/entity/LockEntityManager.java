package org.agmas.noellesroles.entity;

import io.wifi.starrailexpress.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.BlockUtils;
import org.agmas.noellesroles.utils.Pair;

import java.util.*;

/**
 * 锁实体管理器单例
 * - 管理锁实体与门之间的关系
 * - 使用方式：使用撬锁器开门时查询是否被锁实体影响
 * - NOTE :
 * 该管理器并不会在关闭地图(也可能是游戏？)后保存数据，总之就是重启之后所有锁实体将不会影响到门，但是考虑到游戏也不会在关闭后继续，所以没必要管
 */
public class LockEntityManager {
    private LockEntityManager() {
        // 在此处添加锁实体影响的物品
        canBeAffectedItems.add(TMMItems.KEY);
        canBeAffectedItems.add(TMMItems.LOCKPICK);
        canBeAffectedItems.add(ModItems.MASTER_KEY);
        canBeAffectedItems.add(ModItems.MASTER_KEY_P);
        canBeAffectedItems.add(FunnyItems.BOWEN_BADGE);
        // 在此处添加可以撬锁的物品
        canBeUsedToUnLock.add(TMMItems.LOCKPICK);
        canBeUsedToUnLock.add(ModItems.MASTER_KEY_P);

    };

    public static LockEntityManager getInstance() {
        return instance;
    }

    /** 重置锁实体映射 */
    public void resetLockEntities() {
        // 清理所有已删除的锁实体
        cleanDeadEntities();
        // 清理仍然存在的锁实体
        lockEntities.values().forEach(stack -> stack.forEach(Entity::discard));
        lockEntities.clear();
    }

    /** 清理所有已删除的锁实体，由于锁操作不多，每次操作的时候清理一下最方便快捷，对性能也没啥影响 */
    public void cleanDeadEntities() {
        lockEntities.values().forEach(stack -> stack.removeIf(entity -> entity == null || entity.isRemoved()));
        lockEntities.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /** 清理指定位置的已删除的锁实体，有更好的性能 */
    public void cleanStackDeadEntities(Vec3i pos) {
        Stack<LockEntity> stack;
        if (lockEntities.containsKey(pos))
            stack = lockEntities.get(pos);
        else
            return;

        stack.removeIf(entity -> entity == null || entity.isRemoved());

        if (stack.isEmpty())
            lockEntities.remove(pos);
    }

    /** 获取对应位置的锁实体 */
    public LockEntity getLockEntity(Vec3i pos) {
        cleanStackDeadEntities(pos);
        if (lockEntities.containsKey(pos))
            return lockEntities.get(pos).peek();
        return null;
    }

    /** 添加锁实体 */
    public void addLockEntity(Vec3i pos, LockEntity lockEntity) {
        cleanStackDeadEntities(pos);
        if (!lockEntities.containsKey(pos)) {
            Stack<LockEntity> stack = new Stack<>();
            lockEntities.put(pos, stack);
        }
        lockEntities.get(pos).push(lockEntity);
    }

    /**
     * 移除栈顶锁实体
     * <p>
     * 移除锁实体时，会自动移除栈顶锁实体，如果栈为空则移除整个位置
     * </p>
     * 
     * @param pos 锁实体的位置
     */
    public void removeLockEntity(Vec3i pos) {
        cleanStackDeadEntities(pos);
        if (lockEntities.containsKey(pos)) {
            lockEntities.get(pos).peek().discard();
            lockEntities.get(pos).pop();
            if (lockEntities.get(pos).isEmpty()) {
                lockEntities.remove(pos);
            }
        }
    }

    /**
     * 移除指定锁实体
     * 
     * @param pos        锁实体的位置
     * @param lockEntity 目标锁实体
     */
    public void removeLockEntity(Vec3i pos, LockEntity lockEntity) {
        cleanStackDeadEntities(pos);
        if (lockEntities.containsKey(pos)) {
            lockEntities.get(pos).remove(lockEntity);
            if (lockEntity != null)
                lockEntity.discard();

            if (lockEntities.get(pos).isEmpty()) {
                lockEntities.remove(pos);
            }
        }
    }

    public void removeLockEntity(Vec3i pos, int entityId) {
        cleanStackDeadEntities(pos);
        if (lockEntities.containsKey(pos)) {
            // 使用迭代器寻找目标实体调用销毁函数并移出队列
            Iterator<LockEntity> iterator = lockEntities.get(pos).iterator();
            while (iterator.hasNext()) {
                LockEntity entity = iterator.next();
                if (entity.getId() == entityId) {
                    // 析构实体
                    entity.discard();
                    // 从队列移除
                    iterator.remove();
                    break;
                }
            }
            if (lockEntities.get(pos).isEmpty()) {
                lockEntities.remove(pos);
            }
        }
    }

    /**
     * 获取附近锁实体的位置
     * <p>
     * - 优先查找当前位置（应为门的上半部分位置）是否有锁实体，没有的话再查找左右两边
     * </p>
     * 
     * @param checkPos 检查点位置
     * @param world    当前世界
     * @return 最近锁实体的位置
     */
    public BlockPos getNearByLockPos(BlockPos checkPos, Level world) {
        BlockPos ans = null;
        if (this.getLockEntity(checkPos) != null) {
            ans = checkPos;
        } else if (world.getBlockEntity(checkPos.below()) instanceof DoorBlockEntity entity) {
            switch (entity.getFacing()) {
                case NORTH:
                case SOUTH:
                    if (this.getLockEntity(checkPos.east()) != null)
                        ans = checkPos.east();
                    else if (this.getLockEntity(checkPos.west()) != null) {
                        ans = checkPos.west();
                    }
                    break;
                case EAST:
                case WEST:
                    if (this.getLockEntity(checkPos.north()) != null)
                        ans = checkPos.north();
                    else if (this.getLockEntity(checkPos.south()) != null) {
                        ans = checkPos.south();
                    }
                    break;
                default:
                    break;
            }
        }
        return ans;
    }

    /**
     * 使锁能锁门
     * <p>
     * - 如果进行上锁，则会直接上锁：使乘客钥匙无效
     * - 如果解锁时门上仍然有锁则无法解锁
     * </p>
     */
    public static void setDoorLocked(Level world, DoorBlockEntity doorEntity, boolean trapped) {
        String currentKeyName = doorEntity.getKeyName();
        if (currentKeyName == null)
            currentKeyName = "";

        if (trapped) {
            doorEntity.setKeyName("locked:" + currentKeyName);
        } else {
            if (LockEntityManager.getInstance().getLockEntity(
                    LockEntityManager.getInstance().getNearByLockPos(doorEntity.getBlockPos().above(),
                            world)) == null) {
                // 只移除 "locked:" 前缀，而不是所有出现的 "locked:"
                int index = currentKeyName.indexOf("locked:");
                if (index != -1) {
                    doorEntity.setKeyName(currentKeyName.substring(0, index) + currentKeyName.substring(index + 7));
                }
            }
        }
    }

    /**
     * 门是否已经上锁
     * 
     * @param door    当前门实体
     */
    public static boolean isDoorLocked(DoorBlockEntity door) {
        // 锁门：包括临近的门
        return door.getKeyName().contains("locked:");
    }

    /**
     * 锁门及左右的门
     * 
     * @param door    当前门实体
     * @param world   当前世界
     * @param trapped 是否锁门
     */
    public static void lockNearByDoors(DoorBlockEntity door, Level world, boolean trapped) {
        // 锁门：包括临近的门
        Pair<DoorBlockEntity, DoorBlockEntity> nearByDoors = BlockUtils.getNeighbourDoor(door, world);
        if (nearByDoors.first != null) {
            LockEntityManager.setDoorLocked(world, nearByDoors.first, trapped);
        }
        if (nearByDoors.second != null) {
            LockEntityManager.setDoorLocked(world, nearByDoors.second, trapped);
        }
        LockEntityManager.setDoorLocked(world, door, trapped);
    }

    public ArrayList<Item> getCanBeAffectedItems() {
        return canBeAffectedItems;
    }

    public ArrayList<Item> getCanBeUsedToUnLock() {
        return canBeUsedToUnLock;
    }

    private static final LockEntityManager instance = new LockEntityManager();
    // 使用位置与锁列表对应：每个格子可以匹配多个锁实体
    private static final Map<Vec3i, Stack<LockEntity>> lockEntities = new HashMap<>();
    private final ArrayList<Item> canBeAffectedItems = new ArrayList<>();
    private final ArrayList<Item> canBeUsedToUnLock = new ArrayList<>();
}
