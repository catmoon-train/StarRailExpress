package org.agmas.noellesroles.role_data.neutral;

import java.util.UUID;

import org.agmas.noellesroles.role.bouns.roles.BeeFamilyRole;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;

public class BeeFamilyRoleData extends SimpleRoleData {

    public BeeFamilyRoleData(RoleDataContext context) {
        super(context);
    }

    public boolean beeChannel = true;
    public UUID markTarget = null;
    // 服务端存储：转换前的职业
    public SRERole beforeRole = null;

    @Override
    public void writeToSyncNbt(CompoundTag tag, Provider registryLookup) {
        tag.putBoolean("channel", beeChannel);
        if (markTarget != null)
            tag.putUUID("markTarget", markTarget);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, Provider registryLookup) {
        beeChannel = getBooleanTag(tag, "channel", false);
        if (tag.contains("markTarget")) {
            markTarget = tag.getUUID("markTarget");
        }
    }

    public void changeChannel(boolean beeChannel) {
        this.beeChannel = beeChannel;
        sync();
    }

    public void turnChannel() {
        changeChannel(!beeChannel);
        player.displayClientMessage(BeeFamilyRole.getChannelText(player), true);
    }

    public void markSuccessor(UUID target) {
        this.markTarget = target;
        sync();
    }
}
