package net.exmo.sre.mod_whitelist.common.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

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
	 * Computes a SHA256 hash for a file or a directory. Directories are hashed
	 * deterministically by relative path and file content.
	 *
	 * @param path file or directory path
	 * @return the hex-encoded SHA256 hash
	 */
	public static String hash(Path path) throws IOException {
		if (path == null) {
			throw new IllegalArgumentException("Path cannot be null");
		}
		MessageDigest digest = newDigest();
		updatePath(digest, path.toAbsolutePath().normalize(), "");
		return bytesToHex(digest.digest());
	}

	/**
	 * Computes a deterministic SHA256 hash for multiple roots.
	 */
	public static String hashPaths(Collection<Path> paths) throws IOException {
		if (paths == null) {
			throw new IllegalArgumentException("Paths cannot be null");
		}
		MessageDigest digest = newDigest();
		List<Path> sortedPaths = paths.stream()
				.map(path -> path.toAbsolutePath().normalize())
				.sorted(Comparator.comparing(Path::toString))
				.toList();
		for (Path path : sortedPaths) {
			updateBytes(digest, "root:" + path.getFileName());
			updatePath(digest, path, "");
		}
		return bytesToHex(digest.digest());
	}

	private static MessageDigest newDigest() {
		try {
			return MessageDigest.getInstance(ALGORITHM);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 algorithm not available", e);
		}
	}

	private static void updatePath(MessageDigest digest, Path path, String prefix) throws IOException {
		if (Files.isDirectory(path)) {
			try (var stream = Files.walk(path)) {
				List<Path> files = stream
						.filter(Files::isRegularFile)
						.sorted(Comparator.comparing(p -> path.relativize(p).toString()))
						.toList();
				for (Path file : files) {
					String relative = path.relativize(file).toString().replace('\\', '/');
					updateBytes(digest, prefix + relative);
					updateFileBytes(digest, file);
				}
			}
			return;
		}
		updateBytes(digest, prefix + path.getFileName());
		updateFileBytes(digest, path);
	}

	private static void updateFileBytes(MessageDigest digest, Path file) throws IOException {
		try (InputStream inputStream = Files.newInputStream(file)) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = inputStream.read(buffer)) != -1) {
				digest.update(buffer, 0, read);
			}
		}
	}

	private static void updateBytes(MessageDigest digest, String value) {
		digest.update(value.getBytes(StandardCharsets.UTF_8));
		digest.update((byte) 0);
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
