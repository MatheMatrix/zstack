# U28 Flyway Data Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce the idempotent Flyway migration `V5.5.18.2__schema.sql` that backfills `PhysicalServerVO` + `PhysicalServerRoleVO` + `PhysicalServerCapacityVO` + `ResourceVO` + `AccountResourceRefVO` rows for existing KVM / BM2 / Container / vcenter inventory, populates `ClusterEO.serverPoolUuid` and initial `ServerPoolVO` rows, backfills `PhysicalServerProvisionNetworkPoolRefVO` from BM2 ClusterRef history, and VIEW-izes `BareMetal2ProvisionNetworkVO` + `BareMetal2ProvisionNetworkClusterRefVO` over the unified tables (capacity PRD §2.1, provision PRD §2.2, compat PRD §2.3).

**Architecture:** Single Flyway file consisting of 9 idempotent SQL blocks executed in dependency order under one Flyway transaction. Every statement uses `INSERT ... ON DUPLICATE KEY UPDATE` or `INSERT IGNORE` / `CREATE OR REPLACE VIEW` so rerunning on an already-migrated schema is a no-op. Deterministic UUIDs derived from source entity uuid via `MD5(CONCAT(source_uuid, '-ps'))` for PS rows, via `MD5(CONCAT(zone_uuid, '-default-pool'))` for shared ServerPool rows. No Java code changes: this unit is schema-only and must be tagged atomically with U4/U5/U6/U7/U27 + the Phase 2H Java side.

**Tech Stack:** Flyway 4.x, MariaDB 10.3 (prod) / 10.11 (local test), plain DDL + DML. No Hibernate interaction during migration — runs from `flyway` classpath before Spring contexts start.

**Out of scope for U28 (lives elsewhere):**
- BM2 Java write-handler rewrites (3 handlers in `BareMetal2ProvisionNetworkManagerImpl`) — **U23/U24**.
- `@SoftDeletionCascades` removal on `BareMetal2ProvisionNetworkClusterRefVO` — **U23**.
- Rollback runbook — **U29**.
- BM1 chassis rows — explicitly skipped with a log message (out of unified hardware scope per ARCHITECT_DECISION).

---

## File Structure

Single deliverable file; one commit at the end.

- **Create:** `conf/db/upgrade/V5.5.18.2__schema.sql` (the Flyway migration — ~250 lines, 9 blocks)
- **Scratch (not committed):** `/tmp/u28-verify.sql` — post-migration verification queries (row-count diffs, orphan detection). Used for dry-run; can be kept in `docs/runbooks/` later under U29.
- **Not modified:** no Java files, no entity files, no Phase 1 `V5.5.18__schema.sql`, no U27 `V5.5.18.1__schema.sql`.

---

## SQL Block Order (dependency chain)

Execution order is load-bearing — downstream blocks assume upstream rows exist.

```
Block 0a  → ServerPoolVO (one per BM2-bearing cluster, NB-4 1:1)
Block 0b  → ServerPoolVO (one per non-BM2 zone, shared) + ClusterEO.serverPoolUuid backfill
Block 1a  → PhysicalServerVO from KVM HostEO (hypervisorType='KVM')
Block 1b  → PhysicalServerVO from BareMetal2ChassisVO
Block 1c  → PhysicalServerVO from NativeHostVO (via HostEO join)
Block 1.5 → PhysicalServerRoleVO for each row produced by 1a/1b/1c
Block 1.6 → ResourceVO + AccountResourceRefVO registration for every new PS row
Block 8   → PhysicalServerCapacityVO vcenter half-migration (HostCapacityVO_backup JOIN ESXHostVO)
Block B1  → PhysicalServerProvisionNetworkPoolRefVO backfill from BM2 ClusterRef (after 0b populated serverPoolUuid)
Block B2  → BareMetal2ProvisionNetworkVO → VIEW (data copy into PhysicalServerProvisionNetworkVO, RENAME source to *_backup, CREATE VIEW)
Block B3  → BareMetal2ProvisionNetworkClusterRefVO → VIEW (RENAME source to *_backup, CREATE VIEW over PoolRef JOIN ClusterVO)
```

Each block is written to be rerun-safe. No block depends on transient state from the same script run.

---

## Task 1: Skeleton + header comments + convention block

**Files:**
- Create: `conf/db/upgrade/V5.5.18.2__schema.sql`

- [ ] **Step 1: Create the file with header + convention scaffold**

```sql
-- Unified Hardware Management Phase 2I (U28):
-- Data migration that backfills PhysicalServer / Role / Capacity / Resource rows
-- for existing KVM / BM2 / NativeHost / vcenter inventory; initialises ServerPool
-- rows and ClusterEO.serverPoolUuid; migrates BM2 ProvisionNetwork history onto
-- the unified PoolRef model; converts BareMetal2ProvisionNetworkVO and
-- BareMetal2ProvisionNetworkClusterRefVO to VIEWs over the unified tables.
--
-- Atomic with U27 V5.5.18.1__schema.sql: this script assumes the DDL in U27
-- has already run (PhysicalServerCapacityVO table exists, HostCapacityVO is
-- already a VIEW, HostCapacityVO_backup holds pre-migration rows).
--
-- Idempotency invariants:
--   - All INSERTs use ON DUPLICATE KEY UPDATE (or INSERT IGNORE for ref tables
--     with composite UNIQUE) so reruns produce 0 new rows.
--   - Deterministic UUID derivation via MD5 with stable source-entity salts.
--   - All VIEWs use CREATE OR REPLACE.
--   - RENAME-to-backup blocks are guarded with existence checks so they
--     don't fail on rerun.
--
-- Admin account UUID hardcoded: 36c27e8ff05c4780bf6d2fa65700f22e (NB-15).
-- BM1 chassis rows (BaremetalChassisVO) are out of scope — logged, not migrated.

-- ============================================================================
-- Block 0a: ServerPool per BM2-bearing cluster (NB-4: BM2 cluster 1:1 pool)
-- ============================================================================

-- Block 0a goes here

-- ============================================================================
-- Block 0b: ServerPool per non-BM2 zone (shared) + ClusterEO.serverPoolUuid
-- ============================================================================

-- Block 0b goes here

-- ============================================================================
-- Block 1a: PhysicalServerVO backfill from KVMHostVO
-- ============================================================================

-- Block 1a goes here

-- ============================================================================
-- Block 1b: PhysicalServerVO backfill from BareMetal2ChassisVO
-- ============================================================================

-- Block 1b goes here

-- ============================================================================
-- Block 1c: PhysicalServerVO backfill from NativeHostVO
-- ============================================================================

-- Block 1c goes here

-- ============================================================================
-- Block 1.5: PhysicalServerRoleVO backfill (KVM_HOST / BAREMETAL_V2 / CONTAINER_HOST)
-- ============================================================================

-- Block 1.5 goes here

-- ============================================================================
-- Block 1.6: ResourceVO + AccountResourceRefVO registration
-- ============================================================================

-- Block 1.6 goes here

-- ============================================================================
-- Block 8: vcenter half-migration to PhysicalServerCapacityVO (U6/U27 COALESCE path)
-- ============================================================================

-- Block 8 goes here

-- ============================================================================
-- Block B1: PhysicalServerProvisionNetworkPoolRefVO backfill from BM2 ClusterRef
-- ============================================================================

-- Block B1 goes here

-- ============================================================================
-- Block B2: BareMetal2ProvisionNetworkVO data copy + rename + VIEW
-- ============================================================================

-- Block B2 goes here

-- ============================================================================
-- Block B3: BareMetal2ProvisionNetworkClusterRefVO rename + VIEW
-- ============================================================================

-- Block B3 goes here
```

- [ ] **Step 2: Dry-run skeleton on local MariaDB** (ensures Flyway parser accepts the file)

