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

package org.agmas.noellesroles.content.block_entity.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TrashCanBlockEntity extends BlockEntity {
    private boolean whitelistEnabled;
    private boolean blacklistEnabled;
    private final List<String> whitelist = new ArrayList<>();
    private final List<String> blacklist = new ArrayList<>();

    public TrashCanBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.TRASH_CAN_ENTITY, pos, state);
    }

    public boolean isWhitelistEnabled() {
        return whitelistEnabled;
    }

    public boolean isBlacklistEnabled() {
        return blacklistEnabled;
    }

    public List<String> getWhitelist() {
        return List.copyOf(whitelist);
    }

    public List<String> getBlacklist() {
        return List.copyOf(blacklist);
    }

    public void setConfig(boolean whitelistEnabled, List<String> whitelist, boolean blacklistEnabled, List<String> blacklist) {
        this.whitelistEnabled = whitelistEnabled;
        this.blacklistEnabled = blacklistEnabled;
        this.whitelist.clear();
        this.blacklist.clear();
        whitelist.stream().map(String::trim).filter(s -> ResourceLocation.tryParse(s) != null).distinct()
                .forEach(this.whitelist::add);
        blacklist.stream().map(String::trim).filter(s -> ResourceLocation.tryParse(s) != null).distinct()
                .forEach(this.blacklist::add);
        setChanged();
    }

    public boolean canDestroy(ResourceLocation itemId) {
        String id = itemId.toString();
        if (whitelistEnabled && !whitelist.contains(id)) {
            return false;
        }
        return !blacklistEnabled || !blacklist.contains(id);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("WhitelistEnabled", whitelistEnabled);
        tag.putBoolean("BlacklistEnabled", blacklistEnabled);
        tag.put("Whitelist", writeList(whitelist));
        tag.put("Blacklist", writeList(blacklist));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        whitelistEnabled = tag.getBoolean("WhitelistEnabled");
        blacklistEnabled = tag.getBoolean("BlacklistEnabled");
        readList(tag.getList("Whitelist", Tag.TAG_STRING), whitelist);
        readList(tag.getList("Blacklist", Tag.TAG_STRING), blacklist);
    }

    private static ListTag writeList(List<String> values) {
        ListTag tag = new ListTag();
        for (String value : values) {
            tag.add(StringTag.valueOf(value));
        }
        return tag;
    }

    private static void readList(ListTag tag, List<String> target) {
        target.clear();
        for (int i = 0; i < tag.size(); i++) {
            target.add(tag.getString(i));
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }
}
