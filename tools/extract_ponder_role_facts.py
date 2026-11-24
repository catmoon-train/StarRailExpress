# -*- coding: utf-8 -*-
"""提取职业思索所需的事实数据（只读源码与语言文件）。

输出：tools/ponder_role_facts.json

用法：python tools/extract_ponder_role_facts.py

数据来源：
  - 语言文件 assets/role_modifier_intro/lang/*.json 的 info.screen.roleid.<id>
  - 语言文件 assets/role_name_goals/lang/*.json 的 announcement.star.role.<id> / announcement.star.goals.<id>
  - 源码里的职业注册（ModRoles / BounsRoles / TH*Roles / AnimeRoles / TMMRoles ...）
  - 源码里的初始物品（RoleInitialItems）与疯狂模式皮肤（getPsychoSkin）

这些数据只用于「决定场景里放什么」，教程正文仍然逐句引用介绍原文。
"""

import io
import json
import os
import re
from collections import OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src", "main", "resources" if False else "src", "main", "java")
JAVA = os.path.join(ROOT, "src", "main", "java")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets")
OUT = os.path.join(ROOT, "tools", "ponder_role_facts.json")

LOCALES = ("zh_cn", "en_us", "zh_tw")

OTHER_MODE = [
    "custom_pending", "discovery_civilian", "hide_and_seek_seeker", "super_loose_end",
    "ghost_loose_end", "dirt_id", "goose", "duck",
    "echo_carrier", "echo_interceptor", "echo_broker",
]
PLACEHOLDER = ["error404_puppet", "example_neutral", "null", "unknown"]
EVENTS = ["command_block", "night_event", "halloween_event"]
# RoleData 的 init 里发放、没有走 getDefaultItems 的初始物品。
# AnglerRoleData.java:38 -> AnglerCatchHandler.giveStartingRod -> noellesroles:angler_rod
DATA_INIT_ITEMS = {"angler": ["noellesroles:angler_rod"]}

ROLE_FILES = [
    "io/wifi/starrailexpress/api/TMMRoles.java",
    "org/agmas/noellesroles/role/ModRoles.java",
    "org/agmas/noellesroles/role/ModMeetingRoles.java",
    "org/agmas/noellesroles/role/TraitorAndModifiers.java",
    "org/agmas/noellesroles/role/bouns/BounsRoles.java",
    "org/agmas/noellesroles/role/touhou/THRedHouseRoles.java",
    "org/agmas/noellesroles/role/touhou/THMountainRoles.java",
    "org/agmas/noellesroles/role/touhou/THMiscRoles.java",
    "org/agmas/noellesroles/role/touhou/THMagicForestRoles.java",
    "org/agmas/noellesroles/role/touhou/THLostForestRoles.java",
    "org/agmas/noellesroles/role/touhou/THHumanVillageRoles.java",
    "org/agmas/noellesroles/role/anime/AnimeRoles.java",
    "org/agmas/noellesroles/RicesRoleRhapsody.java",
]

ROLE_FIELD_RE = re.compile(r'public\s+static\s+(?:final\s+)?SRERole\s+([A-Z][A-Z0-9_]*)\s*=')
COLOR_RE = re.compile(r'new\s+Color\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)\s*\.getRGB\(\)')
COLOR_HEX_RE = re.compile(r'0x([0-9A-Fa-f]{6})')
PSYCHO_RE = re.compile(
    r'getPsychoSkin\s*\([^)]*\)\s*\{\s*return\s+[A-Za-z_]+\.id\(\s*"(textures/entity/custom_psycho/[a-z0-9_/]+\.png)"')
ITEM_CONST_RE = re.compile(
    r'\b([A-Z][A-Z0-9_]*)\s*=\s*[^;]{0,240}?'
    r'(?:registrar\.createWithItem|createItem|\.create)\(\s*"([a-z0-9_]+)"')
