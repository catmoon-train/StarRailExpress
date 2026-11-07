package io.wifi.srepackcompat.skin;

/** 解锁范围严格限定 textures；其他 Authlib 属性仍由原签名校验处理。 */
public final class SkinTexturePolicy {
    private SkinTexturePolicy() { }

    public static boolean isTextureProperty(String name) { return "textures".equals(name); }
}
