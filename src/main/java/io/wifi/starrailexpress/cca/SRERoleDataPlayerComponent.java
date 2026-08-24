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

package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 通用技能组件
 *
 * 用于管理玩家的技能冷却时间和使用次数
 * 该组件会自动在客户端和服务端之间同步
 *
 * 功能：
 * - 冷却时间管理（自动递减）
 * - 技能使用次数限制
 * - 自动同步到客户端（用于 HUD 显示）
 */
public class SRERoleDataPlayerComponent
        implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public RoleData roleData = null;
    public SRERole playerRole = null;
    private boolean initSync = false;

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        if (roleData != null) {
            return roleData.shouldSyncWith(p);
        }
        return player == p;
    }

    /**
     * 构造函数
     */
    public SRERoleDataPlayerComponent(Player player) {
        this.player = player;
    }

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<SRERoleDataPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("role_data"),
            SRERoleDataPlayerComponent.class);

    // 持有该组件的玩家
    private final Player player;

    @Override
    public void clientTick() {
        if (SREGameWorldComponent.getInstance(player).isRunning()) {
            if (roleData != null)
                roleData.clientTick();
        }
    }

    @Override
    public void serverTick() {
        if (SREGameWorldComponent.getInstance(player).isRunning()) {
            if (roleData != null)
                roleData.serverTick();
        }
    }

    @Override
    public void init() {
        serverInit();
    }

    public void serverInit() {
        final var cca = SREGameWorldComponent.getInstance(player);
        playerRole = cca.getRole(player);
        if (playerRole == null) {
            clear();
            return;
        }
        final var roleDataFunc = playerRole.getRoleDataFunc();
        final RoleDataContext ctx = new RoleDataContext(player, playerRole, () -> {
            sync();
        }, (p) -> {
            syncTo(p);
        });
        if (roleDataFunc != null) {
            roleData = roleDataFunc.apply(ctx);
        }
        if (roleData != null) {
            initSync = true;
            sync();
            initSync = false;
            roleData.init();
        }
    }

    public void clientInit() {
        final var cca = SREGameWorldComponent.getInstance(player);
        playerRole = cca.getRole(player);
        if (playerRole == null) {
            clear();
            return;
        }
        final var roleDataFunc = playerRole.getRoleDataFunc();
        final RoleDataContext ctx = new RoleDataContext(player, playerRole, null, null);
        if (roleDataFunc != null) {
            roleData = roleDataFunc.apply(ctx);
            roleData.initOnClient();
        }
    }

    @Override
    public void clear() {
        if (roleData != null) {
            roleData.clear();
        }
        playerRole = null;
        roleData = null;
        initSync = false;
        sync();
    }

    /**
     * 同步到指定玩家客户端
     */
    public void syncTo(ServerPlayer p) {
        initSync = false;
        KEY.syncWith(p, player.asComponentProvider());
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        KEY.sync(this.player);
        initSync = false;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, Provider registryLookup) {
        if (initSync) {
            tag.putBoolean("__init__", true);
            return;
        } else if (roleData == null) {
            tag.putBoolean("__clear__", true);
            return;
        }
        if (roleData != null) {
            roleData.writeToSyncNbt(tag, registryLookup);
        }
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, Provider registryLookup) {

        if (tag.contains("__init__")) {
            clientInit();
            return;
        } else if (tag.contains("__clear__")) {
            clear();
            return;
        }
        if (roleData == null) {
            clientInit();
        }

        if (roleData != null) {
            roleData.readFromSyncNbt(tag, registryLookup);
        }
    }

    public void onRemoveRole() {
        this.clear();
    }

}