ITEM_CONST_RE2 = re.compile(
    r'\b([A-Z][A-Z0-9_]*)\s*=\s*register\(\s*[^;]{0,240}?,\s*"([a-z0-9_]+)"')


def load(path):
    with io.open(path, encoding="utf-8-sig") as f:
        return json.load(f)


def read_java(rel):
    p = os.path.join(JAVA, rel.replace("/", os.sep))
    if not os.path.exists(p):
        return ""
    return io.open(p, encoding="utf-8", errors="ignore").read()


def strip_comments(text):
    """去掉行注释与块注释，避免注释里的分号/关键字干扰解析。"""
    out = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == '"':
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == '"':
                    break
                j += 1
            out.append(text[i:j + 1])
            i = j + 1
            continue
        if ch == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if ch == "/" and i + 1 < n and text[i + 1] == "*":
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                i += 1
            i += 2
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def statement_end(text, start):
    """从 start 起找到语句结束（括号与花括号深度都为 0 的分号）。"""
    depth = 0
    brace = 0
    i = start
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == '"':
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == '"':
                    break
                j += 1
            i = j + 1
            continue
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        elif ch == "{":
            brace += 1
        elif ch == "}":
            brace -= 1
        elif ch == ";" and depth <= 0 and brace <= 0:
            return i
        i += 1
    return n


def collect_item_constants():
    table = {}
    files = [
        ("io/wifi/starrailexpress/index/TMMItems.java", "trainmurdermystery"),
        ("org/agmas/noellesroles/init/ModItems.java", "noellesroles"),
        ("org/agmas/noellesroles/init/FunnyItems.java", "noellesroles"),
        ("io/wifi/starrailexpress/index/SREItems.java", "starrailexpress"),
        ("pro/fazeclan/river/stupid_express/constants/SEItems.java", "stupid_express"),
    ]
    for rel, namespace in files:
        text = read_java(rel)
        if not text:
            continue
        for const, path in ITEM_CONST_RE.findall(text):
            table.setdefault(const, namespace + ":" + path)
        for const, path in ITEM_CONST_RE2.findall(text):
            table.setdefault(const, namespace + ":" + path)
        # TMMItems.java 里部分物品（画板、管理员画板等）虽然写在这个类中，
        # 实际用的是 sreRegistrar.create，注册命名空间是 starrailexpress。
        # 不修正的话场景 items 会拿到 trainmurdermystery:drawing_board 这种
        # 不存在的物品，Ponderer 会直接跳过整个场景。
        for const, path in re.findall(
                r'([A-Z][A-Z0-9_]*)\s*=\s*sreRegistrar\.create\(\s*"([a-z0-9_/]+)"', text):
            table[const] = "starrailexpress:" + path
    return table


def path_of_role_expr(stmt, id_const):
    """从注册语句里找出角色 id 路径。"""
    m = re.search(r'(?:Noellesroles\.id|SRE\.id|SRE\.wifiId|SRE\.xiaoheihandId|\bid)\(\s*"([a-z0-9_]+)"', stmt)
    if m:
        return m.group(1)
    table = globals().get("_ID_CONST_TABLE", {})
    for m in re.finditer(r'\b([A-Z][A-Z0-9_]*_ID)\b', stmt):
        if m.group(1) in table:
            return table[m.group(1)]
    return None


def collect_id_constants():
    """全工程扫描 `<CONST>_ID = xxx.id("path")`，得到常量名 -> 角色 id 路径。"""
    table = {}
    pattern = re.compile(r'\b([A-Z][A-Z0-9_]*_ID)\s*=\s*[^;]{0,160}?\(\s*"([a-z0-9_]+)"')
    for base, _dirs, names in os.walk(JAVA):
        for name in names:
            if not name.endswith(".java"):
                continue
            full = os.path.join(base, name)
            text = io.open(full, encoding="utf-8", errors="ignore").read()
            for match in pattern.finditer(text):
                table.setdefault(match.group(1), match.group(2))
    return table


