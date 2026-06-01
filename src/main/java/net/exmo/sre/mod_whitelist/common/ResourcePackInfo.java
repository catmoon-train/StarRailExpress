package net.exmo.sre.mod_whitelist.common;

import java.io.Serializable;

/**
 * Represents a resource pack's information including its ID and SHA256 hash
 */
public record ResourcePackInfo(String packId, String sha256) implements Serializable {
	
	public ResourcePackInfo {
		if (packId == null || packId.isEmpty()) {
			throw new IllegalArgumentException("packId cannot be null or empty");
		}
		if (sha256 == null || sha256.isEmpty()) {
			throw new IllegalArgumentException("sha256 cannot be null or empty");
		}
	}

	@Override
	public String toString() {
		return "ResourcePackInfo{" +
				"packId='" + packId + '\'' +
				", sha256='" + sha256 + '\'' +
				'}';
	}
}