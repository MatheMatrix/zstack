# 07 — Group 透传与并发、失败聚合

## 7.1 入口排队

**`VolumeSnapshotGroupBase.handle(APIDeleteVolumeSnapshotGroupMsg)`** — `:163`

```java
private void handle(APIDeleteVolumeSnapshotGroupMsg msg) {
    thdf.chainSubmit(new ChainTask(msg) {
        @Override
        public String getSyncSignature() { return id; }   // "volumeSnapshotGroup-<uuid>"
        @Override
        public void run(SyncTaskChain chain) {
            handleDelete(msg, new NoErrorCompletion(chain) {
                @Override public void done() { chain.next(); }
            });
        }
    });
}
```

按 group uuid 串行排队，防止同一 group 并发删除。

## 7.2 API → Inner 转发

**`handleDelete`** — `:187-210`

```java
DeleteVolumeSnapshotGroupInnerMsg imsg = new DeleteVolumeSnapshotGroupInnerMsg();
imsg.setUuid(msg.getUuid());
imsg.setDeletionMode(msg.getDeletionMode());
imsg.setScope(msg.getScope());          // ← 透传
imsg.setDirection(msg.getDirection());  // ← 透传

overlaySend(imsg, new CloudBusCallBack(msg) { ... });
// overlaySend：包成 VolumeSnapshotGroupOverlayMsg，路由到 VmInstance mailbox
// 保证"快照组删除"与"VM 状态变更"互斥
```

## 7.3 真正的并行循环

**`handle(DeleteVolumeSnapshotGroupInnerMsg)`** — `:212-254`

```java
SimpleFlowChain.of("delete-volume-snapshot-group")
    .then("delete-volume-snapshots", trigger ->
        new While<>(snapshots).step((snapshot, compl) -> {
            DeleteVolumeSnapshotMsg rmsg = new DeleteVolumeSnapshotMsg();
            rmsg.setSnapshotUuid(snapshot.getUuid());
            rmsg.setVolumeUuid(snapshot.getVolumeUuid());
            rmsg.setTreeUuid(snapshot.getTreeUuid());
            rmsg.setDeletionMode(msg.getDeletionMode());
            rmsg.setScope(msg.getScope());          // ← 逐快照透传
            rmsg.setDirection(msg.getDirection());  // ← 逐快照透传

            bus.makeTargetServiceIdByResourceUuid(rmsg, VolumeSnapshotConstant.SERVICE_ID,
                    getResourceIdToRouteMsg(snapshot));

            bus.send(rmsg, new CloudBusCallBack(compl) {
                @Override
                public void run(MessageReply r) {
                    reply.addResult(new DeleteSnapshotGroupResult(
                        rmsg.getSnapshotUuid(),
                        rmsg.getVolumeUuid(),
                        r.getError()));
                    compl.done();   // 不短路
                }
            });
        }, 5)                            // ← 并发度 5
        .run(new WhileDoneCompletion(msg) {
            @Override
            public void done(ErrorCodeList errs) {
                trigger.next();          // 错误聚合在 reply.results
            }
        }))
    .then("delete-vm-host-backup-files", trigger -> {
        vmHostFileManager.cleanVmHostBackupFile(self.getUuid());
        trigger.next();
    })
    .done(() -> bus.reply(msg, reply))
    .error(errorCode -> {
        reply.setError(errorCode);
        bus.reply(msg, reply);
    })
    .start();
```

## 7.4 关键设计点

| 维度 | 说明 |
|---|---|
| 按卷分组 | `getEffectiveSnapshots()` 过滤出当前 VM 各卷的快照 |
| 并发度 | **5**（`While.step(..., 5)`） |
| 失败处理 | 每条独立 `compl.done()`，**不短路** |
| 错误聚合 | `reply.addResult(snapshotUuid, volumeUuid, errorCode)` |
| 整体回滚 | **无**；部分成功保留，返回结果列表 |
| 前置检查 | 删除流程**不**检查 `VolumeSnapshotGroupAvailability` |
| 入口唯一性 | `APIDeleteVolumeSnapshotGroupMsg` 与 `DeleteVolumeSnapshotGroupInnerMsg` 都只在此类处理 |