```bash
cd /home/mj/zstack-workspace/zstack-unifi-host
# DB zstack_u27_test already has U27 applied
mysql -u root zstack_u27_test < conf/db/upgrade/V5.5.18.2__schema.sql
```

Expected: exits 0, no new rows (all blocks are placeholders).

---

## Task 2: Block 0a — BM2 cluster 1:1 ServerPool

**Why:** NB-4 says BM2 clusters must have dedicated pools (isolation), non-BM2 share one zone-wide pool. Block 0a creates one ServerPool per cluster that contains any BM2 chassis.

**Files:**
- Modify: `conf/db/upgrade/V5.5.18.2__schema.sql` (replace "Block 0a goes here" comment)

- [ ] **Step 1: Write the block**

```sql
-- One ServerPool per cluster that holds BM2 chassis. Deterministic uuid derived
-- from clusterUuid so rerun is idempotent. Backfills ClusterEO.serverPoolUuid
-- for those clusters in the same statement family so Block B1 can JOIN on it.
INSERT INTO `ServerPoolVO`
    (`uuid`, `name`, `description`, `zoneUuid`, `state`, `createDate`, `lastOpDate`)
SELECT
    MD5(CONCAT(c.`uuid`, '-pool-bm2'))                    AS `uuid`,
    CONCAT('bm2-pool-', SUBSTRING(c.`uuid`, 1, 8))        AS `name`,
    'auto-created for BM2 chassis (U28 migration)'        AS `description`,
    c.`zoneUuid`                                          AS `zoneUuid`,
    'Enabled'                                             AS `state`,
    NOW()                                                 AS `createDate`,
    NOW()                                                 AS `lastOpDate`
FROM `ClusterEO` c
WHERE c.`deleted` IS NULL
  AND EXISTS (
      SELECT 1 FROM `BareMetal2ChassisVO` b
      WHERE b.`clusterUuid` = c.`uuid`
  )
ON DUPLICATE KEY UPDATE
    `lastOpDate` = `lastOpDate`;  -- no-op touch to make rerun explicit

-- Backfill ClusterEO.serverPoolUuid for BM2-bearing clusters (only when NULL).
UPDATE `ClusterEO` c
SET c.`serverPoolUuid` = MD5(CONCAT(c.`uuid`, '-pool-bm2'))
WHERE c.`deleted` IS NULL
  AND c.`serverPoolUuid` IS NULL
  AND EXISTS (
      SELECT 1 FROM `BareMetal2ChassisVO` b
      WHERE b.`clusterUuid` = c.`uuid`
  );
```

- [ ] **Step 2: Dry-run and verify expected row counts**

Preflight count BM2 clusters:
```bash
mysql -u root zstack_u27_test -e "SELECT COUNT(DISTINCT clusterUuid) FROM BareMetal2ChassisVO WHERE clusterUuid IS NOT NULL;"
```

Apply the new block:
```bash
mysql -u root zstack_u27_test < conf/db/upgrade/V5.5.18.2__schema.sql
mysql -u root zstack_u27_test -e "SELECT COUNT(*) AS pools FROM ServerPoolVO WHERE description LIKE '%BM2%';"
```

Expected: `pools` equals the preflight count. If 216 clone has zero BM2 chassis (likely), expect 0.

- [ ] **Step 3: Rerun idempotency check**

```bash
mysql -u root zstack_u27_test < conf/db/upgrade/V5.5.18.2__schema.sql
mysql -u root zstack_u27_test -e "SELECT COUNT(*) FROM ServerPoolVO;"
```

Expected: identical row count to Step 2 — rerun produced zero net change.

---

## Task 3: Block 0b — non-BM2 zone-shared ServerPool + ClusterEO backfill

**Why:** KVM-only / Container-only / empty / mixed-but-no-BM2 clusters share one ServerPool per zone. This gives the `HostCapacityVO` VIEW's `COALESCE(r.serverUuid, h.uuid)` fallback a valid pool target without forcing operators to hand-author pools.

**Files:**
- Modify: `conf/db/upgrade/V5.5.18.2__schema.sql` (replace "Block 0b goes here")

- [ ] **Step 1: Write the block**

```sql
-- One shared ServerPool per zone (covers all non-BM2 clusters in that zone).
-- Deterministic uuid derived from zoneUuid so reruns and cross-zone coexistence
-- are both stable.
INSERT INTO `ServerPoolVO`
    (`uuid`, `name`, `description`, `zoneUuid`, `state`, `createDate`, `lastOpDate`)
SELECT
    MD5(CONCAT(z.`uuid`, '-default-pool'))              AS `uuid`,
    'default-pool'                                      AS `name`,
    'auto-created zone-shared pool (U28 migration)'     AS `description`,
    z.`uuid`                                            AS `zoneUuid`,
    'Enabled'                                           AS `state`,
    NOW()                                               AS `createDate`,
    NOW()                                               AS `lastOpDate`
FROM `ZoneEO` z
WHERE z.`deleted` IS NULL
ON DUPLICATE KEY UPDATE
    `lastOpDate` = `lastOpDate`;

-- Backfill ClusterEO.serverPoolUuid for non-BM2 clusters. Leave already-set
-- values alone (Block 0a's assignments must not be overwritten; also honours
-- any operator-set value).
UPDATE `ClusterEO` c
SET c.`serverPoolUuid` = MD5(CONCAT(c.`zoneUuid`, '-default-pool'))
WHERE c.`deleted` IS NULL
  AND c.`serverPoolUuid` IS NULL;
```

- [ ] **Step 2: Dry-run + verify**

```bash
mysql -u root zstack_u27_test -e "SELECT COUNT(*) FROM ZoneEO WHERE deleted IS NULL;"
mysql -u root zstack_u27_test < conf/db/upgrade/V5.5.18.2__schema.sql
mysql -u root zstack_u27_test -e "SELECT COUNT(*) FROM ServerPoolVO; SELECT COUNT(*) FROM ClusterEO WHERE serverPoolUuid IS NOT NULL;"
```

Expected: pool count = active zones + BM2-bearing clusters (216 snapshot = 3 zones + 0 BM2 = 3 pools). ClusterEO.serverPoolUuid non-null count = 7 (all 216 clusters, 216 snapshot has no deleted clusters).

- [ ] **Step 3: Rerun idempotency check** — same counts after second apply.

---

## Task 4: Block 1a — PhysicalServerVO from KVMHostVO

**Why:** Every existing KVM HostEO row needs a PhysicalServerVO partner so Phase 2A/2B writes (now keyed on `PhysicalServerRoleVO.serverUuid`) hit a real row. Without this, `HostCapacityUpdater.resolveServerUuidOrThrow` will throw `CloudRuntimeException` for every pre-existing host after cutover (NB-24 fail-loud).

**Files:**
- Modify: `conf/db/upgrade/V5.5.18.2__schema.sql` (replace "Block 1a goes here")

- [ ] **Step 1: Write the block**

