package io.wifi.starrailexpress.mod_whitelist.common.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for computing SHA256 hashes
 */
public final class SHA256Utils {
	private static final String ALGORITHM = "SHA-256";
	private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

	private SHA256Utils() {
		throw new AssertionError("No instances of utility class allowed");
	}

	/**
	 * Computes the SHA256 hash of a string
	 *
	 * @param input the string to hash
	 * @return the hex-encoded SHA256 hash
	 */
	public static String hash(String input) {
		if (input == null) {
			throw new IllegalArgumentException("Input cannot be null");
		}
		return hash(input.getBytes());
	}

	/**
	 * Computes the SHA256 hash of a byte array
	 *
	 * @param input the bytes to hash
	 * @return the hex-encoded SHA256 hash
	 */
	public static String hash(byte[] input) {
		if (input == null) {
			throw new IllegalArgumentException("Input cannot be null");
		}
		try {
			MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
			byte[] encodedhash = digest.digest(input);
			return bytesToHex(encodedhash);
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 is always available in Java
			throw new RuntimeException("SHA-256 algorithm not available", e);
		}
	}

	/**
	 * Converts a byte array to a hex string
	 *
	 * @param bytes the byte array
	 * @return the hex-encoded string
	 */
	private static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			int v = bytes[i] & 0xFF;
			hexChars[i * 2] = HEX_ARRAY[v >>> 4];
			hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars);
	}
}
