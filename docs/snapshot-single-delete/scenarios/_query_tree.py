#!/usr/bin/env python3
import paramiko
import sys

HOST = "172.26.53.180"
USER = "root"
PWD = "admin@123"
VM_UUID = "fa51c9637c024d94a556dd474a5cd74e"

def run(client, cmd):
    stdin, stdout, stderr = client.exec_command(cmd)
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    return out, err

def mysql(client, sql):
    cmd = "mysql -pzstack.mysql.password zstack -t -e \"" + sql.replace('"', '\\"') + "\""
    out, err = run(client, cmd)
    # Filter out mysql password warning
    err = "\n".join([l for l in err.splitlines() if "Using a password" not in l and l.strip()])
    return out, err

def main():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PWD, timeout=10)

    print("=" * 70)
    print("1. VM 基本信息")
    print("=" * 70)
    out, err = mysql(client,
        f"SELECT uuid, name, state, rootVolumeUuid, hostUuid FROM VmInstanceVO WHERE uuid='{VM_UUID}'\\G")
    print(out); print(err) if err else None

    print("=" * 70)
    print("2. Root Volume 信息")
    print("=" * 70)
    out, err = mysql(client,
        f"SELECT v.uuid, v.name, v.type, v.installPath, v.size, v.primaryStorageUuid, v.rootImageUuid "
        f"FROM VolumeVO v JOIN VmInstanceVO vm ON v.uuid=vm.rootVolumeUuid WHERE vm.uuid='{VM_UUID}'\\G")
    print(out); print(err) if err else None

    print("=" * 70)
    print("3. 快照树 VolumeSnapshotTreeVO")
    print("=" * 70)
    out, err = mysql(client,
        f"SELECT t.uuid AS treeUuid, t.volumeUuid, t.current, t.createDate "
        f"FROM VolumeSnapshotTreeVO t JOIN VolumeVO v ON t.volumeUuid=v.uuid "
        f"JOIN VmInstanceVO vm ON v.uuid=vm.rootVolumeUuid WHERE vm.uuid='{VM_UUID}'\\G")
    print(out); print(err) if err else None

    print("=" * 70)
    print("4. 快照树所有节点 VolumeSnapshotVO")
    print("=" * 70)
    out, err = mysql(client,
        f"SELECT s.uuid, s.name, s.parentUuid, s.treeUuid, s.distance, s.latest, s.size, s.primaryStorageInstallPath "
        f"FROM VolumeSnapshotVO s "
        f"JOIN VolumeVO v ON s.volumeUuid=v.uuid "
        f"JOIN VmInstanceVO vm ON v.uuid=vm.rootVolumeUuid "
        f"WHERE vm.uuid='{VM_UUID}' "
        f"ORDER BY s.distance, s.createDate\\G")
    print(out); print(err) if err else None

    print("=" * 70)
    print("5. 物理 backing chain（在物理机上 qemu-img info）")
    print("=" * 70)
    # First get rootVolume installPath
    out, err = run(client,
        f"mysql -pzstack.mysql.password zstack -N -e \""
        f"SELECT v.installPath FROM VolumeVO v JOIN VmInstanceVO vm ON v.uuid=vm.rootVolumeUuid WHERE vm.uuid='{VM_UUID}'\"")
    root_path = out.strip().splitlines()[-1].strip() if out.strip() else ""
    print(f"vol.installPath = {root_path}")

    # Get all snapshot paths
    out, err = run(client,
        f"mysql -pzstack.mysql.password zstack -N -e \""
        f"SELECT s.name, s.primaryStorageInstallPath FROM VolumeSnapshotVO s "
        f"JOIN VolumeVO v ON s.volumeUuid=v.uuid "
        f"JOIN VmInstanceVO vm ON v.uuid=vm.rootVolumeUuid "
        f"WHERE vm.uuid='{VM_UUID}'\"")
    print("快照物理路径列表：")
    print(out)

    # Trace backing chain from vol
    if root_path:
        print(f"\n--- qemu-img info --backing-chain {root_path} ---")
        out, err = run(client, f"qemu-img info --backing-chain {root_path} 2>&1")
        print(out)

    client.close()

if __name__ == "__main__":
    main()