```sql
-- PhysicalServerVO rows for KVM HostEO (hypervisorType='KVM', not deleted).
-- PhysicalServerVO.uuid = MD5(HostEO.uuid + '-ps') so KVMHost uuid is
-- preserved in RoleVO.roleUuid for later reverse-lookup; name/zone copied
-- 1:1 from HostEO. managementIp copied so OOB discovery (future phase) has
-- a starting point. No OOB credentials seeded — NB-10 means missing OOB is
-- an admin responsibility; unified power API returns operr until OOB is set.
INSERT INTO `PhysicalServerVO`
    (`uuid`, `name`, `description`, `zoneUuid`, `poolUuid`, `managementIp`,
     `architecture`, `state`, `status`, `powerStatus`, `createDate`, `lastOpDate`)
SELECT
    MD5(CONCAT(h.`uuid`, '-ps'))                           AS `uuid`,
    h.`name`                                               AS `name`,
    CONCAT('migrated from KVM host ', h.`uuid`)            AS `description`,
    h.`zoneUuid`                                           AS `zoneUuid`,
    c.`serverPoolUuid`                                     AS `poolUuid`,
    h.`managementIp`                                       AS `managementIp`,
    h.`architecture`                                       AS `architecture`,
    h.`state`                                              AS `state`,
    h.`status`                                             AS `status`,
    'Unknown'                                              AS `powerStatus`,
    h.`createDate`                                         AS `createDate`,
    h.`lastOpDate`                                         AS `lastOpDate`
FROM `HostEO` h
JOIN `ClusterEO` c ON c.`uuid` = h.`clusterUuid` AND c.`deleted` IS NULL
WHERE h.`deleted` IS NULL
  AND h.`hypervisorType` = 'KVM'
  AND c.`serverPoolUuid` IS NOT NULL
ON DUPLICATE KEY UPDATE
    `lastOpDate` = `lastOpDate`;
```

- [ ] **Step 2: Dry-run + verify**

Preflight:
```bash
mysql -u root zstack_u27_test -e "SELECT COUNT(*) FROM HostEO WHERE deleted IS NULL AND hypervisorType='KVM';"
```

Apply + verify:
```bash
mysql -u root zstack_u27_test < conf/db/upgrade/V5.5.18.2__schema.sql
mysql -u root zstack_u27_test -e "SELECT COUNT(*) FROM PhysicalServerVO WHERE description LIKE 'migrated from KVM%';"
```

Expected: equal to preflight count (216 snapshot: 1 KVM host → 1 PS row).

- [ ] **Step 3: Verify zero orphans** (every migrated PS has a pool)

```bash
mysql -u root zstack_u27_test -e "
SELECT COUNT(*) AS orphans
FROM PhysicalServerVO p
LEFT JOIN ServerPoolVO sp ON sp.uuid = p.poolUuid
WHERE sp.uuid IS NULL;"
```

Expected: `orphans = 0`.

---

## Task 5: Block 1b — PhysicalServerVO from BareMetal2ChassisVO

**Files:**
- Modify: `conf/db/upgrade/V5.5.18.2__schema.sql` (replace "Block 1b goes here")

- [ ] **Step 1: Write the block**

```sql
-- PhysicalServerVO rows for BM2 chassis. Uses chassis uuid salted with '-ps'
-- so the PS uuid is distinct from the chassis (RoleVO.roleUuid) — matches
-- the KVM pattern for consistency. BM2 chassis live in a BM2-bearing cluster,
-- whose serverPoolUuid was populated by Block 0a. managementIp left NULL —
-- BM2 IPMI address lives in BareMetal2ChassisNicVO (not migrated in this PR;
-- operators can attach OOB later via UpdatePhysicalServerMsg).
INSERT INTO `PhysicalServerVO`
    (`uuid`, `name`, `description`, `zoneUuid`, `poolUuid`, `managementIp`,
     `architecture`, `state`, `status`, `powerStatus`, `createDate`, `lastOpDate`)
SELECT
    MD5(CONCAT(b.`uuid`, '-ps'))                           AS `uuid`,
    b.`name`                                               AS `name`,
    CONCAT('migrated from BM2 chassis ', b.`uuid`)         AS `description`,
    b.`zoneUuid`                                           AS `zoneUuid`,
    c.`serverPoolUuid`                                     AS `poolUuid`,
    NULL                                                   AS `managementIp`,
    NULL                                                   AS `architecture`,
    b.`state`                                              AS `state`,
    b.`status`                                             AS `status`,
    b.`powerStatus`                                        AS `powerStatus`,
    b.`createDate`                                         AS `createDate`,
    b.`lastOpDate`                                         AS `lastOpDate`
FROM `BareMetal2ChassisVO` b
JOIN `ClusterEO` c ON c.`uuid` = b.`clusterUuid` AND c.`deleted` IS NULL
WHERE c.`serverPoolUuid` IS NOT NULL
ON DUPLICATE KEY UPDATE
    `lastOpDate` = `lastOpDate`;
```

Note: `BareMetal2ChassisVO` has no `deleted` column in Phase 1 schema (BM2ChassisAO does not extend a deletable base). If a later change adds soft-delete, add `AND b.deleted IS NULL`.

- [ ] **Step 2: Dry-run + verify**

```bash
mysql -u root zstack_u27_test -e "SELECT COUNT(*) FROM BareMetal2ChassisVO;"
mysql -u root zstack_u27_test < conf/db/upgrade/V5.5.18.2__schema.sql
mysql -u root zstack_u27_test -e "SELECT COUNT(*) FROM PhysicalServerVO WHERE description LIKE 'migrated from BM2%';"
```

Expected: counts equal. 216 snapshot has 0 BM2 chassis — both should be 0.

---

## Task 6: Block 1c — PhysicalServerVO from NativeHostVO

**Why:** Container NativeHost rows live in HostEO (HostEO is the EO; NativeHostVO extends HostVO which extends HostAO). We need them as PhysicalServer too for the CONTAINER_HOST role.

**Files:**
- Modify: `conf/db/upgrade/V5.5.18.2__schema.sql` (replace "Block 1c goes here")

- [ ] **Step 1: Write the block**

```sql
-- NativeHost rows live in HostEO joined with NativeHostVO on uuid.
-- hypervisorType for NativeHost is 'Container' in practice; we match via
-- NativeHostVO.uuid directly to avoid coupling to hypervisorType string.
INSERT INTO `PhysicalServerVO`
    (`uuid`, `name`, `description`, `zoneUuid`, `poolUuid`, `managementIp`,
     `architecture`, `state`, `status`, `powerStatus`, `createDate`, `lastOpDate`)
SELECT
    MD5(CONCAT(h.`uuid`, '-ps'))                           AS `uuid`,
    h.`name`                                               AS `name`,
    CONCAT('migrated from NativeHost ', h.`uuid`)          AS `description`,
    h.`zoneUuid`                                           AS `zoneUuid`,
    c.`serverPoolUuid`                                     AS `poolUuid`,
    h.`managementIp`                                       AS `managementIp`,
    h.`architecture`                                       AS `architecture`,
    h.`state`                                              AS `state`,
    h.`status`                                             AS `status`,
    'Unknown'                                              AS `powerStatus`,
    h.`createDate`                                         AS `createDate`,
    h.`lastOpDate`                                         AS `lastOpDate`
FROM `HostEO` h
JOIN `NativeHostVO` n ON n.`uuid` = h.`uuid`
JOIN `ClusterEO` c ON c.`uuid` = h.`clusterUuid` AND c.`deleted` IS NULL
WHERE h.`deleted` IS NULL
  AND c.`serverPoolUuid` IS NOT NULL
ON DUPLICATE KEY UPDATE
    `lastOpDate` = `lastOpDate`;
```

- [ ] **Step 2: Dry-run + verify**

```bash
mysql -u root zstack_u27_test -e "SELECT COUNT(*) FROM NativeHostVO;"
mysql -u root zstack_u27_test < conf/db/upgrade/V5.5.18.2__schema.sql
mysql -u root zstack_u27_test -e "SELECT COUNT(*) FROM PhysicalServerVO WHERE description LIKE 'migrated from NativeHost%';"
```

Expected: counts equal. 216 snapshot almost certainly has 0 — the site is a KVM-only env. Any NativeHost cloned in would produce a matching PS row.

---

## Task 7: Block 1.5 — PhysicalServerRoleVO backfill

**Why:** Without RoleVO rows, the HCV VIEW's `LEFT JOIN r` produces NULL serverUuid, falling through to the `COALESCE h.uuid` branch. That branch is for vcenter only (no PS row); for KVM/BM2/Container where we *have* a PS row, we need the RoleVO to route reads to the correct PSC row. Missing RoleVO here breaks `resolveServerUuidOrThrow` post-cutover.