def collect_class_psycho_skins():
    """角色实现类 -> 疯狂模式皮肤贴图。"""
    table = {}
    pattern = re.compile(
        r'class\s+([A-Za-z0-9_]+)[^{;]*\{(?:[^{}]|\{[^{}]*\})*?'
        r'getPsychoSkin\s*\([^)]*\)\s*\{\s*return\s+[A-Za-z_]+\.id\(\s*"(textures/entity/custom_psycho/[a-z0-9_/]+\.png)"',
        re.S)
    for base, _dirs, names in os.walk(JAVA):
        for name in names:
            if not name.endswith(".java"):
                continue
            full = os.path.join(base, name)
            text = io.open(full, encoding="utf-8", errors="ignore").read()
            for match in pattern.finditer(text):
                table.setdefault(match.group(1), match.group(2))
    return table


def parse_role_statements():
    facts = OrderedDict()
    class_skins = collect_class_psycho_skins()
    for rel in ROLE_FILES:
        raw = read_java(rel)
        if not raw:
            continue
        text = strip_comments(raw)
        for match in ROLE_FIELD_RE.finditer(text):
            const = match.group(1)
            end = statement_end(text, match.end())
            stmt = text[match.start():end]
            path = path_of_role_expr(stmt, const + "_ID")
            if not path:
                continue
            color = None
            cm = COLOR_RE.search(stmt)
            if cm:
                r, g, b = (int(x) for x in cm.groups())
                color = (r << 16) | (g << 8) | b
            else:
                hm = COLOR_HEX_RE.search(stmt)
                if hm:
                    color = int(hm.group(1), 16)
            booleans = re.findall(r'\b(true|false)\b', stmt)
            is_innocent = booleans[0] == "true" if len(booleans) >= 1 else None
            can_use_killer = booleans[1] == "true" if len(booleans) >= 2 else None
            skin = None
            psycho = PSYCHO_RE.search(stmt)
            if psycho:
                skin = psycho.group(1)
            else:
                for cm2 in re.finditer(r'new\s+([A-Za-z0-9_]+)\s*[\(\{]', stmt):
                    if cm2.group(1) in class_skins:
                        skin = class_skins[cm2.group(1)]
                        break
            facts[path] = {
                "const": const,
                "file": rel,
                "color": color,
                "isInnocent": is_innocent,
                "canUseKiller": can_use_killer,
                "otherMode": "setOtherModeRole(true)" in stmt,
                "eventNeutral": "setEventNeutral(true)" in stmt,
                "hiddenForRotation": "setHiddenForRoleRotation(true)" in stmt,
                "vigilanteTeam": "setVigilanteTeam(true)" in stmt,
                "specialNeutral": "setSpecialNeutral(true)" in stmt,
                "neutrals": "setNeutrals(true)" in stmt,
                "psychoSkin": skin,
            }
    return facts


def collect_initial_items():
    """职业 id -> 初始物品 id 列表。"""
    text = strip_comments(read_java("org/agmas/noellesroles/init/RoleInitialItems.java"))
    item_table = collect_item_constants()
    result = {}
    for match in re.finditer(r'INITIAL_ITEMS_MAP\.put\(\s*([A-Za-z_][A-Za-z0-9_.]*)\s*,\s*([A-Za-z_][A-Za-z0-9_]*)', text):
        const = match.group(1).split(".")[-1]
        var = match.group(2)
        # 找到该变量最近的一次声明位置；窗口内可能含有 lambda / 方法引用 / 嵌套块，
        # 只认 `.add()` 的旧写法会漏掉 Items.* 和整块 lambda 里的物品。
        decl = None
        for dm in re.finditer(r'\b' + re.escape(var) + r'\s*=\s*new\s+ArrayList', text):
            if dm.start() < match.start():
                decl = dm.start()
        items = []
        if decl is not None:
            window = text[decl:match.start()]
            for name in re.findall(r'\b[A-Za-z_][A-Za-z0-9_]*\.([A-Z][A-Z0-9_]*)', window):
                resolved = item_table.get(name)
                if resolved and resolved not in items:
                    items.append(resolved)
            # 原版物品不在 item_table 里，单独识别 Items.X。
            for name in re.findall(r'\bItems\.([A-Z][A-Z0-9_]*)', window):
                resolved = "minecraft:" + name.lower()
                if resolved not in items:
                    items.append(resolved)
        path = globals().get("_PATH_BY_CONST", {}).get(const) or const.lower()
        result[path] = items
    return result


