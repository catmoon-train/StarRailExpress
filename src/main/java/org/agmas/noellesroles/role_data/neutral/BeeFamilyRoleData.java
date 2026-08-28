package org.agmas.noellesroles.role_data.neutral;

import org.agmas.noellesroles.role.bouns.roles.BeeFamilyRole;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;

public class BeeFamilyRoleData extends SimpleRoleData {

    public BeeFamilyRoleData(RoleDataContext context) {
        super(context);
    }

    public boolean beeChannel = true;

    @Override
    public void writeToSyncNbt(CompoundTag tag, Provider registryLookup) {
        tag.putBoolean("channel", beeChannel);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, Provider registryLookup) {
        beeChannel = getBooleanTag(tag, "channel", false);
    }

    public void changeChannel(boolean beeChannel) {
        this.beeChannel = beeChannel;
        sync();
    }

    public void turnChannel() {
        changeChannel(!beeChannel);
        player.displayClientMessage(BeeFamilyRole.getChannelText(player), true);
    }

}