**Files:**
- Modify: `conf/db/upgrade/V5.5.18.2__schema.sql` (replace "Block 1.5 goes here")

- [ ] **Step 1: Write the block**

```sql
-- RoleVO uuid is the standalone row identity; roleType + (serverUuid, roleType)
-- uniqueness guards against duplicates. Deterministic uuid = MD5(source + '-role').
-- schedulingMode matches NB-4 semantics:
--   KVM        → INTERNAL_SHARED
--   BAREMETAL_V2 → INTERNAL_EXCLUSIVE (AC-V2-ROLE-09 mutex)
--   CONTAINER  → INTERNAL_SHARED
-- roleUuid points back to the source entity uuid (HostEO.uuid or Chassis.uuid)
-- so reverse lookups `SELECT serverUuid WHERE roleUuid = hostUuid` work.

-- KVM_HOST role
INSERT INTO `PhysicalServerRoleVO`
    (`uuid`, `serverUuid`, `roleType`, `roleUuid`, `schedulingMode`,
     `createDate`, `lastOpDate`)
SELECT
    MD5(CONCAT(h.`uuid`, '-role-kvm'))                     AS `uuid`,
    MD5(CONCAT(h.`uuid`, '-ps'))                           AS `serverUuid`,
    'KVM_HOST'                                             AS `roleType`,
    h.`uuid`                                               AS `roleUuid`,
    'INTERNAL_SHARED'                                      AS `schedulingMode`,
    h.`createDate`                                         AS `createDate`,
    h.`lastOpDate`                                         AS `lastOpDate`
FROM `HostEO` h
WHERE h.`deleted` IS NULL
  AND h.`hypervisorType` = 'KVM'
  AND EXISTS (SELECT 1 FROM `PhysicalServerVO` p WHERE p.`uuid` = MD5(CONCAT(h.`uuid`, '-ps')))
ON DUPLICATE KEY UPDATE
    `lastOpDate` = `lastOpDate`;

-- BAREMETAL_V2 role
INSERT INTO `PhysicalServerRoleVO`
    (`uuid`, `serverUuid`, `roleType`, `roleUuid`, `schedulingMode`,
     `createDate`, `lastOpDate`)
SELECT
    MD5(CONCAT(b.`uuid`, '-role-bm2'))                     AS `uuid`,
    MD5(CONCAT(b.`uuid`, '-ps'))                           AS `serverUuid`,
    'BAREMETAL_V2'                                         AS `roleType`,
    b.`uuid`                                               AS `roleUuid`,
    'INTERNAL_EXCLUSIVE'                                   AS `schedulingMode`,
    b.`createDate`                                         AS `createDate`,
    b.`lastOpDate`                                         AS `lastOpDate`
FROM `BareMetal2ChassisVO` b
WHERE EXISTS (SELECT 1 FROM `PhysicalServerVO` p WHERE p.`uuid` = MD5(CONCAT(b.`uuid`, '-ps')))
ON DUPLICATE KEY UPDATE
    `lastOpDate` = `lastOpDate`;

-- CONTAINER_HOST role
INSERT INTO `PhysicalServerRoleVO`
    (`uuid`, `serverUuid`, `roleType`, `roleUuid`, `schedulingMode`,
     `createDate`, `lastOpDate`)
SELECT
    MD5(CONCAT(h.`uuid`, '-role-container'))               AS `uuid`,
    MD5(CONCAT(h.`uuid`, '-ps'))                           AS `serverUuid`,
    'CONTAINER_HOST'                                       AS `roleType`,
    h.`uuid`                                               AS `roleUuid`,
    'INTERNAL_SHARED'                                      AS `schedulingMode`,
    h.`createDate`                                         AS `createDate`,
    h.`lastOpDate`                                         AS `lastOpDate`
FROM `HostEO` h
JOIN `NativeHostVO` n ON n.`uuid` = h.`uuid`
WHERE h.`deleted` IS NULL
  AND EXISTS (SELECT 1 FROM `PhysicalServerVO` p WHERE p.`uuid` = MD5(CONCAT(h.`uuid`, '-ps')))
ON DUPLICATE KEY UPDATE
    `lastOpDate` = `lastOpDate`;
```

- [ ] **Step 2: Dry-run + verify**

```bash
mysql -u root zstack_u27_test < conf/db/upgrade/V5.5.18.2__schema.sql
mysql -u root zstack_u27_test -e "
SELECT roleType, COUNT(*) FROM PhysicalServerRoleVO GROUP BY roleType;
SELECT COUNT(*) FROM PhysicalServerVO p LEFT JOIN PhysicalServerRoleVO r ON r.serverUuid = p.uuid WHERE r.uuid IS NULL;"
```

Expected: row count matches PS count grouped by source type. "Zero orphan PS without any role" unless a test case intentionally creates one.

- [ ] **Step 3: Verify idx_role_uuid_type usage**

```bash
mysql -u root zstack_u27_test -e "
EXPLAIN SELECT r.serverUuid FROM PhysicalServerRoleVO r WHERE r.roleUuid = '1136f3d3278f42ffb24fe66186242e0b' AND r.roleType='KVM_HOST';"
```

Expected: `key: idx_role_uuid_type`, `ref: const,const`. (Index created in U27.)

---

## Task 8: Block 1.6 — ResourceVO + AccountResourceRefVO registration

**Why:** AC-V2-MIG-02 requires every migrated PS be discoverable via `QueryPhysicalServerMsg`, which filters through `AccountResourceRefVO`. Without these rows, admin can `APIQueryPhysicalServerMsg` but will see zero migrated rows.

**Files:**
- Modify: `conf/db/upgrade/V5.5.18.2__schema.sql` (replace "Block 1.6 goes here")

- [ ] **Step 1: Write the block**

```sql
-- ResourceVO rows for every PS created by Blocks 1a-1c. PhysicalServerVO extends
-- ResourceVO via InheritanceType.JOINED, so ResourceVO is the parent table.
-- resourceType = 'PhysicalServerVO'; concreteResourceType = fully-qualified class.
INSERT INTO `ResourceVO`
    (`uuid`, `resourceName`, `resourceType`, `concreteResourceType`)
SELECT
    p.`uuid`                                           AS `uuid`,
    p.`name`                                           AS `resourceName`,
    'PhysicalServerVO'                                 AS `resourceType`,
    'org.zstack.header.server.PhysicalServerVO'        AS `concreteResourceType`
FROM `PhysicalServerVO` p
ON DUPLICATE KEY UPDATE
    `resourceName` = VALUES(`resourceName`);

-- AccountResourceRefVO: hardcoded admin owner per NB-15. Admin UUID is
-- '36c27e8ff05c4780bf6d2fa65700f22e' (zstack default admin). INSERT IGNORE
-- avoids duplicate-key collision on the underlying UNIQUE(resourceUuid, accountUuid)
-- pair (implicit via IDENTITY id, see ZStack conventions).
-- permission = 2 (RESOURCE_PERMISSION_WRITE).
INSERT IGNORE INTO `AccountResourceRefVO`
    (`accountUuid`, `ownerAccountUuid`, `resourceUuid`, `resourceType`,
     `concreteResourceType`, `permission`, `isShared`, `createDate`, `lastOpDate`)
SELECT
    '36c27e8ff05c4780bf6d2fa65700f22e'                 AS `accountUuid`,
    '36c27e8ff05c4780bf6d2fa65700f22e'                 AS `ownerAccountUuid`,
    p.`uuid`                                           AS `resourceUuid`,
    'PhysicalServerVO'                                 AS `resourceType`,
    'org.zstack.header.server.PhysicalServerVO'        AS `concreteResourceType`,
    2                                                  AS `permission`,
    0                                                  AS `isShared`,
    NOW()                                              AS `createDate`,
    NOW()                                              AS `lastOpDate`
FROM `PhysicalServerVO` p
WHERE NOT EXISTS (
    SELECT 1 FROM `AccountResourceRefVO` a
    WHERE a.`resourceUuid` = p.`uuid`
);
```

