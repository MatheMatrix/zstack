#!/usr/bin/env python3
import subprocess
import sys
import re
import os


def get_files(paths=['.', 'premium']):
    all_files = []
    for path in paths:
        if os.path.exists(path):
            result = subprocess.run(['git', 'ls-files'],
                                    cwd=path,
                                    capture_output=True,
                                    text=True)
            files = result.stdout.splitlines()
            path_files = [(os.path.join(path, f),
                           'pom' if 'pom.xml' in f else 'global')
                          for f in files
                          if 'pom.xml' in f or 'GlobalProperty' in f]
            all_files.extend(path_files)
    return all_files


def update_version_in_pom(file_path, new_version):
    try:
        # 使用 'rb' 模式读取，保留原始字节
        with open(file_path, 'rb') as f:
            content = f.read()

        # 将字节转换为字符串进行处理
        str_content = content.decode('utf-8')
        pattern = r'(<parent>.*?<version>)(.*?)(</version>.*?</parent>)'
        regex = re.compile(pattern, re.DOTALL)

        if not regex.search(str_content):
            print(f"Skipped {file_path} - no matching parent version found")
            return

        def replace_version(match):
            old_version = match.group(2)
            print(f"In {file_path}: Changing version from {
            old_version} to {new_version}")
            return f'{match.group(1)}{new_version}{match.group(3)}'

        new_content = regex.sub(replace_version, str_content)

        if new_content != str_content:
            # 使用 'wb' 模式写入，确保字节级别的一致性
            with open(file_path, 'wb') as f:
                f.write(new_content.encode('utf-8'))
            print(f"Updated {file_path}")

    except Exception as e:
        print(f"Error processing {file_path}: {str(e)}")


def update_version_in_global_property(file_path, old_version, new_version):
    try:
        # 使用 'rb' 模式读取，保留原始字节
        with open(file_path, 'rb') as f:
            content = f.read()

        # 将字节转换为字符串进行处理
        str_content = content.decode('utf-8')
        pattern = r'(@GlobalProperty\s*\([^)]*defaultValue\s*=\s*"[^"]*?)' + \
                  re.escape(old_version) + r'(\.tar\.gz"[^)]*\))'
        regex = re.compile(pattern)

        if not regex.search(str_content):
            print(
                f"Skipped {file_path} - no matching version in GlobalProperty")
            return

        def replace_version(match):
            print(f"In {file_path}: Changing version from {
            old_version} to {new_version}")
            return f'{match.group(1)}{new_version}{match.group(2)}'

        new_content = regex.sub(replace_version, str_content)

        if new_content != str_content:
            # 使用 'wb' 模式写入，确保字节级别的一致性
            with open(file_path, 'wb') as f:
                f.write(new_content.encode('utf-8'))
            print(f"Updated {file_path}")

    except Exception as e:
        print(f"Error processing {file_path}: {str(e)}")


def main():
    if len(sys.argv) != 3:
        print("Usage: python script.py <old_version> <new_version>")
        print("Example: python script.py 5.2.0 5.3.0")
        sys.exit(1)

    old_version = sys.argv[1]
    new_version = sys.argv[2]
    files = get_files()

    print(f"Found {len(files)} files to process")
    for file_path, file_type in files:
        if file_type == 'pom':
            update_version_in_pom(file_path, new_version)
        else:
            update_version_in_global_property(
                file_path, old_version, new_version)


if __name__ == "__main__":
    main()
