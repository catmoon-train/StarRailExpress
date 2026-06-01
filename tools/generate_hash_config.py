#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Mod Whitelist Hash Generator
一键获取mods、resourcepacks、shaderpacks文件夹下的文件SHA256哈希值
并生成适配mod_whitelist配置文件的JSON数组格式

使用方法:
1. 确保在StarRailExpress项目根目录下运行
2. 程序会自动检测run/mods, run/resourcepacks, run/shaderpacks目录
3. 如果存在现有的配置文件，会进行合并而不是覆盖
4. 生成的哈希值可以直接复制到ALLOWED_RESOURCE_PACK_HASHES和ALLOWED_SHADER_PACK_HASHES配置中
"""

import os
import sys
import json
import hashlib
from pathlib import Path
from typing import List, Dict, Set


def calculate_sha256(file_path: str) -> str:
    """计算文件的SHA256哈希值（适配Java SHA256Utils.hash(Path)逻辑）"""
    sha256_hash = hashlib.sha256()
    try:
        # 先更新文件名（对应Java SHA256Utils.updateBytes）
        file_name = os.path.basename(file_path)
        sha256_hash.update(file_name.encode('utf-8'))
        sha256_hash.update(b'\x00')  # 对应Java digest.update((byte) 0)
        
        # 再更新文件内容（对应Java updateFileBytes）
        with open(file_path, "rb") as f:
            # 分块读取大文件，避免内存溢出
            for chunk in iter(lambda: f.read(4096), b""):
                sha256_hash.update(chunk)
        return sha256_hash.hexdigest()
    except Exception as e:
        print(f"警告: 无法读取文件 {file_path}: {e}")
        return None


def get_files_from_directory(directory: str) -> List[str]:
    """获取目录下所有文件的路径列表"""
    if not os.path.exists(directory):
        print(f"目录不存在: {directory}")
        return []
    
    files = []
    for item in os.listdir(directory):
        item_path = os.path.join(directory, item)
        if os.path.isfile(item_path):
            files.append(item_path)
        else:
            print(f"跳过目录: {item_path}")
    
    return files


def load_existing_config(config_path: str) -> Dict:
    """加载现有的配置文件"""
    if not os.path.exists(config_path):
        return {}
    
    try:
        with open(config_path, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception as e:
        print(f"警告: 无法加载现有配置文件 {config_path}: {e}")
        return {}


def save_config(config: Dict, config_path: str):
    """保存配置文件"""
    try:
        # 确保目录存在
        os.makedirs(os.path.dirname(config_path), exist_ok=True)
        
        with open(config_path, 'w', encoding='utf-8') as f:
            json.dump(config, f, indent=2, ensure_ascii=False)
        print(f"配置已保存到: {config_path}")
    except Exception as e:
        print(f"错误: 无法保存配置文件 {config_path}: {e}")


def merge_hashes(existing_hashes: List[str], new_hashes: List[str]) -> List[str]:
    """合并哈希值列表，去重并保持顺序"""
    existing_set = set(existing_hashes)
    merged = existing_hashes.copy()
    
    for hash_val in new_hashes:
        if hash_val and hash_val not in existing_set:
            merged.append(hash_val)
            existing_set.add(hash_val)
    
    return merged


def main():
    # 项目根目录
    project_root = Path(__file__).parent.parent
    
    # Minecraft运行目录
    run_dir = project_root / "run"
    mods_dir = run_dir / "mods"
    resourcepacks_dir = run_dir / "resourcepacks" 
    shaderpacks_dir = run_dir / "shaderpacks"
    
    # 配置文件路径
    config_dir = run_dir / "config" / "mod_whitelist"
    hash_config_file = config_dir / "allowed_hashes.json"
    
    print("=== Mod Whitelist Hash Generator ===")
    print(f"项目根目录: {project_root}")
    print(f"Minecraft运行目录: {run_dir}")
    print()
    
    # 收集各个目录的哈希值
    all_hashes = {
        "ALLOWED_RESOURCE_PACK_HASHES": [],
        "ALLOWED_SHADER_PACK_HASHES": []
    }
    
    # 处理资源包
    if os.path.exists(resourcepacks_dir):
        print("正在处理资源包 (resourcepacks)...")
        resourcepack_files = get_files_from_directory(str(resourcepacks_dir))
        for file_path in resourcepack_files:
            file_name = os.path.basename(file_path)
            hash_val = calculate_sha256(file_path)
            if hash_val:
                all_hashes["ALLOWED_RESOURCE_PACK_HASHES"].append(hash_val)
                print(f"  ✓ {file_name} -> {hash_val}")
            else:
                print(f"  ✗ {file_name} -> 计算失败")
        print(f"资源包处理完成，共找到 {len(resourcepack_files)} 个文件")
    else:
        print("资源包目录不存在，跳过处理")
    
    print()
    
    # 处理光影包
    if os.path.exists(shaderpacks_dir):
        print("正在处理光影包 (shaderpacks)...")
        shaderpack_files = get_files_from_directory(str(shaderpacks_dir))
        for file_path in shaderpack_files:
            file_name = os.path.basename(file_path)
            hash_val = calculate_sha256(file_path)
            if hash_val:
                all_hashes["ALLOWED_SHADER_PACK_HASHES"].append(hash_val)
                print(f"  ✓ {file_name} -> {hash_val}")
            else:
                print(f"  ✗ {file_name} -> 计算失败")
        print(f"光影包处理完成，共找到 {len(shaderpack_files)} 个文件")
    else:
        print("光影包目录不存在，跳过处理")
    
    print()
    
    # 加载现有配置并合并
    existing_config = load_existing_config(str(hash_config_file))
    merged_config = existing_config.copy()
    
    for key, new_hashes in all_hashes.items():
        if new_hashes:
            existing_hashes = existing_config.get(key, [])
            merged_hashes = merge_hashes(existing_hashes, new_hashes)
            merged_config[key] = merged_hashes
            print(f"{key}: 新增 {len(merged_hashes) - len(existing_hashes)} 个哈希值，总共 {len(merged_hashes)} 个")
        else:
            if key not in merged_config:
                merged_config[key] = []
    
    # 保存配置
    if any(hashes for hashes in all_hashes.values()):
        save_config(merged_config, str(hash_config_file))
        print()
        print("=== 使用说明 ===")
        print("1. 打开配置文件:", hash_config_file)
        print("2. 复制 ALLOWED_RESOURCE_PACK_HASHES 和 ALLOWED_SHADER_PACK_HASHES 数组内容")
        print("3. 粘贴到 ./config/starrailexpress-config.json 中对应的配置项")
        print("4. 确保在配置文件中启用相应的验证选项:")
        print("   - ENABLE_RESOURCE_PACK_VERIFICATION: true")
        print("   - VERIFY_RESOURCE_PACK_HASHES: true") 
        print("   - ENABLE_SHADER_PACK_VERIFICATION: true")
        print("   - VERIFY_SHADER_PACK_HASHES: true")
    else:
        print("没有找到任何文件来生成哈希值")
    
    print()
    print("程序执行完成！")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n程序被用户中断")
        sys.exit(1)
    except Exception as e:
        print(f"程序执行出错: {e}")
        sys.exit(1)