Note on `INSERT IGNORE` + `WHERE NOT EXISTS`: the `NOT EXISTS` makes the block idempotent even when the target table has no `UNIQUE` constraint covering `(resourceUuid, accountUuid)`. `INSERT IGNORE` is a belt-and-braces guard for any constraint that might be added upstream.

- [ ] **Step 2: Dry-run + verify**

```bash
mysql -u root zstack_u27_test < conf/db/upgrade/V5.5.18.2__schema.sql
mysql -u root zstack_u27_test -e "
SELECT COUNT(*) AS ps_count FROM PhysicalServerVO;
SELECT COUNT(*) AS resource_count FROM ResourceVO WHERE resourceType='PhysicalServerVO';
SELECT COUNT(*) AS acct_ref_count FROM AccountResourceRefVO WHERE resourceType='PhysicalServerVO';"
```

Expected: all three counts equal.

---

## Task 9: Block 8 — vcenter half-migration

**Why:** vcenter ESXi hosts stay as-is (option C) — they live in HostEO with `hypervisorType='ESX'` but never get a PhysicalServerVO partner (see U27 VIEW note lines 80-81). Their PSC row is the only thing the HCV VIEW's `COALESCE` fallback depends on.

**Files:**
- Modify: `conf/db/upgrade/V5.5.18.2__schema.sql` (replace "Block 8 goes here")

- [ ] **Step 1: Write the block**

```sql
-- Migrate capacity rows for vcenter ESXi hosts from the pre-U27 backup table
-- into PhysicalServerCapacityVO with uuid = ESXHostVO.uuid (NOT MD5-salted).
-- This feeds the HCV VIEW COALESCE(r.serverUuid, h.uuid) fallback for hosts
-- lacking a RoleVO (vcenter option C half-migration). Governance columns
-- default to 1.0 ratios / 0 reserved / 'Ready' state per compat PRD §2.3 Step 8.
INSERT INTO `PhysicalServerCapacityVO`
    (`uuid`, `totalMemory`, `totalCpu`, `cpuNum`, `cpuSockets`, `cpuCoreNum`,
     `availableMemory`, `availableCpu`, `totalPhysicalMemory`,
     `availablePhysicalMemory`, `cpuOverprovisioningRatio`,
     `memoryOverprovisioningRatio`, `reservedMemory`, `totalDisk`,
     `availableDisk`, `capacityState`, `createDate`, `lastOpDate`)
SELECT
    hcb.`uuid`                                AS `uuid`,
    hcb.`totalMemory`                         AS `totalMemory`,
    hcb.`totalCpu`                            AS `totalCpu`,
    hcb.`cpuNum`                              AS `cpuNum`,
    hcb.`cpuSockets`                          AS `cpuSockets`,
    hcb.`cpuCoreNum`                          AS `cpuCoreNum`,
    hcb.`availableMemory`                     AS `availableMemory`,
    hcb.`availableCpu`                        AS `availableCpu`,
    hcb.`totalPhysicalMemory`                 AS `totalPhysicalMemory`,
    hcb.`availablePhysicalMemory`             AS `availablePhysicalMemory`,
    1.0                                       AS `cpuOverprovisioningRatio`,
    1.0                                       AS `memoryOverprovisioningRatio`,
    0                                         AS `reservedMemory`,
    0                                         AS `totalDisk`,
    0                                         AS `availableDisk`,
    'Ready'                                   AS `capacityState`,
    NOW()                                     AS `createDate`,
    NOW()                                     AS `lastOpDate`
FROM `HostCapacityVO_backup` hcb
JOIN `ESXHostVO` e ON e.`uuid` = hcb.`uuid`
ON DUPLICATE KEY UPDATE
    `totalMemory` = VALUES(`totalMemory`),
    `totalCpu` = VALUES(`totalCpu`),
    `cpuNum` = VALUES(`cpuNum`),
    `cpuSockets` = VALUES(`cpuSockets`),
    `cpuCoreNum` = VALUES(`cpuCoreNum`),
    `availableMemory` = VALUES(`availableMemory`),
    `availableCpu` = VALUES(`availableCpu`),
    `totalPhysicalMemory` = VALUES(`totalPhysicalMemory`),
    `availablePhysicalMemory` = VALUES(`availablePhysicalMemory`),
    `lastOpDate` = NOW();

-- Also backfill PhysicalServerCapacityVO for KVM / NativeHost entries. Same
-- pattern, but keyed on MD5(host_uuid + '-ps'). This initialises PSC rows from
-- HCV historical values so capacity reads return non-zero immediately after
-- cutover; subsequent HostCapacityUpdater runs (Phase 2B writes) will keep
-- them current.
INSERT INTO `PhysicalServerCapacityVO`
    (`uuid`, `totalMemory`, `totalCpu`, `cpuNum`, `cpuSockets`, `cpuCoreNum`,
     `availableMemory`, `availableCpu`, `totalPhysicalMemory`,
     `availablePhysicalMemory`, `cpuOverprovisioningRatio`,
     `memoryOverprovisioningRatio`, `reservedMemory`, `totalDisk`,
     `availableDisk`, `capacityState`, `createDate`, `lastOpDate`)
SELECT
    MD5(CONCAT(hcb.`uuid`, '-ps'))            AS `uuid`,
    hcb.`totalMemory`, hcb.`totalCpu`, hcb.`cpuNum`, hcb.`cpuSockets`, hcb.`cpuCoreNum`,
    hcb.`availableMemory`, hcb.`availableCpu`,
    hcb.`totalPhysicalMemory`, hcb.`availablePhysicalMemory`,
    1.0, 1.0, 0, 0, 0, 'Ready',
    NOW(), NOW()
FROM `HostCapacityVO_backup` hcb
JOIN `PhysicalServerVO` p ON p.`uuid` = MD5(CONCAT(hcb.`uuid`, '-ps'))
ON DUPLICATE KEY UPDATE
    `totalMemory` = VALUES(`totalMemory`),
    `totalCpu` = VALUES(`totalCpu`),
    `cpuNum` = VALUES(`cpuNum`),
    `cpuSockets` = VALUES(`cpuSockets`),
    `cpuCoreNum` = VALUES(`cpuCoreNum`),
    `availableMemory` = VALUES(`availableMemory`),
    `availableCpu` = VALUES(`availableCpu`),
    `totalPhysicalMemory` = VALUES(`totalPhysicalMemory`),
    `availablePhysicalMemory` = VALUES(`availablePhysicalMemory`),
    `lastOpDate` = NOW();
```

- [ ] **Step 2: Dry-run + verify VIEW returns pre-migration row counts**

```bash
mysql -u root zstack_u27_test < conf/db/upgrade/V5.5.18.2__schema.sql
mysql -u root zstack_u27_test -e "
SELECT COUNT(*) AS backup_rows FROM HostCapacityVO_backup;
SELECT COUNT(*) AS psc_rows FROM PhysicalServerCapacityVO;
SELECT COUNT(*) AS view_rows FROM HostCapacityVO;"
```

Expected: `view_rows = backup_rows` (every original HCV row now visible through the VIEW via KVM path or vcenter COALESCE path). 216 snapshot: 10 HCV backup rows → 10 view rows.

- [ ] **Step 3: Data fidelity spot-check**

```bash
mysql -u root zstack_u27_test -e "
SELECT h.uuid, h.availableCpu AS old_cpu, v.availableCpu AS new_cpu
FROM HostCapacityVO_backup h
LEFT JOIN HostCapacityVO v ON v.uuid = h.uuid
WHERE h.availableCpu != COALESCE(v.availableCpu, -1);"
```

