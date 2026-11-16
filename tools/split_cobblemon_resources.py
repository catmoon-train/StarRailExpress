"""拆分 Cobblemon 1.8.4 客户端资源；准备与替换分离，保留可验证原件。"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import tempfile
import zipfile

PACK_FORMAT = 34
FALLBACK_PREFIXES = (
    "assets/cobblemon/shaders/",
    "assets/minecraft/shaders/",
    "assets/cobblemon/atlases/",
    "assets/minecraft/atlases/",
    "assets/cobblemon/bedrock/berries/",
    "assets/cobblemon/bedrock/misc/",
)
FALLBACK_FILES = frozenset((
    "assets/cobblemon/icon_cobblemon.png",
    "assets/cobblemon/bedrock/generic/animations/substitute.animation.json",
    "assets/cobblemon/bedrock/generic/models/substitute.geo.json",
    "assets/cobblemon/bedrock/generic/posers/substitute.json",
    "assets/cobblemon/bedrock/generic/variations/0_substitute_base.json",
    "assets/cobblemon/textures/pokemon/substitute.png",
    "assets/cobblemon/textures/pokemon/substitute_shiny.png",
    "assets/cobblemon/textures/pokemon/substitute_alpha.png",
))


def sha256(path: Path) -> str:
    with path.open("rb") as file:
        return hashlib.file_digest(file, "sha256").hexdigest()


def language(name: str) -> bool:
    parts = PurePosixPath(name).parts
    return len(parts) >= 4 and parts[0] == "assets" and parts[2] == "lang"


def fallback(name: str) -> bool:
    return name in FALLBACK_FILES or name.startswith(FALLBACK_PREFIXES)


def retained(name: str) -> bool:
    if name.startswith("assets/"):
        return language(name) or fallback(name)
    if name.startswith("resourcepacks/"):
        parts = name.split("/", 2)
        return len(parts) == 3 and (parts[2] == "pack.mcmeta" or language(parts[2]))
    return True


def write_entry(target: zipfile.ZipFile, info: zipfile.ZipInfo, content: bytes) -> None:
    info = copy.copy(info)
    target.writestr(info, content, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def metadata() -> bytes:
    return json.dumps({"pack": {"pack_format": PACK_FORMAT,
        "description": "Cobblemon 1.8.4 完整客户端资源（语言由模组提供）"}},
        ensure_ascii=False, indent=2).encode("utf-8")


def validate(source: Path, stripped: Path, main_pack: Path, optional: dict[str, Path]) -> dict:
    counts = {"preservedFiles": 0, "languageFiles": 0, "fallbackFiles": 0,
              "fallbackBytes": 0, "externalAssetFiles": 0, "externalAssetBytes": 0}
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(stripped) as slim, zipfile.ZipFile(main_pack) as pack:
        expected_slim, expected_pack = set(), {"pack.mcmeta", "LICENSE"}
        slim_names, pack_names = set(slim.namelist()), set(pack.namelist())
        for info in original.infolist():
            name = info.filename
            if info.is_dir():
                continue
            data = original.read(info)
            if retained(name):
                expected_slim.add(name)
                if slim.read(name) != data:
                    raise ValueError(f"保留文件发生变化: {name}")
                counts["preservedFiles"] += 1
                counts["languageFiles"] += int(language(name))
                if name.startswith("assets/") and not language(name):
                    counts["fallbackFiles"] += 1
                    counts["fallbackBytes"] += len(data)
            elif name in slim_names:
                raise ValueError(f"精简模组残留外置资源: {name}")
            if name.startswith("assets/") and not language(name):
                expected_pack.add(name)
                if pack.read(name) != data:
                    raise ValueError(f"材质包资源发生变化: {name}")
                counts["externalAssetFiles"] += 1
                counts["externalAssetBytes"] += len(data)
        if slim_names != expected_slim or pack_names != expected_pack:
            raise ValueError("精简模组或材质包出现额外/缺失条目")
        if any(language(name) for name in pack_names):
            raise ValueError("主材质包不应包含语言文件")
        if json.loads(pack.read("pack.mcmeta"))["pack"]["pack_format"] != PACK_FORMAT:
            raise ValueError("材质包格式不匹配")
        for key, path in optional.items():
            prefix = f"resourcepacks/{key}/"
            with zipfile.ZipFile(path) as output:
                expected = {i.filename[len(prefix):] for i in original.infolist()
                            if not i.is_dir() and i.filename.startswith(prefix)} | {"LICENSE"}
                if set(output.namelist()) != expected:
                    raise ValueError(f"可选材质包条目错误: {key}")
                for name in expected - {"LICENSE"}:
                    if output.read(name) != original.read(prefix + name):
                        raise ValueError(f"可选材质包资源发生变化: {key}/{name}")
        for archive in (slim, pack):
            if archive.testzip() is not None:
                raise ValueError("ZIP CRC 校验失败")
    return counts


def prepare(source: Path, destination: Path, expected_sha256: str) -> Path:
    source, destination = source.resolve(), destination.resolve()
    if sha256(source) != expected_sha256:
        raise ValueError("原 JAR 哈希与预期不符，拒绝操作")
    if destination.exists():
        raise FileExistsError(f"输出目录已存在，拒绝覆盖: {destination}")
    with zipfile.ZipFile(source) as original:
        names = original.namelist()
        if len(names) != len(set(names)):
            raise ValueError("原 JAR 包含重复 ZIP 条目")
        for name in names:
            if PurePosixPath(name).is_absolute() or ".." in PurePosixPath(name).parts or "\\" in name:
                raise ValueError(f"不安全的 ZIP 路径: {name}")
        mod = json.loads(original.read("fabric.mod.json"))
        if mod.get("id") != "cobblemon" or mod.get("version") != "1.8.4+1.21.1":
            raise ValueError("此兜底清单只核对过 Cobblemon Fabric 1.8.4+1.21.1")
        missing = FALLBACK_FILES - set(names)
        if missing:
            raise ValueError(f"必要兜底资源缺失: {sorted(missing)}")
        if any(n.startswith("META-INF/") and n.upper().endswith((".SF", ".RSA", ".DSA", ".EC")) for n in names):
            raise ValueError("原 JAR 已签名，不能在不重新签名的情况下拆分")
        destination.mkdir(parents=True)
        stripped = destination / (source.stem + "-slim.jar")
        main_pack = destination / "Cobblemon-1.8.4-resources.zip"
        optional_names = sorted({n.split("/")[1] for n in names
                                 if n.startswith("resourcepacks/") and len(n.split("/")) > 2})
        optional = {key: destination / f"Cobblemon-1.8.4-optional-{key}.zip" for key in optional_names}
        with zipfile.ZipFile(stripped, "x") as slim, zipfile.ZipFile(main_pack, "x") as pack:
            pack.writestr("pack.mcmeta", metadata())
            pack.writestr("LICENSE", original.read("LICENSE"))
            for info in original.infolist():
                if info.is_dir():
                    continue
                if retained(info.filename):
                    write_entry(slim, info, original.read(info))
                if info.filename.startswith("assets/") and not language(info.filename):
                    write_entry(pack, info, original.read(info))
        for key, path in optional.items():
            prefix = f"resourcepacks/{key}/"
            with zipfile.ZipFile(path, "x") as pack:
                pack.writestr("LICENSE", original.read("LICENSE"))
                for info in original.infolist():
                    if not info.is_dir() and info.filename.startswith(prefix):
                        renamed = copy.copy(info)
                        renamed.filename = info.filename[len(prefix):]
                        write_entry(pack, renamed, original.read(info))
    counts = validate(source, stripped, main_pack, optional)
    if sha256(source) != expected_sha256:
        raise ValueError("准备期间原 JAR 被其他程序修改，拒绝生成提交清单")
    outputs = [stripped, main_pack, *optional.values()]
    manifest = {"source": str(source), "originalSha256": expected_sha256,
                "stripped": stripped.name, "mainPack": main_pack.name,
                "optionalPacks": {key: p.name for key, p in optional.items()},
                "outputs": {p.name: {"sha256": sha256(p), "bytes": p.stat().st_size} for p in outputs},
                "counts": counts,
                "retainedFallbacks": sorted(n for n in names if not n.endswith("/") and n.startswith("assets/")
                                            and not language(n) and fallback(n))}
    path = destination / "split-manifest.json"
    path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return path


def apply(manifest_path: Path) -> Path:
    manifest_path = manifest_path.resolve()
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    source = Path(manifest["source"])
    directory = manifest_path.parent
    for name, info in manifest["outputs"].items():
        if Path(name).name != name or sha256(directory / name) != info["sha256"]:
            raise ValueError(f"生成产物校验失败: {name}")
    if sha256(source) != manifest["originalSha256"]:
        raise ValueError("原 JAR 已变化或已经替换，拒绝覆盖")
    backup = directory / (source.name + ".original.bak")
    with backup.open("xb") as output, source.open("rb") as input_file:
        shutil.copyfileobj(input_file, output)
    if sha256(backup) != manifest["originalSha256"]:
        raise ValueError("原件备份校验失败，未替换")
    descriptor, temporary = tempfile.mkstemp(prefix=".cobblemon-split-", suffix=".tmp", dir=source.parent)
    try:
        with os.fdopen(descriptor, "wb") as output, (directory / manifest["stripped"]).open("rb") as input_file:
            shutil.copyfileobj(input_file, output)
            output.flush()
            os.fsync(output.fileno())
        if sha256(Path(temporary)) != manifest["outputs"][manifest["stripped"]]["sha256"]:
            raise ValueError("替换临时文件校验失败")
        if sha256(source) != manifest["originalSha256"]:
            raise ValueError("备份期间原 JAR 被修改，拒绝替换")
        os.replace(temporary, source)
    finally:
        Path(temporary).unlink(missing_ok=True)
    return backup


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    create = commands.add_parser("prepare")
    create.add_argument("source", type=Path)
    create.add_argument("destination", type=Path)
    create.add_argument("--expected-sha256", required=True)
    install = commands.add_parser("apply")
    install.add_argument("manifest", type=Path)
    args = parser.parse_args()
    if args.command == "prepare":
        print(prepare(args.source, args.destination, args.expected_sha256))
    else:
        print("已替换原 JAR，备份：", apply(args.manifest))


if __name__ == "__main__":
    main()
