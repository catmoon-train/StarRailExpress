#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
add_gpl_header.py - 为 Java 源文件添加 GPL v3 许可头注释
用法: python add_gpl_header.py [--dir DIR] [--year YEAR] [--holder HOLDER] [--backup]
"""

import os
import argparse
import datetime
import re

# GPL v3 头注释模板（使用 Java 的 /* ... */ 风格）
GPL_HEADER_TEMPLATE = """/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
"""

# 用于检测是否已包含 GPL 头的正则表达式（匹配 "GNU General Public License"）
GPL_PATTERN = re.compile(r'GNU General Public License', re.IGNORECASE)


def parse_arguments():
    """解析命令行参数"""
    parser = argparse.ArgumentParser(
        description='为 Java 源文件添加 GPL v3 许可头注释'
    )
    parser.add_argument(
        '--dir', '-d',
        default='.',
        help='要处理的根目录（默认为当前目录）'
    )
    parser.add_argument(
        '--year', '-y',
        default=str(datetime.datetime.now().year),
        help='版权年份（默认为当前年份）'
    )
    parser.add_argument(
        '--holder', '-H',
        default='Your Name/Organization',
        help='版权持有人（默认为 "Your Name/Organization"）'
    )
    parser.add_argument(
        '--backup', '-b',
        action='store_true',
        help='修改前为原始文件创建备份（扩展名为 .bak）'
    )
    parser.add_argument(
        '--force', '-f',
        action='store_true',
        help='强制覆盖已存在的 GPL 头（默认跳过）'
    )
    return parser.parse_args()


def build_header(year, holder):
    """根据年份和持有人生成完整的 GPL 头注释"""
    return GPL_HEADER_TEMPLATE.format(year=year, holder=holder)


def has_gpl_header(content):
    """检查文件内容是否已包含 GPL 头（基于关键字匹配）"""
    return bool(GPL_PATTERN.search(content))


def process_file(file_path, header, backup=False, force=False):
    """
    处理单个 Java 文件：
    - 如果已有 GPL 头且未强制覆盖，则跳过
    - 否则在文件开头插入新的 GPL 头（保留原有内容）
    """
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except UnicodeDecodeError:
        print(f"跳过 {file_path}（非 UTF-8 编码）")
        return

    # 检查是否已有 GPL 头
    if has_gpl_header(content):
        if not force:
            print(f"跳过 {file_path}（已包含 GPL 头）")
            return
        else:
            print(f"强制覆盖 {file_path} 的 GPL 头")

    # 备份（如果启用）
    if backup:
        backup_path = file_path + '.bak'
        try:
            with open(backup_path, 'w', encoding='utf-8') as bf:
                bf.write(content)
            print(f"已备份 {file_path} -> {backup_path}")
        except Exception as e:
            print(f"备份失败 {file_path}: {e}")
            return

    # 插入新的 GPL 头
    new_content = header + '\n' + content
    try:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"已处理 {file_path}")
    except Exception as e:
        print(f"写入失败 {file_path}: {e}")


def main():
    args = parse_arguments()
    root_dir = os.path.abspath(args.dir)
    if not os.path.isdir(root_dir):
        print(f"错误：目录 '{root_dir}' 不存在")
        return 1

    header = build_header(args.year, args.holder)
    print(f"使用的 GPL 头：\n{header}")

    # 遍历目录
    for dirpath, dirnames, filenames in os.walk(root_dir):
        for filename in filenames:
            if filename.endswith('.java'):
                file_path = os.path.join(dirpath, filename)
                process_file(file_path, header, args.backup, args.force)

    print("处理完成。")
    return 0


if __name__ == '__main__':
    exit(main())