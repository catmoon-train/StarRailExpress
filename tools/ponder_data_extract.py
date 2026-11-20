"""从源码与语言文件提取职业思索所需的事实数据（只读分析）。

用法：python tools/ponder_data_extract.py
输出：tools/ponder_role_data.json
"""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LANG = ROOT / "src/main/resources/assets"
JAVA = ROOT / "src/main/java"
OUT = Path(__file__).resolve().parent / "ponder_role_data.json"

# 其它模式职业只按职业定义里的 setOtherModeRole(true) 判定，包括基类中统一设置的情形。
OTHER_MODE_BASE_CLASSES = ("GooseDuckRole",)


def load_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def language_data():
    intros, names = {}, {}
    for locale in ("zh_cn", "en_us", "zh_tw"):
        intros[locale] = load_json(LANG / "role_modifier_intro/lang" / (locale + ".json"))
        names[locale] = load_json(LANG / "role_name_goals/lang" / (locale + ".json"))
    return intros, names


ID_PATTERN = re.compile(
    r'\b[A-Za-z_][A-Za-z0-9_]*\.(?:id|wifiId|xiaoheihandId)\(\s*["\']([^"\']+)["\']\s*\)'
    r'|ResourceLocation\.(?:fromNamespaceAndPath|parse)\(\s*["\'][^"\']+["\']\s*,\s*["\']([^"\']+)["\']\s*\)'
)
CONST_PATTERN = re.compile(
    r'\b([A-Z][A-Z0-9_]*_ID)\s*=\s*[^\n;]{0,160}?\(\s*["\']([^"\']+)["\']\s*\)'
)
COLOR_PATTERN = re.compile(r'new\s+Color\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)')
OTHER_MODE_PATTERN = re.compile(r'setOtherModeRole\(\s*true\s*\)')


def build_index():
    """扫描全部 Java 一次，建立 id -> 注册片段索引，以及其它模式 id 集合。"""
    index = {}
    constants = {}
    const_to_id = {}
    other = set()
    for path in JAVA.rglob("*.java"):
        text = path.read_text(encoding="utf-8", errors="ignore")
        rel = str(path.relative_to(JAVA)).replace("\\", "/")
        for match in ID_PATTERN.finditer(text):
            key = match.group(1) or match.group(2)
            if key and key not in index:
                index[key] = (rel, text[max(0, match.start() - 240):match.start() + 3200])
        for match in CONST_PATTERN.finditer(text):
            constants.setdefault(match.group(2), set()).add(match.group(1))
            const_to_id[match.group(1)] = match.group(2)
        for match in OTHER_MODE_PATTERN.finditer(text):
            begin = text.rfind("registerRole(", max(0, match.start() - 1800), match.start())
            if begin < 0:
                begin = max(0, match.start() - 500)
            chunk = text[begin:match.start()]
            ids = [m.group(1) or m.group(2) for m in ID_PATTERN.finditer(chunk)]
            # 常量形式：把 <ROLE>_ID 还原成真实 path。
            for const_match in re.finditer(r'\b([A-Z][A-Z0-9_]*_ID)\b', chunk):
                if const_match.group(1) in const_to_id:
                    ids.append(const_to_id[const_match.group(1)])
            if ids:
                other.add(ids[-1])
            if "EchoRoles" in rel:
                other.update(("echo_carrier", "echo_interceptor", "echo_broker"))
        for base in OTHER_MODE_BASE_CLASSES:
            for match in re.finditer(r'new\s+' + base + r'\s*\(', text):
                chunk = text[match.start():match.start() + 400]
                for const_match in re.finditer(r'\b([A-Z][A-Z0-9_]*_ID)\b', chunk):
                    if const_match.group(1) in const_to_id:
                        other.add(const_to_id[const_match.group(1)])
    return index, constants, other


def normalize_int(rgb):
    value = (0xFF000000 | rgb) & 0xFFFFFFFF
    return value - 0x100000000 if value >= 0x80000000 else value


def registration(index, constants, intro_id):
    result = {"role_id": "", "color": None, "instinct_on": [], "instinct_seen": [], "source": ""}
    hit = index.get(intro_id)
    if hit is None:
        return result
    path, chunk = hit
    result["source"] = path
    result["role_id"] = "noellesroles:" + intro_id
    if "GooseDuck" in path or "gooseduck" in path:
        result["role_id"] = "gooseduck:" + intro_id
    # 注册语句通常使用 <ROLE>_ID 常量，颜色写在常量的后面；用常量名定位更准确。
    for constant in constants.get(intro_id, ()):
        for occurrence in re.finditer(r'\b' + re.escape(constant) + r'\b', chunk):
            candidate = chunk[occurrence.start():occurrence.start() + 420]
            color = COLOR_PATTERN.search(candidate)
            if color:
                chunk = chunk[occurrence.start() - 120:occurrence.start() + 2600]
                break
    color = COLOR_PATTERN.search(chunk)
    if color:
        r, g, b = (int(x) for x in color.groups())
        result["color"] = normalize_int((r << 16) | (g << 8) | b)
    for kind, field in (("setInstinctType", "instinct_on"), ("setBeSeenInstinctType", "instinct_seen")):
        for call in re.finditer(re.escape(kind) + r'\(\s*([^;]{0,180}?)\)\s*\)', chunk):
            result[field].append(re.sub(r"\s+", " ", call.group(1))[:130])
    return result


def main():
    intros, names = language_data()
    index, constants, other_ids = build_index()
    base = intros["zh_cn"]
    keys = [k[len("info.screen.roleid."):] for k in base
            if k.startswith("info.screen.roleid.") and not k.endswith(".simple")]

    roles = []
    for key in keys:
        text_zh = base.get("info.screen.roleid." + key, "")
        lines = text_zh.split("\n")
        other = key in other_ids
        roles.append({
            "intro_id": key,
            "name": {locale: names[locale].get("announcement.star.role." + key, "")
                     for locale in ("zh_cn", "en_us", "zh_tw")},
            "intro": {locale: intros[locale].get("info.screen.roleid." + key, "").split("\n")
                      for locale in ("zh_cn", "en_us", "zh_tw")},
            "faction": lines[0] if lines else "",
            "mode": "other" if other else "normal",
            "registration": registration(index, constants, key),
        })

    data = {
        "roles": roles,
        "notes": {
            "total": len(roles),
            "normal": sum(r["mode"] == "normal" for r in roles),
            "other": sum(r["mode"] == "other" for r in roles),
            "color_found": sum(r["registration"]["color"] is not None for r in roles),
        },
    }
    OUT.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(data["notes"], ensure_ascii=False))


if __name__ == "__main__":
    main()
