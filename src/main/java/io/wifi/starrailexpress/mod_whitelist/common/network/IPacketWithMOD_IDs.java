package io.wifi.starrailexpress.mod_whitelist.common.network;


import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface IPacketWithMOD_IDs {
	@Nullable
	List<String> getMOD_IDs();

	@SuppressWarnings("unused")
	void setMOD_IDs(@Nullable List<String> MOD_IDs);
}
