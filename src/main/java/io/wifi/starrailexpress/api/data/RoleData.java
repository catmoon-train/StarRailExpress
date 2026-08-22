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
package io.wifi.starrailexpress.api.data;

import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SRERoleDataPlayerComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * <p>
 * 玩家职业NBT数据接口。
 * </p>
 * <p>
 * 仅支持保存当前职业的数据。每次切换职业时都会创建新的实例，因此通常无需在 {@link #init()} 和 {@link #clear()}
 * 中实现重置逻辑。
 * </p>
 * <p>
 * 所有实现类必须提供一个接受 {@link RoleDataContext} 参数的构造方法，以便通过
 * {@link #create(Class, Player)} 创建实例。
 * </p>
 *
 * @author StarRailExpress Team
 * @version 1.0
 * @see SRERoleDataPlayerComponent
 * @see RoleDataContext
 */
public interface RoleData {

    /**
     * 获取玩家当前的职业数据，可能为 {@code null}。
     *
     * @param player 目标玩家，不能为 {@code null}
     * @return 当前职业数据，若玩家未持有任何职业或数据未初始化则返回 {@code null}
     */
    @Nullable
    public static RoleData getNullable(Player player) {
        if (player == null) {
            return null;
        }
        SRERoleDataPlayerComponent component = SRERoleDataPlayerComponent.KEY.get(player);
        if (component == null) {
            return null;
        }
        return component.roleData;
    }

    /**
     * 玩家是否真正持有该职业数据（非空、非占位实例）。
     */
    public static boolean isAttached(@Nullable RoleData data) {
        return data != null && !data.isPlaceholder();
    }

    /**
     * 获取玩家真正持有的指定类型职业数据。类型不匹配或尚未初始化时返回 {@code null}。
     */
    @Nullable
    public static <T extends RoleData> T getAttached(Class<T> clazz, Player player) {
        if (player == null || clazz == null) {
            return null;
        }
        RoleData roleData = getNullable(player);
        if (roleData != null && clazz.isInstance(roleData) && !roleData.isPlaceholder()) {
            return clazz.cast(roleData);
        }
        return null;
    }

    /**
     * 获取玩家当前的职业数据，并将其转换为指定类型。
     *
     * @param clazz  期望的职业数据类型
     * @param player 目标玩家
     * @param <T>    职业数据类型
     * @return 包含转换后数据的 {@code Optional}，若数据不存在或类型不匹配则返回空
     */
    @Nullable
    public static <T extends RoleData> Optional<T> getOptional(Class<T> clazz, Player player) {
        return Optional.ofNullable(getNullable(clazz, player));
    }

    /**
     * 获取指定类型的职业数据。
     * <p>
     * 玩家未持有该职业时返回该类型的<strong>空占位实例</strong>（字段为默认值，
     * {@link #isPlaceholder()} 为 {@code true}），因此可以直接读字段而不会 NPE。
     * 需要执行技能/写回同步时请用 {@link #ifPresent}、{@link #test} 或 {@link #getAttached}。
     * </p>
     *
     * @return 不存在必定返回NULL
     */
    @Nullable
    public static <T extends RoleData> T getNullable(Class<T> clazz, Player player) {
        T attached = getAttached(clazz, player);
        if (attached != null) {
            return attached;
        }
        return null;
    }

    /**
     * 若玩家持有指定类型的职业数据则执行 {@code action}，否则忽略。
     */
    public static <T extends RoleData> void ifPresent(Class<T> clazz, Player player, Consumer<T> action) {
        T data = getAttached(clazz, player);
        if (data != null && action != null) {
            action.accept(data);
        }
    }

    /**
     * 若玩家持有指定类型的职业数据则用其测试 {@code predicate}，否则返回 {@code false}。
     */
    public static <T extends RoleData> boolean test(Class<T> clazz, Player player, Predicate<T> predicate) {
        T data = getAttached(clazz, player);
        return data != null && predicate != null && predicate.test(data);
    }

    /**
     * 若玩家持有指定类型的职业数据则映射为结果，否则返回 {@code defaultValue}。
     */
    @Nullable
    public static <T extends RoleData, R> R map(Class<T> clazz, Player player, Function<T, R> mapper, R defaultValue) {
        T data = getAttached(clazz, player);
        if (data == null || mapper == null) {
            return defaultValue;
        }
        return mapper.apply(data);
    }

    /**
     * 获取玩家当前的职业数据并转换为指定类型，若数据不存在或类型不匹配，则尝试使用 {@link #create(Class, Player)}
     * 创建新实例并返回。
     * <p>
     * 注意：创建的新实例不会自动附加到玩家组件中，调用方需自行处理存储逻辑。
     * </p>
     *
     * @param clazz  期望的职业数据类型
     * @param player 目标玩家
     * @param <T>    职业数据类型
     * @return 已存在的或新创建的数据实例，若创建失败则返回 {@code null}
     */
    @Nullable
    public static <T extends RoleData> T getOrCreate(Class<T> clazz, Player player) {
        T existing = getAttached(clazz, player);
        if (existing != null) {
            return existing;
        }
        return create(clazz, player);
    }

    /**
     * 通过反射调用目标类的构造方法（参数为 {@link RoleDataContext}）创建职业数据实例。
     *
     * @param clazz  要创建的职业数据类型
     * @param player 关联的玩家
     * @param <T>    职业数据类型
     * @return 新创建的实例，若构造失败（如缺少构造方法、反射异常等）则返回 {@code null}，并记录错误日志
     */
    @Nullable
    public static <T extends RoleData> T create(Class<T> clazz, Player player) {
        try {
            Constructor<T> ctor = clazz.getDeclaredConstructor(RoleDataContext.class);
            RoleDataContext ctx = new RoleDataContext(player, null, null);
            return ctor.newInstance(ctx);
        } catch (ReflectiveOperationException e) {
            // 记录日志
            SRE.LOGGER.error("Error while create instance.", e);
            return null;
        }
    }

    /**
     * 指定类型的空占位实例（按类缓存）。只用于读默认字段，不会同步到玩家。
     */
    @Nullable
    @SuppressWarnings("unchecked")
    static <T extends RoleData> T emptyOf(Class<T> clazz) {
        if (clazz == null || clazz.isInterface()) {
            return null;
        }
        RoleData empty = EmptyCache.INSTANCES.get(clazz);
        return empty != null ? (T) empty : null;
    }

    final class EmptyCache {
        static final ClassValue<RoleData> INSTANCES = new ClassValue<>() {
            @Override
            protected RoleData computeValue(Class<?> type) {
                try {
                    Constructor<?> ctor = type.getDeclaredConstructor(RoleDataContext.class);
                    ctor.setAccessible(true);
                    Object instance = ctor.newInstance(RoleDataContext.empty());
                    if (instance instanceof RoleData roleData) {
                        return roleData;
                    }
                    return null;
                } catch (Exception e) {
                    SRE.LOGGER.error("Failed to create empty RoleData for {}", type.getName(), e);
                    return null;
                }
            }
        };
    }

    /**
     * 获取玩家当前的职业数据（任意类型）的 {@code Optional} 包装。
     *
     * @param player 目标玩家
     * @return 包含当前数据的 {@code Optional}，若不存在则返回空
     */
    @Nullable
    public static Optional<RoleData> getOptional(Player player) {
        return Optional.ofNullable(getNullable(player));
    }

    /**
     * 获取与此数据关联的玩家对象。
     *
     * @return 持有该数据的玩家
     */
    Player getPlayer();

    /**
     * 是否为 {@link #getNullable(Class, Player)} 在玩家未持有该职业时返回的空占位。
     * 占位实例字段为默认值，调用 sync 不会写回玩家。
     */
    default boolean isPlaceholder() {
        return false;
    }

    /**
     * 判断此数据是否应与指定的服务器玩家同步。
     * <p>
     * 默认实现通过比较 {@link #getPlayer()} 与传入玩家是否为同一对象来判断。
     * </p>
     *
     * @param player 待检查的服务器玩家
     * @return 若需要同步则返回 {@code true}，否则返回 {@code false}
     */
    default boolean shouldSyncWith(ServerPlayer player) {
        return this.getPlayer() == player;
    }

    /**
     * 将职业数据写入 NBT 标签，用于网络同步。
     *
     * @param tag            目标 NBT 标签
     * @param registryLookup 注册表查找提供者，用于序列化注册表对象
     */
    void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup);

    /**
     * 从 NBT 标签读取职业数据，用于网络同步。
     *
     * @param tag            源 NBT 标签
     * @param registryLookup 注册表查找提供者，用于反序列化注册表对象
     */
    void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup);

    /**
     * Optional server-side representation. This is separate from client sync so
     * private role state does not have to be exposed over the network.
     */
    default void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    /** Reads the optional server-side representation written by {@link #writeToNbt}. */
    default void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    /**
     * Writes the server-authoritative state used by an in-memory time rewind.
     *
     * <p>The default keeps existing role implementations compatible by reusing
     * their sync representation. Roles with server-only mutable state can
     * override this method without exposing that state to clients or normal
     * player saves.
     */
    default void writeToRewindNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToSyncNbt(tag, registryLookup);
        writeToNbt(tag, registryLookup);
    }

    /** Restores state previously written by {@link #writeToRewindNbt}. */
    default void readFromRewindNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromNbt(tag, registryLookup);
        readFromSyncNbt(tag, registryLookup);
    }

    /**
     * 客户端侧每 tick 调用的更新逻辑。
     * <p>
     * 默认实现为空。
     * </p>
     */
    default void clientTick() {
    }

    /**
     * 服务器侧每 tick 调用的更新逻辑。
     * <p>
     * 默认实现为空。
     * </p>
     */
    default void serverTick() {
    }

    /**
     * 当玩家被赋予此职业时触发的初始化回调。
     * <p>
     * 默认实现为空。
     * </p>
     */
    default void init() {
    }

    /**
     * 当玩家离开此职业时触发的清理回调。
     * <p>
     * 默认实现为空。
     * </p>
     */
    default void clear() {
    }

    // -------------------- NBT 辅助工具方法 --------------------

    /**
     * 从 NBT 标签中安全读取字符串值，若标签不存在或类型不匹配则返回默认值。
     *
     * @param tag          NBT 标签
     * @param name         键名
     * @param defaultValue 默认值
     * @return 读取到的字符串值或默认值
     * @see RoleComponent#getStringTagOrDefault(CompoundTag, String, String)
     */
    default String getStringTag(CompoundTag tag, String name, String defaultValue) {
        return RoleComponent.getStringTagOrDefault(tag, name, defaultValue);
    }

    /**
     * 从 NBT 标签中安全读取整数值，若标签不存在或类型不匹配则返回默认值。
     *
     * @param tag          NBT 标签
     * @param name         键名
     * @param defaultValue 默认值
     * @return 读取到的整数值或默认值
     * @see RoleComponent#getIntTagOrDefault(CompoundTag, String, int)
     */
    default int getIntTag(CompoundTag tag, String name, int defaultValue) {
        return RoleComponent.getIntTagOrDefault(tag, name, defaultValue);
    }

    /**
     * 从 NBT 标签中安全读取字节值，若标签不存在或类型不匹配则返回默认值。
     *
     * @param tag          NBT 标签
     * @param name         键名
     * @param defaultValue 默认值
     * @return 读取到的字节值或默认值
     * @see RoleComponent#getByteTagOrDefault(CompoundTag, String, Byte)
     */
    default Byte getByteTag(CompoundTag tag, String name, Byte defaultValue) {
        return RoleComponent.getByteTagOrDefault(tag, name, defaultValue);
    }

    /**
     * 从 NBT 标签中安全读取短整数值，若标签不存在或类型不匹配则返回默认值。
     *
     * @param tag          NBT 标签
     * @param name         键名
     * @param defaultValue 默认值
     * @return 读取到的短整数值或默认值
     * @see RoleComponent#getShortTagOrDefault(CompoundTag, String, short)
     */
    default short getShortTag(CompoundTag tag, String name, short defaultValue) {
        return RoleComponent.getShortTagOrDefault(tag, name, defaultValue);
    }

    /**
     * 从 NBT 标签中安全读取长整数值，若标签不存在或类型不匹配则返回默认值。
     *
     * @param tag          NBT 标签
     * @param name         键名
     * @param defaultValue 默认值
     * @return 读取到的长整数值或默认值
     * @see RoleComponent#getLongTagOrDefault(CompoundTag, String, long)
     */
    default long getLongTag(CompoundTag tag, String name, long defaultValue) {
        return RoleComponent.getLongTagOrDefault(tag, name, defaultValue);
    }

    /**
     * 从 NBT 标签中安全读取双精度浮点值，若标签不存在或类型不匹配则返回默认值。
     *
     * @param tag          NBT 标签
     * @param name         键名
     * @param defaultValue 默认值
     * @return 读取到的双精度浮点值或默认值
     * @see RoleComponent#getDoubleTagOrDefault(CompoundTag, String, double)
     */
    default double getDoubleTag(CompoundTag tag, String name, double defaultValue) {
        return RoleComponent.getDoubleTagOrDefault(tag, name, defaultValue);
    }

    /**
     * 从 NBT 标签中安全读取单精度浮点值，若标签不存在或类型不匹配则返回默认值。
     *
     * @param tag          NBT 标签
     * @param name         键名
     * @param defaultValue 默认值
     * @return 读取到的单精度浮点值或默认值
     * @see RoleComponent#getFloatTagOrDefault(CompoundTag, String, float)
     */
    default float getFloatTag(CompoundTag tag, String name, float defaultValue) {
        return RoleComponent.getFloatTagOrDefault(tag, name, defaultValue);
    }

    /**
     * 从 NBT 标签中安全读取布尔值，若标签不存在或类型不匹配则返回默认值。
     *
     * @param tag          NBT 标签
     * @param name         键名
     * @param defaultValue 默认值
     * @return 读取到的布尔值或默认值
     * @see RoleComponent#getBooleanTagOrDefault(CompoundTag, String, boolean)
     */
    default boolean getBooleanTag(CompoundTag tag, String name, boolean defaultValue) {
        return RoleComponent.getBooleanTagOrDefault(tag, name, defaultValue);
    }
}