def collect_inventory_screen_roles():
    """声明了背包界面扩展（技能在背包里选人）的职业常量集合。"""
    consts = set()
    pattern = re.compile(r'\b([A-Za-z_][A-Za-z0-9_.]*)\.setInventoryScreenExtensionFactory\s*\(')
    for base, _dirs, names in os.walk(JAVA):
        for name in names:
            if not name.endswith(".java"):
                continue
            full = os.path.join(base, name)
            text = io.open(full, encoding="utf-8", errors="ignore").read()
            for match in pattern.finditer(text):
                consts.add(match.group(1).split(".")[-1])
    return consts


def collect_class_to_path(registrations):
    """职业实现类的简单类名 -> 职业 id。"""
    table = {}
    for rel in ROLE_FILES:
        text = strip_comments(read_java(rel))
        for match in ROLE_FIELD_RE.finditer(text):
            const = match.group(1)
            end = statement_end(text, match.end())
            stmt = text[match.start():end]
            path = path_of_role_expr(stmt, const + "_ID")
            if not path:
                # 事件职业（雾客 / 史蒂夫等）注册语句里不一定写 id("...")，
                # 常量名本身就是角色 id 的大写形式。
                path = (const[:-3] if const.endswith("_ID") else const).lower()
            for cm in re.finditer(r'new\s+(?:[A-Za-z_][\w]*\.)*([A-Za-z0-9_]+)\s*[\(\{]', stmt):
                table.setdefault(cm.group(1), path)
    return table


def collect_shop_items():
    """职业 id -> 商店条目（物品 id）。尽力而为，运行时仍以真实 ShopEntry 为准。"""
    item_table = collect_item_constants()
    result = {}
    for base, _dirs, names in os.walk(JAVA):
        for name in names:
            if not name.endswith(".java"):
                continue
            full = os.path.join(base, name)
            raw = io.open(full, encoding="utf-8", errors="ignore").read()
            if "new ShopEntry(" not in raw:
                continue
            text = strip_comments(raw)
            classes = re.findall(r'(?:public|private|protected)?\s*(?:static\s+)?(?:final\s+)?class\s+([A-Za-z0-9_]+)', text)
            for match in re.finditer(r'getShopEntries\s*\([^)]*\)\s*\{', text):
                depth = 1
                i = match.end()
                while i < len(text) and depth > 0:
                    if text[i] == "{":
                        depth += 1
                    elif text[i] == "}":
                        depth -= 1
                    i += 1
                body = text[match.end():i]
                path = None
                for class_name in classes:
                    found = globals().get("_CLASS_TO_PATH", {}).get(class_name)
                    if found:
                        path = found
                        break
                if not path:
                    continue
                entries = result.setdefault(path, [])
                for em in re.finditer(
                        r'new\s+ShopEntry\(\s*(?:new\s+ItemStack\(\s*)?'
                        r'([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)?)',
                        body):
                    item_const = em.group(1).split(".")[-1]
                    item_id = item_table.get(item_const)
                    if item_id is None and item_const in ("BOW", "TRIDENT", "BUNDLE", "SPYGLASS"):
                        item_id = "minecraft:" + item_const.lower()
                    if item_id and item_id not in entries:
                        entries.append(item_id)
    return result