Expected: zero rows (every historical row reflected faithfully through the VIEW).

---

## Task 10: Block B1 — PoolRef backfill from BM2 ClusterRef

**Why:** The Phase-1 `BareMetal2ProvisionNetworkClusterRefVO` recorded (network, cluster) pairs. Post-U27, the unified model keys off (network, pool). Without this backfill, previously-attached provision networks lose their associations.

**Files:**
- Modify: `conf/db/upgrade/V5.5.18.2__schema.sql` (replace "Block B1 goes here")

- [ ] **Step 1: Write the block**

```sql
-- Translate BM2 (network, cluster) history into (network, pool) via
-- ClusterEO.serverPoolUuid lookup (populated by Blocks 0a/0b). DISTINCT
-- dedupes when multiple clusters sharing the same pool both attach the same
-- network. INSERT IGNORE plus UNIQUE(networkUuid, poolUuid) enforces idempotency.
-- Clusters whose serverPoolUuid is still NULL (should be impossible after 0b
-- but be defensive) are skipped; AC-V2-MIG-11 requires the runbook log them.
INSERT IGNORE INTO `PhysicalServerProvisionNetworkPoolRefVO`
    (`networkUuid`, `poolUuid`, `createDate`, `lastOpDate`)
SELECT DISTINCT
    ref.`networkUuid`                                 AS `networkUuid`,
    c.`serverPoolUuid`                                AS `poolUuid`,
    ref.`createDate`                                  AS `createDate`,
    ref.`lastOpDate`                                  AS `lastOpDate`
FROM `BareMetal2ProvisionNetworkClusterRefVO` ref
JOIN `ClusterEO` c ON c.`uuid` = ref.`clusterUuid` AND c.`deleted` IS NULL
WHERE c.`serverPoolUuid` IS NOT NULL;
```

- [ ] **Step 2: Dry-run + verify**

```bash
mysql -u root zstack_u27_test -e "
SELECT COUNT(*) AS bm2_ref_count FROM BareMetal2ProvisionNetworkClusterRefVO;
SELECT COUNT(DISTINCT networkUuid, c.serverPoolUuid)
FROM BareMetal2ProvisionNetworkClusterRefVO bm2
JOIN ClusterEO c ON c.uuid = bm2.clusterUuid
WHERE c.serverPoolUuid IS NOT NULL;"
mysql -u root zstack_u27_test < conf/db/upgrade/V5.5.18.2__schema.sql
mysql -u root zstack_u27_test -e "SELECT COUNT(*) FROM PhysicalServerProvisionNetworkPoolRefVO;"
```

Expected: PoolRef count equals the distinct-pair preflight count. 216 snapshot: 1 BM2 ClusterRef row, whose cluster has serverPoolUuid (now) — expect 1 PoolRef row.

- [ ] **Step 3: Verify skipped-row logging requirement**

If any BM2 ClusterRef row had `c.serverPoolUuid IS NULL` (none expected after 0b), capture them for the U29 runbook:

```bash
mysql -u root zstack_u27_test -e "
SELECT ref.networkUuid, ref.clusterUuid
FROM BareMetal2ProvisionNetworkClusterRefVO ref
LEFT JOIN ClusterEO c ON c.uuid = ref.clusterUuid
WHERE c.serverPoolUuid IS NULL OR c.uuid IS NULL;"
```

Expected: empty result set. Record the query in U29 as the operator-facing diagnostic.

---

## Task 11: Block B2 — BareMetal2ProvisionNetworkVO → VIEW

**Why:** NB-4 BLOCKER B8 says BM2's provision-network data model must unify into `PhysicalServerProvisionNetworkVO`. Copy the rows in, rename the source table, then create a VIEW over the unified table so `BareMetal2ProvisionNetworkVO` reads still work zero-code-change.

**Files:**
- Modify: `conf/db/upgrade/V5.5.18.2__schema.sql` (replace "Block B2 goes here")

- [ ] **Step 1: Write the block**

```sql
-- Data copy into the unified table. type='GATEWAY_PXE' aligns with Phase 1
-- default for the `type` column. dhcpRangeNetworkCidr lives only on BM2; add
-- it as a nullable column on the unified table so the VIEW is a one-to-one
-- column projection (keeps Hibernate's read/write SELECT clause happy).
ALTER TABLE `PhysicalServerProvisionNetworkVO`
    ADD COLUMN IF NOT EXISTS `dhcpRangeNetworkCidr` VARCHAR(64) DEFAULT NULL;

INSERT INTO `PhysicalServerProvisionNetworkVO`
    (`uuid`, `name`, `description`, `zoneUuid`, `type`, `dhcpInterface`,
     `dhcpRangeStartIp`, `dhcpRangeEndIp`, `dhcpRangeNetmask`, `dhcpRangeGateway`,
     `dhcpRangeNetworkCidr`, `state`, `createDate`, `lastOpDate`)
SELECT
    bpn.`uuid`, bpn.`name`, bpn.`description`, bpn.`zoneUuid`, 'GATEWAY_PXE',
    bpn.`dhcpInterface`, bpn.`dhcpRangeStartIp`, bpn.`dhcpRangeEndIp`,
    bpn.`dhcpRangeNetmask`, bpn.`dhcpRangeGateway`, bpn.`dhcpRangeNetworkCidr`,
    bpn.`state`, bpn.`createDate`, bpn.`lastOpDate`
FROM `BareMetal2ProvisionNetworkVO` bpn
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `description` = VALUES(`description`),
    `dhcpInterface` = VALUES(`dhcpInterface`),
    `dhcpRangeStartIp` = VALUES(`dhcpRangeStartIp`),
    `dhcpRangeEndIp` = VALUES(`dhcpRangeEndIp`),
    `dhcpRangeNetmask` = VALUES(`dhcpRangeNetmask`),
    `dhcpRangeGateway` = VALUES(`dhcpRangeGateway`),
    `dhcpRangeNetworkCidr` = VALUES(`dhcpRangeNetworkCidr`),
    `state` = VALUES(`state`),
    `lastOpDate` = NOW();

-- BM2 instance FK currently points at BareMetal2ProvisionNetworkVO.uuid. After
-- we RENAME it away, the FK becomes stale — drop it so the RENAME doesn't
-- cascade-fail. The FK is re-added as a VIEW-to-VIEW semantic check in the
-- application layer (BareMetal2InstanceProvisionNicVO; future phase if needed).
ALTER TABLE `BareMetal2InstanceProvisionNicVO`
    DROP FOREIGN KEY IF EXISTS `fkBareMetal2InstanceProvisionNicVOBareMetal2ProvisionNetworkVO`;

-- Preserve raw data for rollback. Guard with EXISTS check: only rename when
-- the source is still a BASE TABLE (not a VIEW from a prior run).
-- MariaDB/MySQL has no native IF NOT EXISTS for RENAME, so use prepared stmt.
SET @is_base_table := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'BareMetal2ProvisionNetworkVO'
      AND TABLE_TYPE = 'BASE TABLE'
);

SET @sql := IF(@is_base_table = 1,
    'RENAME TABLE `BareMetal2ProvisionNetworkVO` TO `BareMetal2ProvisionNetworkVO_backup`',
    'DO 0'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Updatable VIEW over the unified table. WITH CHECK OPTION so writes through
-- the VIEW to non-matching rows (type != 'GATEWAY_PXE') fail loudly.
-- ALGORITHM=MERGE inlines the VIEW into caller SELECTs (consistent with U27 HCV).
CREATE OR REPLACE
    ALGORITHM = MERGE
    SQL SECURITY INVOKER
VIEW `BareMetal2ProvisionNetworkVO` AS
SELECT
    `uuid`, `name`, `description`, `zoneUuid`,
    `dhcpInterface`, `dhcpRangeStartIp`, `dhcpRangeEndIp`,
    `dhcpRangeNetmask`, `dhcpRangeGateway`, `dhcpRangeNetworkCidr`,
    `state`, `createDate`, `lastOpDate`
FROM `PhysicalServerProvisionNetworkVO`
WHERE `type` = 'GATEWAY_PXE'
WITH CHECK OPTION;
```

