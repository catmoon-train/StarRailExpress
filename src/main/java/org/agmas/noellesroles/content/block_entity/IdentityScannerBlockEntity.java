package org.agmas.noellesroles.content.block_entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.game.scanner.IdentityScannerLedger;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.packet.IdentityScannerStateS2CPacket.Entry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 机器独立冷却和本机公开历史；个人去重由单局账本统一维护。 */
public final class IdentityScannerBlockEntity extends BlockEntity {
    private UUID roundId;
    private long readyAt;
    private final List<Entry> history = new ArrayList<>();
    public IdentityScannerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.IDENTITY_SCANNER_BLOCK_ENTITY, pos, state);
    }
    public void ensureRound(UUID currentRound) {
        if (!currentRound.equals(roundId)) {
            roundId = currentRound;
            readyAt = 0;
            history.clear();
            setChanged();
        }
    }
    public int remaining(long now) { return IdentityScannerLedger.remaining(readyAt, now); }
    public List<Entry> history() { return List.copyOf(history); }
    public void scanned(IdentityScannerLedger.Identity identity, long now) {
        readyAt = now + 30 * 20;
        if (history.size() == 256) history.removeFirst();
        history.add(new Entry(identity.name(), identity.roleId(), identity.color()));
        setChanged();
    }
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (roundId != null) tag.putUUID("ScannerRound", roundId);
        tag.putLong("ScannerReadyAt", readyAt);
        ListTag entries = new ListTag();
        for (Entry entry : history) {
            CompoundTag row = new CompoundTag();
            row.putString("Name", entry.name()); row.putString("Role", entry.roleId()); row.putInt("Color", entry.color());
            entries.add(row);
        }
        tag.put("ScannerHistory", entries);
    }
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        roundId = tag.hasUUID("ScannerRound") ? tag.getUUID("ScannerRound") : null;
        readyAt = Math.max(0, tag.getLong("ScannerReadyAt"));
        history.clear();
        ListTag entries = tag.getList("ScannerHistory", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(256, entries.size()); i++) {
            CompoundTag row = entries.getCompound(i);
            String name = row.getString("Name"), role = row.getString("Role");
            if (name.length() <= 64 && role.length() <= 256) history.add(new Entry(name, role, row.getInt("Color")));
        }
    }
}