def collect_default_items(class_to_path):
    """扫职业实现类的 getDefaultItems() 覆写。

    RoleInitialItems 没有登记的角色会走 SRERole#getDefaultItems() 兜底，
    弩手 / 义警 / 剑士等开局物品都在这里，旧版抽取器完全没读这个方法。
    """
    item_table = collect_item_constants()
    result = {}
    for base, _dirs, names in os.walk(JAVA):
        for name in names:
            if not name.endswith(".java"):
                continue
            full = os.path.join(base, name)
            raw = io.open(full, encoding="utf-8", errors="ignore").read()
            if "getDefaultItems" not in raw:
                continue
            text = strip_comments(raw)
            for method in re.finditer(r'\bgetDefaultItems\s*\([^)]*\)\s*\{', text):
                cls = None
                for cm in re.finditer(r'\bclass\s+([A-Za-z0-9_]+)', text[:method.start()]):
                    cls = cm.group(1)
                path = class_to_path.get(cls) if cls else None
                if not path:
                    # 匿名 EggRole/NormalRole 覆写 getDefaultItems() 时没有类名可用，
                    # 用最近的 registerRole 常量名回推角色 id（如 LIN_FAMILY）。
                    field = None
                    for fm in re.finditer(r'\b([A-Z][A-Z0-9_]*)\s*=\s*(?:TMMRoles\.)?registerRole\s*\(',
                                          text[:method.start()]):
                        field = fm.group(1)
                    if field:
                        const = field[:-3] if field.endswith("_ID") else field
                        path = globals().get("_PATH_BY_CONST", {}).get(const) or const.lower()
                if not path:
                    continue
                depth, index = 1, method.end()
                while index < len(text) and depth > 0:
                    if text[index] == "{":
                        depth += 1
                    elif text[index] == "}":
                        depth -= 1
                    index += 1
                body = text[method.end():index - 1]
                items = []
                for item_name in re.findall(r'\b[A-Za-z_][\w]*\.([A-Z][A-Z0-9_]*)', body):
                    item_id = item_table.get(item_name)
                    if item_id and item_id not in items:
                        items.append(item_id)
                for item_name in re.findall(r'\bItems\.([A-Z][A-Z0-9_]*)', body):
                    item_id = "minecraft:" + item_name.lower()
                    if item_id not in items:
                        items.append(item_id)
                if items:
                    result.setdefault(path, [])
                    for item_id in items:
                        if item_id not in result[path]:
                            result[path].append(item_id)
    return result


def collect_extra_initial_items():
    """补扫 ModRolesInitialEventRegister 里直接发放的初始物品。"""
    text = strip_comments(read_java("org/agmas/noellesroles/init/ModRolesInitialEventRegister.java"))
    if not text:
        return {}
    item_table = collect_item_constants()
    id_table = collect_id_constants()
    result = {}
    # 分支头有 role.equals / role.identifier().equals / RoleUtils.compareRole 三种写法。
    branch_re = re.compile(
        r'if\s*\(\s*(?:role\.equals\s*\(\s*|role\.identifier\(\)\.equals\s*\(\s*'
        r'|RoleUtils\.compareRole\s*\([^,]+,\s*)([A-Za-z_][\w.]*)')
    add_re = re.compile(
        r'(?:player\.addItem\s*\(\s*|(?:RoleUtils|MCItemsUtils|SREItemUtils)'
        r'\.insertStackInFreeSlot\s*\(\s*player\s*,\s*|new\s+ItemStack\s*\(\s*)'
        r'([A-Za-z_][\w.]*)')
    for match in branch_re.finditer(text):
        const = match.group(1).replace(".identifier", "").split(".")[-1]
        # 外部模组常量不一定能被 collect_id_constants 扫到，常量名一般就是大写角色 id。
        path = id_table.get(const) or const.lower()
        end = min(match.end() + 1400, len(text))
        # 分支通常以 return; 结束，截断窗口避免把下一个职业发放的物品混进来。
        stop = text.find("return;", match.end(), end)
        if stop != -1:
            end = stop
        window = text[match.end():end]
        items = []
        for add in add_re.finditer(window):
            expr = add.group(1)
            if expr.endswith(".getDefaultInstance"):
                expr = expr[:-len(".getDefaultInstance")]
            name = expr.split(".")[-1]
            item_id = item_table.get(name)
            if not item_id and expr.startswith("Items."):
                item_id = "minecraft:" + name.lower()
            if item_id and item_id not in items:
                items.append(item_id)
        if items:
            result.setdefault(path, [])
            for item_id in items:
                if item_id not in result[path]:
                    result[path].append(item_id)
    return result


