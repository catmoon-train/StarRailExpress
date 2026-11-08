package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AmonRewindTest {
    @Test
    void preservesAuthoritativeStateInsteadOfOnlyHudCounts() {
        UUID seed = UUID.randomUUID();
        UUID host = UUID.randomUUID();
        UUID disguise = UUID.randomUUID();
        CompoundTag state = new CompoundTag();
        CompoundTag incubation = new CompoundTag();
        incubation.putInt(seed.toString(), 734);
        state.put("Incubation", incubation);
        ListTag hosts = new ListTag();
        hosts.add(StringTag.valueOf(host.toString()));
        state.put("MaturedHosts", hosts);
        state.putUUID("Possess", host);
        state.putUUID("Disguise", disguise);
        state.putInt("PossessTicks", 512);
        state.putDouble("HomeX", 12.5);
        state.putDouble("HomeY", 64);
        state.putDouble("HomeZ", -38.25);
        state.putFloat("HomeYRot", 123);
        state.putDouble("PrePossessionScale", 1.3);
        state.putInt("UsurpCount", 3);
        state.putBoolean("HasUsurped", true);
        state.putInt("SeedCap", 6);
        state.putInt("ControlCount", 4);
        state.putBoolean("Finale", true);
        state.putInt("FinaleTicks", 987);
        state.putInt("Reserve", 2);

        AmonRoleData data = new AmonRoleData(new RoleDataContext(null, null, null, null));
        data.readFromRewindNbt(state, null);
        CompoundTag restored = new CompoundTag();
        data.writeToRewindNbt(restored, null);

        for (String key : state.getAllKeys()) {
            assertEquals(state.get(key), restored.get(key), key);
        }
        assertEquals(1, restored.getInt("Seeds"));
        assertEquals(1, restored.getInt("Matured"));
        assertEquals(host, data.possessTarget);
        assertEquals(987, data.finaleTicks);
        assertEquals(2, data.reserveLives);
        assertFalse(restored.contains("refugeeSuspended"));
    }

    @Test
    void restoringEmptyNodeClearsPreviousHostsAndPossession() {
        AmonRoleData data = new AmonRoleData(new RoleDataContext(null, null, null, null));
        CompoundTag oldState = new CompoundTag();
        CompoundTag incubation = new CompoundTag();
        incubation.putInt(UUID.randomUUID().toString(), 100);
        oldState.put("Incubation", incubation);
        oldState.putUUID("Possess", UUID.randomUUID());
        oldState.putUUID("Disguise", UUID.randomUUID());
        oldState.putDouble("HomeX", 10);
        data.readFromRewindNbt(oldState, null);
        data.readFromRewindNbt(new CompoundTag(), null);
        CompoundTag restored = new CompoundTag();
        data.writeToRewindNbt(restored, null);

        assertEquals(0, restored.getInt("Seeds"));
        assertEquals(0, restored.getInt("Matured"));
        assertFalse(restored.contains("Possess"));
        assertFalse(restored.contains("Disguise"));
        assertFalse(restored.contains("HomeX"));
        assertTrue(Double.isNaN(restored.getDouble("PrePossessionScale")));
        assertNull(data.possessTarget);
    }
}
