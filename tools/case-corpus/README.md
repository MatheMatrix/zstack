# Integration Case Corpus Tool

`case_corpus.py` inventories current ZStack Integration Case source, creates a
deterministic mutually exclusive shard manifest, runs exactly one assigned Case
at a time, and verifies the resulting evidence bundle.

The tool supports the repository hosts' Python 3.6 and Git 1.8 baseline and
uses only the Python standard library. It does not read environment credentials
and never deletes logs. Remote cleanup remains a separate coordinator action
after a copied evidence directory verifies locally.

## Inventory

Run from the zstack repository root with the exact nested premium checkout:

```bash
python3 tools/case-corpus/case_corpus.py inventory \
  --zstack-root . \
  --premium-root premium \
  --output /tmp/case-corpus/cases.jsonl \
  --summary /tmp/case-corpus/summary.json
```

Concrete `*Case.groovy` classes are included when their inheritance chain
reaches `SubCase`, `PremiumSubCase`, or another base implementing `Case`.
Abstract framework bases are excluded. Declared methods are a syntax scan, not
a call graph; `selected_method` is the Case entry method `test`.

## Manifest

Shard aliases and seed are explicit manifest inputs. Ordering does not affect
the output.

```bash
python3 tools/case-corpus/case_corpus.py manifest \
  --inventory /tmp/case-corpus/cases.jsonl \
  --output /tmp/case-corpus/three-machine-v1.json \
  --seed zsv-mn-corpus-v1 \
  --shard dev-machine-1 \
  --shard dev-machine-2 \
  --shard dev-machine-3
```

The algorithm is versioned HRW/Rendezvous hashing over SHA-256. Every Case has
one owner. Removing a shard leaves assignments on all surviving shards stable.
Completion state is intentionally separate from the immutable manifest, so a
run can resume by skipping Case IDs that already have verified evidence.

## Single-Case run

The build receipt must be JSON with `machine_alias`, `zstack_commit`,
`premium_commit`, `exit_code`, `command`, `started_at`, and `ended_at`. A run is
rejected unless both Git HEADs and the successful build receipt match the
manifest.

```bash
python3 tools/case-corpus/case_corpus.py run \
  --inventory /tmp/case-corpus/cases.jsonl \
  --manifest /tmp/case-corpus/three-machine-v1.json \
  --case-id zstack:org.zstack.test.integration.core.MustPassCase \
  --shard dev-machine-1 \
  --machine-alias dev-machine-1 \
  --zstack-root . \
  --premium-root premium \
  --evidence-root /tmp/zsv-mn-case-corpus/runs \
  --build-receipt /tmp/zsv-mn-case-corpus/build.json
```

The runner acquires an atomic machine lock, refuses tracked dirty files, moves
an existing `management-server.log` into a `preexisting/` boundary, executes
the inventory argv without a shell, and captures a new log plus stdout, stderr,
exit code, UTC timing, source snapshot/reference, method list, build receipt,
branch/commits, and SHA-256 checksums. A missing or empty MN log cannot be
reported as success. Network-deferred and excluded Cases are refused by default.

## Verify and cleanup boundary

After recursively copying a completed remote evidence directory to durable
local storage, run:

```bash
python3 tools/case-corpus/case_corpus.py verify /local/copied/evidence-directory
```

Only `{"verified":true,"errors":[]}` combined with runner exit zero permits
the coordinator to delete that exact Case's active remote management log.
Preexisting logs and the evidence directory remain outside that cleanup scope
unless separately archived and authorized. A failed Case or failed
verification must retain all remote evidence.