def collect_handler_shop_items():
    """补扫 RoleShopHandler 里用局部变量拼出来的职业商店。

    ``collect_shop_items`` 只认 ``getShopEntries`` 方法；本工程的
    ``RoleShopHandler`` 是把 ``ArrayList<ShopEntry> XXX_SHOP`` 填好后再
    ``ShopContent.customEntries.put(ROLE_ID, XXX_SHOP)``，唱片师 / 纵火犯
    等职业的商店都在这一批里。这里把「局部变量 -> 职业路径」和
    「局部变量 -> 物品 id」两段对应关系补出来。
    """
    text = strip_comments(read_java("org/agmas/noellesroles/init/RoleShopHandler.java"))
    if not text:
        return {}
    item_table = collect_item_constants()
    id_table = collect_id_constants()

    arrays = {}
    for name, body in re.findall(r'\bItem\[\]\s+([A-Za-z_]\w*)\s*=\s*\{([^}]*)\}', text, re.S):
        arrays[name] = [part.strip().split(".")[-1] for part in body.split(",") if part.strip()]

    def resolve(expr):
        expr = expr.strip()
        if "[" in expr:
            array = expr.split("[", 1)[0].strip().split(".")[-1]
            names = arrays.get(array, [])
        else:
            # 捕获形如 ModItems.SPEAKER.getDefaultInstance( —— 取到常量名那一节即可。
            parts = expr.split(".")
            names = [parts[1] if len(parts) >= 2 else parts[0]]
        result = []
        for name in names:
            item_id = item_table.get(name)
            if item_id is None and name in ("BOW", "TRIDENT", "BUNDLE", "SPYGLASS"):
                item_id = "minecraft:" + name.lower()
            if item_id is None and name.startswith("MUSIC_DISC_"):
                item_id = "minecraft:" + name.lower()
            if item_id and item_id not in result:
                result.append(item_id)
        return result

    result = {}
    for var in set(re.findall(r'ArrayList<ShopEntry>\s+([A-Za-z_]\w*)\s*=\s*new ArrayList', text)):
        items = []
        pattern = (re.escape(var) + r'\.add\(\s*new ShopEntry\(\s*'
                   r'(?:new ItemStack\(\s*)?([^,;\)]+)')
        for match in re.finditer(pattern, text):
            for item_id in resolve(match.group(1)):
                if item_id not in items:
                    items.append(item_id)
        if not items:
            continue
        put = re.search(r'\.put\(\s*([A-Za-z_][\w.]*)\s*,\s*' + re.escape(var) + r'\s*\)', text)
        if not put:
            continue
        path = id_table.get(put.group(1).split(".")[-1])
        if path:
            result.setdefault(path, [])
            for item_id in items:
                if item_id not in result[path]:
                    result[path].append(item_id)
    return result