- [ ] **Step 2: Dry-run + verify VIEW read symmetry**

```bash
mysql -u root zstack_u27_test -e "SELECT COUNT(*) FROM BareMetal2ProvisionNetworkVO_backup;" 2>/dev/null || echo "already migrated"
mysql -u root zstack_u27_test < conf/db/upgrade/V5.5.18.2__schema.sql
mysql -u root zstack_u27_test -e "
SELECT COUNT(*) AS view_rows FROM BareMetal2ProvisionNetworkVO;
SELECT COUNT(*) AS backup_rows FROM BareMetal2ProvisionNetworkVO_backup;
SELECT COUNT(*) AS unified_rows FROM PhysicalServerProvisionNetworkVO WHERE type='GATEWAY_PXE';"
```

Expected: `view_rows == backup_rows == unified_rows`.

- [ ] **Step 3: Updatable-VIEW smoke test**

```bash
mysql -u root zstack_u27_test -e "
INSERT INTO BareMetal2ProvisionNetworkVO (uuid, name, zoneUuid, state) VALUES ('test-uuid-bm2-view', 'smoke', (SELECT uuid FROM ZoneEO LIMIT 1), 'Enabled');
SELECT type FROM PhysicalServerProvisionNetworkVO WHERE uuid = 'test-uuid-bm2-view';
DELETE FROM BareMetal2ProvisionNetworkVO WHERE uuid = 'test-uuid-bm2-view';"
```

Expected: INSERT succeeds, `type='GATEWAY_PXE'` (inherited via WITH CHECK OPTION default), DELETE removes row from base table.

---

## Task 12: Block B3 — BareMetal2ProvisionNetworkClusterRefVO → VIEW

**Why:** AC-V2-MIG-09: BM2 ClusterRef reads (24 call sites in BM2 module) keep working without code changes. VIEW projects `PhysicalServerProvisionNetworkPoolRefVO` back into (network, cluster) pairs via `ClusterVO.serverPoolUuid`.

**Files:**
- Modify: `conf/db/upgrade/V5.5.18.2__schema.sql` (replace "Block B3 goes here")

- [ ] **Step 1: Write the block**

```sql
-- Drop the original table after Block B1 already migrated its content into
-- PoolRef. Same RENAME-guard pattern as B2.
SET @is_base_table := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'BareMetal2ProvisionNetworkClusterRefVO'
      AND TABLE_TYPE = 'BASE TABLE'
);

SET @sql := IF(@is_base_table = 1,
    'RENAME TABLE `BareMetal2ProvisionNetworkClusterRefVO` TO `BareMetal2ProvisionNetworkClusterRefVO_backup`',
    'DO 0'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- VIEW recomputes (network, cluster) pairs on demand by joining PoolRef with
-- ClusterEO on serverPoolUuid. A single PoolRef row can map back to N cluster
-- rows (all clusters that share the same pool) — this is intentional and
-- matches the unified model.
--
-- MERGE is used to keep the JOIN inlined into caller WHERE filters. This VIEW
-- is read-only; BM2 write paths are redirected to PoolRef by U23.
CREATE OR REPLACE
    ALGORITHM = MERGE
    SQL SECURITY INVOKER
VIEW `BareMetal2ProvisionNetworkClusterRefVO` AS
SELECT
    pr.`id`                                           AS `id`,
    pr.`networkUuid`                                  AS `networkUuid`,
    c.`uuid`                                          AS `clusterUuid`,
    pr.`createDate`                                   AS `createDate`,
    pr.`lastOpDate`                                   AS `lastOpDate`
FROM `PhysicalServerProvisionNetworkPoolRefVO` pr
JOIN `ClusterEO` c
    ON c.`serverPoolUuid` = pr.`poolUuid`
   AND c.`deleted` IS NULL;
```

- [ ] **Step 2: Dry-run + verify BM2 code-path read symmetry**

```bash
mysql -u root zstack_u27_test < conf/db/upgrade/V5.5.18.2__schema.sql
mysql -u root zstack_u27_test -e "
SELECT COUNT(*) AS backup_rows FROM BareMetal2ProvisionNetworkClusterRefVO_backup;
SELECT COUNT(*) AS view_rows FROM BareMetal2ProvisionNetworkClusterRefVO;"
```

Expected: `view_rows >= backup_rows`. View can have *more* rows than backup if multiple clusters share one pool; that's NB-4 expansion semantics.

- [ ] **Step 3: Cross-check BM2 module's 24 call sites simulate successfully**

Pick a representative call site from `BareMetal2ChassisApiInterceptor.java:140-142`:

```bash
mysql -u root zstack_u27_test -e "
SELECT networkUuid FROM BareMetal2ProvisionNetworkClusterRefVO WHERE clusterUuid = (SELECT clusterUuid FROM BareMetal2ProvisionNetworkClusterRefVO_backup LIMIT 1);"
```

Expected: returns the same `networkUuid` that was present in the backup for that cluster.

---

## Task 13: End-to-end dry-run on cloned 216 DB

**Files:** none modified; verification only.

- [ ] **Step 1: Reset local DB to U27 baseline**

```bash
# Start from zstack_u27_test, which already has U27 applied.
# If it's been polluted by prior U28 runs, rebuild it:
mysql -u root -e "DROP DATABASE IF EXISTS zstack_u28_test; CREATE DATABASE zstack_u28_test;"
mysqldump -u root --no-data zstack_u27_test | mysql -u root zstack_u28_test
mysqldump -u root zstack_u27_test --where='1=1' --no-create-info \
    --ignore-table=zstack_u27_test.HostCapacityVO | mysql -u root zstack_u28_test 2>&1 | head -5
# Re-copy HostCapacityVO_backup content
mysql -u root zstack_u28_test -e "
INSERT INTO HostCapacityVO_backup SELECT * FROM zstack_u27_test.HostCapacityVO_backup;"
```

- [ ] **Step 2: Apply the full migration file**

```bash
time mysql -u root zstack_u28_test < conf/db/upgrade/V5.5.18.2__schema.sql 2>&1 | tee /tmp/u28-apply.log
```

Expected: exit 0, no errors in `/tmp/u28-apply.log`, wall time < 5 s on 216-sized snapshot.

- [ ] **Step 3: Re-apply (idempotency)**

```bash
mysql -u root zstack_u28_test < conf/db/upgrade/V5.5.18.2__schema.sql 2>&1 | tee /tmp/u28-reapply.log
# Compare row counts before / after
mysql -u root zstack_u28_test -e "
SELECT 'ServerPoolVO', COUNT(*) FROM ServerPoolVO
UNION ALL SELECT 'PhysicalServerVO', COUNT(*) FROM PhysicalServerVO
UNION ALL SELECT 'PhysicalServerRoleVO', COUNT(*) FROM PhysicalServerRoleVO
UNION ALL SELECT 'PhysicalServerCapacityVO', COUNT(*) FROM PhysicalServerCapacityVO
UNION ALL SELECT 'ResourceVO (PS)', COUNT(*) FROM ResourceVO WHERE resourceType='PhysicalServerVO'
UNION ALL SELECT 'AccountResourceRefVO (PS)', COUNT(*) FROM AccountResourceRefVO WHERE resourceType='PhysicalServerVO'
UNION ALL SELECT 'PoolRef', COUNT(*) FROM PhysicalServerProvisionNetworkPoolRefVO
UNION ALL SELECT 'BM2PN view', COUNT(*) FROM BareMetal2ProvisionNetworkVO
UNION ALL SELECT 'BM2PNClusterRef view', COUNT(*) FROM BareMetal2ProvisionNetworkClusterRefVO
UNION ALL SELECT 'HostCapacityVO view', COUNT(*) FROM HostCapacityVO;"
```

