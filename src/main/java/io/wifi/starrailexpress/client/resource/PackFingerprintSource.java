package io.wifi.starrailexpress.client.resource;

import java.io.IOException;
import java.security.MessageDigest;

/** 由包类型 Mixin 提供真实文件来源；只用于本地重载缓存失效，不作为安全验证。 */
public interface PackFingerprintSource {
    void sre$appendFingerprint(MessageDigest digest) throws IOException;
}
