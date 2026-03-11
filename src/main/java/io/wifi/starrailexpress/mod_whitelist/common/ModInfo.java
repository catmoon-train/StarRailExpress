package io.wifi.starrailexpress.mod_whitelist.common;

import java.io.Serializable;

/**
 * Represents a mod's information including its ID and SHA256 hash
 */
public record ModInfo(String modId, String sha256) implements Serializable {
	
	public ModInfo {
		if (modId == null || modId.isEmpty()) {
			throw new IllegalArgumentException("modId cannot be null or empty");
		}
		if (sha256 == null || sha256.isEmpty()) {
			throw new IllegalArgumentException("sha256 cannot be null or empty");
		}
	}

	@Override
	public String toString() {
		return "ModInfo{" +
				"modId='" + modId + '\'' +
				", sha256='" + sha256 + '\'' +
				'}';
	}
}