Expected: second-run row counts byte-identical to first-run counts.

- [ ] **Step 4: VIEW-exercise the hot-path read**

```bash
mysql -u root zstack_u28_test -e "
EXPLAIN SELECT * FROM HostCapacityVO WHERE uuid = '1136f3d3278f42ffb24fe66186242e0b';"
```

Expected: `const` on HostEO PK, `ref` on `idx_role_uuid_type`, `eq_ref` on PSC PK. AC-CM-PERF-01 satisfied.

- [ ] **Step 5: Zero-regression compare with U27-only baseline**

```bash
mysql -u root zstack_u27_test -e "SELECT uuid, availableCpu, availableMemory FROM HostCapacityVO ORDER BY uuid" > /tmp/u27-hcv.txt
mysql -u root zstack_u28_test -e "SELECT uuid, availableCpu, availableMemory FROM HostCapacityVO ORDER BY uuid" > /tmp/u28-hcv.txt
diff /tmp/u27-hcv.txt /tmp/u28-hcv.txt
```

Expected: `diff` produces no output (every HCV row visible post-U28 matches its pre-U28 value). **This is the AC-V2-MIG-04 gate.**

---

## Task 14: Commit + push

- [ ] **Step 1: Sanity re-read final script**

```bash
wc -l conf/db/upgrade/V5.5.18.2__schema.sql
grep -c 'ON DUPLICATE KEY UPDATE\|INSERT IGNORE\|CREATE OR REPLACE\|IF NOT EXISTS' conf/db/upgrade/V5.5.18.2__schema.sql
```

Expected: ~250 lines, every mutation protected by an idempotency guard.

- [ ] **Step 2: Final dry-run** (confirm nothing has drifted since Task 13)

```bash
mysql -u root -e "DROP DATABASE IF EXISTS zstack_u28_final; CREATE DATABASE zstack_u28_final;"
mysqldump -u root zstack_u27_test | mysql -u root zstack_u28_final
mysql -u root zstack_u28_final < conf/db/upgrade/V5.5.18.2__schema.sql
echo $?  # must be 0
```

- [ ] **Step 3: Commit with zcommit**

```bash
cd /home/mj/zstack-workspace/zstack-unifi-host
source /home/mj/zstack-workspace/scripts/zcommit.sh
zcommit feature server "" "U28 Flyway data migration: ServerPool init + PS/Role/Capacity backfill + BM2 VIEW-ization" "
Adds V5.5.18.2__schema.sql covering 11 idempotent blocks per capacity PRD
§2.3 / provision PRD §2.2 / compat PRD §2.3:

Block 0a  - ServerPool 1:1 per BM2-bearing cluster (NB-4)
Block 0b  - ServerPool shared per non-BM2 zone + ClusterEO.serverPoolUuid
Block 1a  - PhysicalServerVO from KVMHostVO
Block 1b  - PhysicalServerVO from BareMetal2ChassisVO
Block 1c  - PhysicalServerVO from NativeHostVO
Block 1.5 - PhysicalServerRoleVO (KVM_HOST / BAREMETAL_V2 / CONTAINER_HOST)
Block 1.6 - ResourceVO + AccountResourceRefVO registration (admin owner, NB-15)
Block 8   - PhysicalServerCapacityVO vcenter half-migration + KVM/Container
            PSC seed from HostCapacityVO_backup (U6/U27 COALESCE path)
Block B1  - PhysicalServerProvisionNetworkPoolRefVO backfill from BM2 history
Block B2  - BareMetal2ProvisionNetworkVO -> MERGE VIEW (WITH CHECK OPTION)
Block B3  - BareMetal2ProvisionNetworkClusterRefVO -> MERGE VIEW

Satisfies AC-V2-MIG-01..11. BM1 chassis explicitly out of scope (not migrated).
Dry-run green on cloned 216 MariaDB 10.11 snapshot; HCV row-count and
availableCpu/Memory diff clean against U27-only baseline.

Atomic release with U4/U5/U6/U7/U27. MN startup requires this migration or
PhysicalServerRoleVO/Capacity writes will fail via NB-24 resolveServerUuidOrThrow
for all pre-existing hosts.

MINI-22"
```

- [ ] **Step 4: Verify commit-msg hook WARN count ≤ 3**

Expected output from `zcommit`: `git commit` succeeds; any WARN count printed by the hook must be ≤ 3 (email `maximjcc@outlook.com` lacks `zstack` → 1 WARN).

- [ ] **Step 5: Push**

```bash
git push origin feature/unifi-host-dev
```

Expected: push accepted, new commit visible on `jin.ma/zstack` fork.

---

## Self-Review

**Spec coverage:**
- AC-V2-MIG-01 (idempotent) → every block uses `ON DUPLICATE KEY UPDATE` / `INSERT IGNORE` / `CREATE OR REPLACE`. Task 13 Step 3 is the explicit re-apply gate.
- AC-V2-MIG-02 (QueryPhysicalServerMsg visibility) → Block 1.6 AccountResourceRefVO.
- AC-V2-MIG-03 (QueryPhysicalServer discovers existing inventory) → Blocks 1a/1b/1c populate PS rows.
- AC-V2-MIG-04 (HCV VIEW matches pre-migration) → Task 13 Step 5 `diff` gate.
- AC-V2-MIG-05/06 (BM2 PN updatable VIEW) → Task 11 Step 3 smoke test.
- AC-V2-MIG-08 (PoolRef is the new truth) → Block B1 + Block B3 VIEW.
- AC-V2-MIG-09 (BM2 ClusterRef as VIEW) → Block B3.
- AC-V2-MIG-10 handled by U23 (Java handler) — out of scope for this plan, flagged.
- AC-V2-MIG-11 (skipped ClusterRef log) → Task 10 Step 3 diagnostic query, carried into U29 runbook.

**Placeholder scan:** no TBDs, no "add validation" stubs, every SQL block is literal.

**Type consistency:** PS uuid derivation `MD5(CONCAT(source_uuid, '-ps'))` used uniformly in Blocks 1a/1b/1c/1.5/8/B2; role uuid `-role-{type}` salt matches `-ps` pattern; pool uuid `MD5(CONCAT(cluster_or_zone, '-pool-bm2' | '-default-pool'))` stable across 0a/0b/B1/B3.

**Known open items (escalate to architect):**
1. BM1 chassis skipped (no `BaremetalChassisVO` row migrated). Confirm this is acceptable — `REVISED_Architecture_Assessment.md` says yes, but the plan should call it out in the release note that ships with U29.
2. `dhcpRangeNetworkCidr` added as nullable column on `PhysicalServerProvisionNetworkVO` — Phase 1 did not include it. Alternative (delete the BM2 Java getter in U23/U24) leaves the column off the unified schema; current approach keeps write-through Hibernate SELECT clause symmetrical. Pick one; U28 implements the ADD-COLUMN path.
3. Block 8 seeds PSC rows for KVM and Container hosts from HCV backup, meaning first post-cutover capacity reads return backup values. Subsequent Phase 2B writes overwrite them. If the atomic release rolls forward HostCapacityUpdater before the first recalculation sweep, there is a ~1-tick window where PSC values are up to one heartbeat stale. This is acceptable per compat PRD §2.3 Step 8 rationale; call out in U29 runbook.

---

## Execution Handoff

Plan complete and saved to `docs/plans/2026-04-23-001-u28-flyway-data-migration.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration. Good fit because U28 has clean task boundaries and per-block verification gates.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
