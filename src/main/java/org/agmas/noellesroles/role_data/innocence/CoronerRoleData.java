package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.scanner.IdentityScannerLedger;
import org.agmas.noellesroles.game.scanner.IdentityScannerService;

/** 同步本局首次成为验尸官后的丢弃期限；单局账本保留期限，防止重连或转职重置。 */
public final class CoronerRoleData extends SimpleRoleData {
    private long scannerDropReadyAt = -1;

    public CoronerRoleData(RoleDataContext context) { super(context); }

    @Override public boolean shouldSyncWith(ServerPlayer viewer) { return viewer == player; }

    @Override public void serverTick() {
        if (!(player instanceof ServerPlayer owner)) return;
        long readyAt = IdentityScannerService.coronerDropReadyAt(owner);
        if (readyAt >= 0 && readyAt != scannerDropReadyAt) {
            scannerDropReadyAt = readyAt;
            // 只在首次登记或新局期限改变时同步，不逐tick发冷却包。
            sync();
        }
    }

    /** 服务端查询权威账本，客户端使用同一期限和已同步的局内时钟预测丢弃按键。 */
    public int scannerDropRemaining() {
        long readyAt = player instanceof ServerPlayer owner
                ? IdentityScannerService.coronerDropReadyAt(owner) : scannerDropReadyAt;
        return readyAt < 0 ? IdentityScannerLedger.CORONER_DROP_LOCK_TICKS
                : IdentityScannerLedger.remaining(readyAt, GameUtils.getTicksFromGameStart(player.level()));
    }

    @Override public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putLong("scannerDropReadyAt", scannerDropReadyAt);
    }

    @Override public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        scannerDropReadyAt = tag.contains("scannerDropReadyAt") ? tag.getLong("scannerDropReadyAt") : -1;
    }
}
