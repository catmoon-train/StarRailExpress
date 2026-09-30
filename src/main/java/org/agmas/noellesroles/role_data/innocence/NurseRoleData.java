package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.roles.innocence.nurse.NurseRole;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 护士职业数据。
 *
 * <p>记录「可透视的尸体」：因虚拟血量归零死亡的玩家（不限具体武器死因），
 * 其尸体自生成起 {@link NurseRole#BODY_GLOW_DURATION_TICKS} tick 内可被护士透视。
 * 只同步给护士本人；客户端 {@code NurseBodyGlowMixin} 据此渲染发光轮廓。
 */
public class NurseRoleData extends SimpleRoleData {
    /** 尸体实体 UUID（字符串） -> 失效游戏时间。 */
    public final Map<String, Long> glowBodies = new HashMap<>();

    public NurseRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    @Override
    public void serverTick() {
        if (glowBodies.isEmpty() || player == null) {
            return;
        }
        long gameTime = player.level().getGameTime();
        // 过期的条目移除；有变化才同步
        if (glowBodies.values().removeIf(expiry -> expiry <= gameTime)) {
            sync();
        }
    }

    /** 服务端：登记一具可透视尸体（到达失效时间自动移除）。 */
    public void addGlowBody(UUID bodyUuid, long expiryGameTime) {
        glowBodies.put(bodyUuid.toString(), expiryGameTime);
        sync();
    }

    /** 该尸体当前是否处于可透视状态（双端通用，客户端用同步数据判断）。 */
    public boolean isBodyGlowing(UUID bodyUuid, long gameTime) {
        Long expiry = glowBodies.get(bodyUuid.toString());
        return expiry != null && expiry > gameTime;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Long> entry : glowBodies.entrySet()) {
            CompoundTag body = new CompoundTag();
            body.putString("id", entry.getKey());
            body.putLong("expiry", entry.getValue());
            list.add(body);
        }
        tag.put("glowBodies", list);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        glowBodies.clear();
        ListTag list = tag.getList("glowBodies", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag body = list.getCompound(i);
            glowBodies.put(body.getString("id"), body.getLong("expiry"));
        }
    }
}
