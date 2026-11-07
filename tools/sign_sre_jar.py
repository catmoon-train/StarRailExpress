#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SRE JAR 密钥签名工具（配合 ENABLE_JAR_KEY_AUTH 使用）

在构建产物 jar 中嵌入一份随机认证密钥（sre_auth_key.txt）。服务端与所有客户端
必须运行同一份签名后的 jar：入服时服务端下发 nonce，双方各自用嵌入密钥对
「自身 jar 摘要 | nonce | 版本」做 HMAC-SHA256 比对，任何对 jar 的修改都会导致
摘要不一致而被拒绝进入。

使用方法:
    python tools/sign_sre_jar.py build/libs/star_rail_express-x.y.z.jar
    python tools/sign_sre_jar.py <jar> --key tools/sre_auth_key.secret

- 密钥文件不存在时会自动生成（64 位十六进制随机串）并保存，请勿提交到版本库！
- 重复签名会先移除旧的 sre_auth_key.txt 再写入。
- 签名后把同一个 jar 分发给服务端与全部客户端即可。
"""

import argparse
import os
import secrets
import shutil
import sys
import tempfile
import zipfile

KEY_ENTRY_NAME = "sre_auth_key.txt"
DEFAULT_KEY_FILE = os.path.join(os.path.dirname(__file__), "sre_auth_key.secret")


def load_or_create_key(key_file: str) -> str:
    if os.path.exists(key_file):
        with open(key_file, "r", encoding="utf-8") as f:
            key = f.read().strip()
        if key:
            print(f"使用现有密钥文件: {key_file}")
            return key
    key = secrets.token_hex(32)
    with open(key_file, "w", encoding="utf-8") as f:
        f.write(key + "\n")
    print(f"已生成新密钥并保存到: {key_file}")
    print("!! 请妥善保管该文件，且不要提交到版本库 !!")
    return key


def sign_jar(jar_path: str, key: str) -> None:
    if not os.path.isfile(jar_path):
        print(f"错误: 找不到 jar 文件 {jar_path}")
        sys.exit(1)

    tmp_fd, tmp_path = tempfile.mkstemp(suffix=".jar")
    os.close(tmp_fd)
    try:
        with zipfile.ZipFile(jar_path, "r") as src, \
                zipfile.ZipFile(tmp_path, "w", zipfile.ZIP_DEFLATED) as dst:
            for item in src.infolist():
                if item.filename == KEY_ENTRY_NAME:
                    continue  # 移除旧密钥
                dst.writestr(item, src.read(item.filename))
            # 固定时间戳，保证重复签名产物可复现
            info = zipfile.ZipInfo(KEY_ENTRY_NAME, date_time=(2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            dst.writestr(info, key + "\n")
        shutil.move(tmp_path, jar_path)
    except BaseException:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)
        raise

    print(f"签名完成: {jar_path}")
    print("请将该 jar 同时部署到服务端与所有客户端，并在服务端配置中开启:")
    print('  "ENABLE_JAR_KEY_AUTH": true   (config/starrailexpress-config.json)')


def main() -> None:
    parser = argparse.ArgumentParser(description="为 StarRailExpress jar 嵌入认证密钥")
    parser.add_argument("jar", help="要签名的 jar 文件路径")
    parser.add_argument("--key", default=DEFAULT_KEY_FILE,
                        help=f"密钥文件路径（默认 {DEFAULT_KEY_FILE}，不存在则自动生成）")
    args = parser.parse_args()

    key = load_or_create_key(args.key)
    sign_jar(args.jar, key)


if __name__ == "__main__":
    main()