def main():
    global _ID_CONST_TABLE, _PATH_BY_CONST
    intros, names = {}, {}
    for locale in LOCALES:
        intros[locale] = load(os.path.join(
            ASSETS, "role_modifier_intro", "lang", locale + ".json"))
        names[locale] = load(os.path.join(
            ASSETS, "role_name_goals", "lang", locale + ".json"))

    _ID_CONST_TABLE = collect_id_constants()
    registrations = parse_role_statements()

    _PATH_BY_CONST = {}
    for path, reg in registrations.items():
        _PATH_BY_CONST.setdefault(reg["const"], path)
        _PATH_BY_CONST.setdefault(reg["const"] + "_ID", path)
    for const, path in _ID_CONST_TABLE.items():
        _PATH_BY_CONST.setdefault(const, path)
        _PATH_BY_CONST.setdefault(const[:-3] if const.endswith("_ID") else const, path)
    globals()["_PATH_BY_CONST"] = _PATH_BY_CONST

    initial_items = collect_initial_items()
    for path, items in collect_extra_initial_items().items():
        existing = initial_items.setdefault(path, [])
        for item_id in items:
            if item_id not in existing:
                existing.append(item_id)
    globals()["_CLASS_TO_PATH"] = collect_class_to_path(registrations)
    for path, items in collect_default_items(globals()["_CLASS_TO_PATH"]).items():
        existing = initial_items.setdefault(path, [])
        for item_id in items:
            if item_id not in existing:
                existing.append(item_id)
    for path, items in DATA_INIT_ITEMS.items():
        existing = initial_items.setdefault(path, [])
        for item_id in items:
            if item_id not in existing:
                existing.append(item_id)
    shop_items = collect_shop_items()
    for path, items in collect_handler_shop_items().items():
        existing = shop_items.setdefault(path, [])
        for item_id in items:
            if item_id not in existing:
                existing.append(item_id)
    screen_consts = collect_inventory_screen_roles()

    zh_intro = intros["zh_cn"]
    role_ids = [k[len("info.screen.roleid."):] for k in zh_intro
                if k.startswith("info.screen.roleid.") and not k.endswith(".simple")]

    facts = OrderedDict()
    for rid in role_ids:
        reg = registrations.get(rid, {})
        intro = {}
        for locale in LOCALES:
            text = intros[locale].get("info.screen.roleid." + rid, "")
            intro[locale] = text.split("\n")
        facts[rid] = {
            "id": rid,
            "name": {loc: names[loc].get("announcement.star.role." + rid, "") for loc in LOCALES},
            "goal": {loc: names[loc].get("announcement.star.goals." + rid, "") for loc in LOCALES},
            "intro": intro,
            "color": reg.get("color"),
            "isInnocent": reg.get("isInnocent"),
            "canUseKiller": reg.get("canUseKiller"),
            "otherMode": bool(reg.get("otherMode")) or rid in OTHER_MODE,
            "eventNeutral": bool(reg.get("eventNeutral")) or rid in EVENTS,
            "hiddenForRotation": bool(reg.get("hiddenForRotation")),
            "vigilanteTeam": bool(reg.get("vigilanteTeam")),
            "specialNeutral": bool(reg.get("specialNeutral")),
            "neutrals": bool(reg.get("neutrals")),
            "psychoSkin": reg.get("psychoSkin"),
            "inventoryScreen": reg.get("const", "") in screen_consts,
            "initialItems": initial_items.get(rid, []),
            "shopItems": shop_items.get(rid, []),
            "source": reg.get("file", ""),
            "const": reg.get("const", ""),
        }

    payload = {
        "roles": facts,
        "notes": {
            "total": len(facts),
            "registered": sum(1 for v in facts.values() if v["source"]),
            "withInitialItems": sum(1 for v in facts.values() if v["initialItems"]),
            "withShop": sum(1 for v in facts.values() if v["shopItems"]),
            "withPsychoSkin": sum(1 for v in facts.values() if v["psychoSkin"]),
            "otherMode": sum(1 for v in facts.values() if v["otherMode"]),
            "placeholder": [p for p in PLACEHOLDER if p in facts],
        },
    }
    with io.open(OUT, "w", encoding="utf-8") as f:
        f.write(json.dumps(payload, ensure_ascii=False, indent=1))
    print(json.dumps(payload["notes"], ensure_ascii=False))


if __name__ == "__main__":
    main()
