#!/usr/bin/env python3
"""Reproducible ZStack Integration Case inventory and execution evidence."""

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path


SCHEMA_VERSION = 1
METHOD_DISCOVERY = "syntax-scan-v1"
SHARD_ALGORITHM = "hrw-sha256-v1"
NETWORK_MODULES = {
    "flatnetwork",
    "l2",
    "l3",
    "loadbalancer",
    "network",
    "networkservice",
    "ovs",
    "portforwarding",
    "securitygroup",
    "slb",
    "vdpa",
    "vhostuser",
    "vip",
    "virtualrouter",
    "vpc",
}
CLASS_DECL_RE = re.compile(
    r"^\s*(?P<abstract>abstract\s+)?class\s+(?P<class>[A-Za-z_]\w*)"
    r"(?:\s+extends\s+(?P<base>[A-Za-z_][\w.]*))?"
    r"(?:\s+implements\s+(?P<interfaces>[^\n{]+))?",
    re.MULTILINE,
)
PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*$", re.MULTILINE)
METHOD_RE = re.compile(
    r"^\s*(?:(?:public|protected|private|static|final|synchronized|abstract)\s+)*"
    r"(?:def|void|boolean|byte|short|int|long|float|double|char|String|"
    r"[A-Z][\w.<>, ?\[\]]*)\s+([A-Za-z_]\w*)\s*\(",
    re.MULTILINE,
)


class CorpusError(RuntimeError):
    """A guard failure that makes a corpus run unauditable."""


def _sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _declared_methods(source, class_name):
    ignored = {class_name, "if", "for", "while", "switch", "catch"}
    return sorted({name for name in METHOD_RE.findall(source) if name not in ignored})


def _module_for(relative_path, repository):
    parts = list(relative_path.parts)
    try:
        index = parts.index("integration") + 1
    except ValueError:
        try:
            index = parts.index("unittest") + 1
        except ValueError:
            return "unclassified"
    if repository == "premium" and index < len(parts) and parts[index] == "premium":
        index += 1
    if index >= len(parts) - 1:
        return "unclassified"
    return parts[index].lower()


def _simple_name(name):
    return name.rsplit(".", 1)[-1] if name else None


def _inheritance_index(repo_root, scan_roots):
    graph = {}
    contract_roots = {"SubCase", "PremiumSubCase"}
    for relative_root in scan_roots:
        source_root = repo_root / relative_root
        if not source_root.is_dir():
            continue
        for source_path in source_root.rglob("*.groovy"):
            source = source_path.read_text(encoding="utf-8", errors="replace")
            for declaration in CLASS_DECL_RE.finditer(source):
                class_name = declaration.group("class")
                base_class = _simple_name(declaration.group("base"))
                if base_class:
                    graph[class_name] = base_class
                interfaces = declaration.group("interfaces") or ""
                implemented = {
                    _simple_name(value.strip())
                    for value in interfaces.split(",")
                    if value.strip()
                }
                if "Case" in implemented:
                    contract_roots.add(class_name)
    return graph, contract_roots


def _case_contract_root(
    class_name, graph, contract_roots
):
    current = class_name
    visited = set()
    while current and current not in visited:
        if current in contract_roots:
            return current
        visited.add(current)
        current = graph.get(current)
    return None


def _record_for(
    source_path,
    repo_root,
    repository,
    repository_commit,
    maven_module,
    inheritance_graph,
    contract_roots,
):
    source = source_path.read_text(encoding="utf-8", errors="replace")
    package_match = PACKAGE_RE.search(source)
    declarations = list(CLASS_DECL_RE.finditer(source))
    class_match = next(
        (match for match in declarations if match.group("class") == source_path.stem),
        declarations[0] if declarations else None,
    )
    if class_match is None or package_match is None or class_match.group("abstract"):
        return None

    class_name = class_match.group("class")
    base_class = _simple_name(class_match.group("base"))
    contract_root = _case_contract_root(class_name, inheritance_graph, contract_roots)
    if contract_root is None:
        return None
    package = package_match.group(1)
    fqcn = f"{package}.{class_name}"
    relative_path = source_path.relative_to(repo_root)
    module = _module_for(relative_path, repository)
    markers = [
        marker
        for marker in ("Deprecated", "SkipTestSuite", "Ignore")
        if re.search(rf"@{marker}\b", source)
    ]
    path_tokens = {part.lower() for part in relative_path.parts}
    network_deferred = module in NETWORK_MODULES or bool(path_tokens & NETWORK_MODULES)

    return {
        "schema_version": SCHEMA_VERSION,
        "id": f"{repository}:{fqcn}",
        "repository": repository,
        "repository_commit": repository_commit,
        "source_path": relative_path.as_posix(),
        "source_sha256": _sha256(source_path),
        "package": package,
        "class_name": class_name,
        "fully_qualified_class": fqcn,
        "base_class": base_class,
        "case_contract_root": contract_root,
        "declared_methods": _declared_methods(source, class_name),
        "selected_method": "test",
        "method_discovery": METHOD_DISCOVERY,
        "module": module,
        "network_deferred": network_deferred,
        "exclusion_markers": markers,
        "maven_module": maven_module,
        "run_requirements": {
            "working_directory": maven_module,
            "argv": [
                "mvn",
                "test",
                f"-Dtest={fqcn}",
                "-DskipJacoco=true",
            ],
            "serial_per_machine": True,
            "management_log": "management-server.log",
        },
    }


def inventory_records(
    zstack_root,
    premium_root,
    zstack_commit,
    premium_commit,
):
    """Return canonical records for concrete Case classes in both repositories."""

    specs = [
        (
            Path(zstack_root),
            "zstack",
            zstack_commit,
            "test/src/test/groovy",
            "test",
            ["test/src/test/groovy", "testlib/src/main/java"],
        )
    ]
    if premium_root is not None:
        specs.append(
            (
                Path(premium_root),
                "premium",
                premium_commit or "",
                "test-premium/src/test/groovy",
                "test-premium",
                ["test-premium/src/test/groovy", "testlib-premium/src/main/java"],
            )
        )

    records = []
    for repo_root, repository, commit, source_root, maven_module, scan_roots in specs:
        inheritance_graph, contract_roots = _inheritance_index(repo_root, scan_roots)
        search_root = repo_root / source_root
        if not search_root.is_dir():
            continue
        for source_path in search_root.rglob("*Case.groovy"):
            record = _record_for(
                source_path,
                repo_root,
                repository,
                commit,
                maven_module,
                inheritance_graph,
                contract_roots,
            )
            if record is not None:
                records.append(record)
    return sorted(records, key=lambda record: record["id"])


def _canonical_json(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _write_text(path, content):
    with Path(path).open("w", encoding="utf-8", newline="\n") as stream:
        stream.write(content)


def write_inventory(records, output_path):
    """Write stable JSONL ordered by Case identifier."""

    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    content = "".join(
        f"{_canonical_json(record)}\n"
        for record in sorted(records, key=lambda record: record["id"])
    )
    _write_text(output, content)


def _inventory_bytes(records):
    return "".join(
        f"{_canonical_json(record)}\n"
        for record in sorted(records, key=lambda record: record["id"])
    ).encode("utf-8")


def _hrw_score(case_id, shard, seed):
    value = "\0".join((SHARD_ALGORITHM, seed, case_id, shard)).encode("utf-8")
    return hashlib.sha256(value).digest()


def build_manifest(
    records,
    shards,
    seed,
    sources,
):
    """Assign every Case to one shard with versioned highest-random-weight hashing."""

    normalized_shards = sorted(shards)
    if not normalized_shards:
        raise ValueError("at least one shard is required")
    if len(normalized_shards) != len(set(normalized_shards)):
        raise ValueError("shard aliases must be unique")
    if any(not shard.strip() for shard in normalized_shards):
        raise ValueError("shard aliases must be non-empty")

    sorted_records = sorted(records, key=lambda record: record["id"])
    case_ids = [record["id"] for record in sorted_records]
    if len(case_ids) != len(set(case_ids)):
        raise ValueError("Case identifiers must be unique")

    assignments = []
    assignment_counts = {shard: 0 for shard in normalized_shards}
    for record in sorted_records:
        shard = max(
            normalized_shards,
            key=lambda candidate: _hrw_score(record["id"], candidate, seed),
        )
        assignment_counts[shard] += 1
        assignments.append({"case_id": record["id"], "shard": shard})

    manifest = {
        "schema_version": SCHEMA_VERSION,
        "algorithm": SHARD_ALGORITHM,
        "seed": seed,
        "inventory_sha256": hashlib.sha256(_inventory_bytes(sorted_records)).hexdigest(),
        "sources": dict(sorted(sources.items())),
        "shards": normalized_shards,
        "assignment_counts": assignment_counts,
        "assignments": assignments,
    }
    manifest["manifest_sha256"] = hashlib.sha256(
        _canonical_json(manifest).encode("utf-8")
    ).hexdigest()
    return manifest


def write_manifest(manifest, output_path):
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    _write_text(output, _canonical_json(manifest) + "\n")


def read_inventory(path):
    records = []
    for line_number, line in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            records.append(json.loads(line))
        except json.JSONDecodeError as error:
            raise CorpusError(f"invalid inventory JSON on line {line_number}: {error}") from error
    return records


def read_manifest(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise CorpusError(f"invalid manifest JSON: {error}") from error


def _validate_inputs(records, manifest):
    if manifest.get("algorithm") != SHARD_ALGORITHM:
        raise CorpusError(f"unsupported manifest algorithm: {manifest.get('algorithm')}")
    expected_inventory = hashlib.sha256(_inventory_bytes(records)).hexdigest()
    if manifest.get("inventory_sha256") != expected_inventory:
        raise CorpusError("manifest inventory SHA-256 does not match inventory")
    expected_manifest = manifest.get("manifest_sha256")
    unhashed = dict(manifest)
    unhashed.pop("manifest_sha256", None)
    actual_manifest = hashlib.sha256(_canonical_json(unhashed).encode("utf-8")).hexdigest()
    if expected_manifest != actual_manifest:
        raise CorpusError("manifest SHA-256 is invalid")


def _git(root, *arguments):
    process = subprocess.run(
        ["git", *arguments],
        cwd=str(root),
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        universal_newlines=True,
        encoding="utf-8",
        errors="replace",
    )
    if process.returncode != 0:
        raise CorpusError(f"git {' '.join(arguments)} failed in {root}: {process.stderr.strip()}")
    return process.stdout.strip()


def _require_clean(root, repository):
    changed = _git(root, "status", "--porcelain", "--untracked-files=no")
    if changed:
        raise CorpusError(f"{repository} repository is dirty: tracked changes are present")


def _current_branch(root):
    return _git(root, "symbolic-ref", "--short", "-q", "HEAD")


def _utc_now():
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def _write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    _write_text(path, _canonical_json(value) + "\n")


def _write_checksums(evidence_dir):
    entries = []
    for path in sorted(evidence_dir.rglob("*")):
        if not path.is_file() or path.name == "checksums.sha256":
            continue
        relative = path.relative_to(evidence_dir).as_posix()
        entries.append(f"{_sha256(path)}  {relative}\n")
    _write_text(evidence_dir / "checksums.sha256", "".join(entries))


def verify_evidence(evidence_dir):
    """Return checksum or file-set errors; an empty list means verified."""

    root = Path(evidence_dir)
    checksum_path = root / "checksums.sha256"
    if not checksum_path.is_file():
        return ["missing checksums.sha256"]
    expected = {}
    errors = []
    for line_number, line in enumerate(checksum_path.read_text(encoding="utf-8").splitlines(), 1):
        if not line:
            continue
        if "  " not in line:
            errors.append(f"invalid checksum line {line_number}")
            continue
        digest, relative = line.split("  ", 1)
        expected[relative] = digest
    actual_files = {
        path.relative_to(root).as_posix()
        for path in root.rglob("*")
        if path.is_file() and path.name != "checksums.sha256"
    }
    for relative in sorted(set(expected) - actual_files):
        errors.append(f"missing file: {relative}")
    for relative in sorted(actual_files - set(expected)):
        errors.append(f"unlisted file: {relative}")
    for relative in sorted(actual_files & set(expected)):
        actual = _sha256(root / relative)
        if actual != expected[relative]:
            errors.append(f"checksum mismatch: {relative}")
    return errors


def run_case(
    *,
    inventory_path,
    manifest_path,
    case_id,
    shard,
    machine_alias,
    zstack_root,
    premium_root,
    evidence_root,
    build_receipt_path,
    allow_network=False,
):
    """Run exactly one assigned Case and collect a self-checking evidence directory."""

    records = read_inventory(inventory_path)
    manifest = read_manifest(manifest_path)
    _validate_inputs(records, manifest)
    record_by_id = {record["id"]: record for record in records}
    if case_id not in record_by_id:
        raise CorpusError(f"Case is not present in inventory: {case_id}")
    record = record_by_id[case_id]
    assignment_by_id = {
        assignment["case_id"]: assignment["shard"]
        for assignment in manifest.get("assignments", [])
    }
    assigned = assignment_by_id.get(case_id)
    if assigned != shard:
        raise CorpusError(f"Case is assigned to {assigned}, not requested shard {shard}")
    if machine_alias != shard:
        raise CorpusError("machine alias must equal the assigned shard alias")
    if record.get("network_deferred") and not allow_network:
        raise CorpusError("network-deferred Case is forbidden by the current pilot policy")
    if record.get("exclusion_markers"):
        raise CorpusError(
            f"Case has exclusion markers: {','.join(record['exclusion_markers'])}"
        )

    zstack = Path(zstack_root).resolve()
    premium = Path(premium_root).resolve()
    evidence_base = Path(evidence_root).resolve()
    build_receipt_file = Path(build_receipt_path).resolve()
    sources = manifest.get("sources", {})
    actual_zstack_head = _git(zstack, "rev-parse", "HEAD")
    actual_premium_head = _git(premium, "rev-parse", "HEAD")
    if actual_zstack_head != sources.get("zstack"):
        raise CorpusError("zstack HEAD does not match the manifest source")
    if actual_premium_head != sources.get("premium"):
        raise CorpusError("premium HEAD does not match the manifest source")
    if record.get("repository_commit") != sources.get(record["repository"]):
        raise CorpusError("Case repository commit does not match manifest sources")
    _require_clean(zstack, "zstack")
    _require_clean(premium, "premium")

    try:
        build_receipt = json.loads(build_receipt_file.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise CorpusError(f"invalid build receipt: {error}") from error
    if build_receipt.get("exit_code") != 0:
        raise CorpusError("build receipt is not successful")
    if build_receipt.get("machine_alias") != machine_alias:
        raise CorpusError("build receipt machine alias does not match runner machine")
    if build_receipt.get("zstack_commit") != actual_zstack_head:
        raise CorpusError("build receipt zstack commit does not match HEAD")
    if build_receipt.get("premium_commit") != actual_premium_head:
        raise CorpusError("build receipt premium commit does not match HEAD")

    repository_root = zstack if record["repository"] == "zstack" else premium
    source_path = repository_root / Path(record["source_path"])
    if not source_path.is_file() or _sha256(source_path) != record["source_sha256"]:
        raise CorpusError("Case source is missing or its SHA-256 does not match inventory")
    work_dir = zstack / record["run_requirements"]["working_directory"]
    if not work_dir.is_dir():
        raise CorpusError(f"Maven working directory does not exist: {work_dir}")

    evidence_base.mkdir(parents=True, exist_ok=True)
    lock_path = evidence_base / f".case-corpus-{machine_alias}.lock"
    try:
        lock_path.mkdir()
    except FileExistsError as error:
        raise CorpusError(f"machine lock already exists: {lock_path}") from error

    try:
        _write_json(
            lock_path / "owner.json",
            {"machine_alias": machine_alias, "case_id": case_id, "acquired_at": _utc_now()},
        )
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
        safe_case = re.sub(r"[^A-Za-z0-9_.-]+", "_", record["class_name"])
        run_suffix = hashlib.sha256(f"{case_id}\0{timestamp}".encode()).hexdigest()[:10]
        run_dir = evidence_base / f"{timestamp}-{safe_case}-{run_suffix}"
        run_dir.mkdir()

        active_log = work_dir / record["run_requirements"]["management_log"]
        if active_log.exists():
            preexisting = run_dir / "preexisting" / active_log.name
            preexisting.parent.mkdir(parents=True)
            active_log.replace(preexisting)

        source_snapshot = run_dir / "source" / source_path.name
        source_snapshot.parent.mkdir(parents=True)
        shutil.copy2(source_path, source_snapshot)
        _write_json(
            run_dir / "source" / "reference.json",
            {
                "case_id": case_id,
                "repository": record["repository"],
                "repository_commit": record["repository_commit"],
                "source_path": record["source_path"],
                "source_sha256": record["source_sha256"],
            },
        )
        _write_json(
            run_dir / "source" / "methods.json",
            {
                "declared_methods": record["declared_methods"],
                "selected_method": record["selected_method"],
                "method_discovery": record["method_discovery"],
            },
        )
        build_target = run_dir / "build" / "build-receipt.json"
        build_target.parent.mkdir(parents=True)
        shutil.copy2(build_receipt_file, build_target)

        runner_dir = run_dir / "runner"
        runner_dir.mkdir()
        stdout_path = runner_dir / "stdout.log"
        stderr_path = runner_dir / "stderr.log"
        argv = [str(argument) for argument in record["run_requirements"]["argv"]]
        started_at = _utc_now()
        monotonic_start = time.monotonic()
        with stdout_path.open("wb") as stdout, stderr_path.open("wb") as stderr:
            process = subprocess.run(
                argv,
                cwd=work_dir,
                stdout=stdout,
                stderr=stderr,
                check=False,
            )
        duration_seconds = round(time.monotonic() - monotonic_start, 6)
        ended_at = _utc_now()
        _write_text(runner_dir / "exit-code.txt", f"{process.returncode}\n")

        collected_log = run_dir / "mn" / active_log.name
        if active_log.is_file() and active_log.stat().st_size > 0:
            collected_log.parent.mkdir(parents=True)
            shutil.copy2(active_log, collected_log)
            log_status = "present"
        elif active_log.is_file():
            log_status = "empty"
        else:
            log_status = "missing"

        if process.returncode != 0:
            collection_status = "case-failed"
            runner_exit_code = process.returncode
        elif log_status == "missing":
            collection_status = "missing-management-log"
            runner_exit_code = 70
        elif log_status == "empty":
            collection_status = "empty-management-log"
            runner_exit_code = 71
        else:
            collection_status = "success"
            runner_exit_code = 0

        metadata = {
            "schema_version": SCHEMA_VERSION,
            "case_id": case_id,
            "class_name": record["class_name"],
            "module": record["module"],
            "machine_alias": machine_alias,
            "shard": shard,
            "zstack_branch": _current_branch(zstack),
            "sources": {"zstack": actual_zstack_head, "premium": actual_premium_head},
            "manifest_sha256": manifest["manifest_sha256"],
            "inventory_sha256": manifest["inventory_sha256"],
            "started_at": started_at,
            "ended_at": ended_at,
            "duration_seconds": duration_seconds,
            "command": argv,
            "case_exit_code": process.returncode,
            "runner_exit_code": runner_exit_code,
            "management_log_status": log_status,
            "collection_status": collection_status,
            "cleanup_eligible": runner_exit_code == 0,
        }
        _write_json(run_dir / "metadata.json", metadata)
        _write_checksums(run_dir)
        return {
            "evidence_dir": str(run_dir),
            "runner_exit_code": runner_exit_code,
            "collection_status": collection_status,
        }
    finally:
        shutil.rmtree(lock_path, ignore_errors=True)


def inventory_summary(records, sources):
    return {
        "schema_version": SCHEMA_VERSION,
        "case_count": len(records),
        "inventory_sha256": hashlib.sha256(_inventory_bytes(records)).hexdigest(),
        "sources": dict(sorted(sources.items())),
        "repository_counts": dict(sorted(Counter(record["repository"] for record in records).items())),
        "module_counts": dict(sorted(Counter(record["module"] for record in records).items())),
        "network_deferred_count": sum(bool(record["network_deferred"]) for record in records),
        "excluded_marker_count": sum(bool(record["exclusion_markers"]) for record in records),
    }


def _sources_from_inventory(records):
    sources = {}
    for record in records:
        repository = record["repository"]
        commit = record["repository_commit"]
        if repository in sources and sources[repository] != commit:
            raise CorpusError(f"inventory contains multiple commits for {repository}")
        sources[repository] = commit
    return dict(sorted(sources.items()))


def build_parser():
    parser = argparse.ArgumentParser(
        description="Inventory, shard, run, and verify ZStack Integration Case corpus evidence."
    )
    subparsers = parser.add_subparsers(dest="command")

    inventory = subparsers.add_parser("inventory", help="generate canonical Case inventory")
    inventory.add_argument("--zstack-root", required=True, type=Path)
    inventory.add_argument("--premium-root", required=True, type=Path)
    inventory.add_argument("--output", required=True, type=Path)
    inventory.add_argument("--summary", required=True, type=Path)

    manifest = subparsers.add_parser("manifest", help="generate deterministic HRW shard manifest")
    manifest.add_argument("--inventory", required=True, type=Path)
    manifest.add_argument("--output", required=True, type=Path)
    manifest.add_argument("--seed", required=True)
    manifest.add_argument("--shard", required=True, action="append")

    run = subparsers.add_parser("run", help="run exactly one assigned Case and collect evidence")
    run.add_argument("--inventory", required=True, type=Path)
    run.add_argument("--manifest", required=True, type=Path)
    run.add_argument("--case-id", required=True)
    run.add_argument("--shard", required=True)
    run.add_argument("--machine-alias", required=True)
    run.add_argument("--zstack-root", required=True, type=Path)
    run.add_argument("--premium-root", required=True, type=Path)
    run.add_argument("--evidence-root", required=True, type=Path)
    run.add_argument("--build-receipt", required=True, type=Path)
    run.add_argument("--allow-network", action="store_true")

    verify = subparsers.add_parser("verify", help="verify one collected evidence directory")
    verify.add_argument("evidence_dir", type=Path)
    return parser


def main(argv=None):
    parser = build_parser()
    arguments = parser.parse_args(argv)
    if arguments.command is None:
        parser.error("a command is required")
    try:
        if arguments.command == "inventory":
            zstack_root = arguments.zstack_root.resolve()
            premium_root = arguments.premium_root.resolve()
            _require_clean(zstack_root, "zstack")
            _require_clean(premium_root, "premium")
            sources = {
                "zstack": _git(zstack_root, "rev-parse", "HEAD"),
                "premium": _git(premium_root, "rev-parse", "HEAD"),
            }
            records = inventory_records(
                zstack_root,
                premium_root,
                sources["zstack"],
                sources["premium"],
            )
            write_inventory(records, arguments.output)
            _write_json(arguments.summary, inventory_summary(records, sources))
            print(_canonical_json(inventory_summary(records, sources)))
            return 0
        if arguments.command == "manifest":
            records = read_inventory(arguments.inventory)
            manifest = build_manifest(
                records,
                arguments.shard,
                arguments.seed,
                _sources_from_inventory(records),
            )
            write_manifest(manifest, arguments.output)
            print(
                _canonical_json(
                    {
                        "manifest_sha256": manifest["manifest_sha256"],
                        "assignment_counts": manifest["assignment_counts"],
                    }
                )
            )
            return 0
        if arguments.command == "run":
            result = run_case(
                inventory_path=arguments.inventory,
                manifest_path=arguments.manifest,
                case_id=arguments.case_id,
                shard=arguments.shard,
                machine_alias=arguments.machine_alias,
                zstack_root=arguments.zstack_root,
                premium_root=arguments.premium_root,
                evidence_root=arguments.evidence_root,
                build_receipt_path=arguments.build_receipt,
                allow_network=arguments.allow_network,
            )
            print(_canonical_json(result))
            return int(result["runner_exit_code"])
        if arguments.command == "verify":
            errors = verify_evidence(arguments.evidence_dir)
            print(_canonical_json({"verified": not errors, "errors": errors}))
            return 0 if not errors else 1
    except (CorpusError, ValueError, OSError) as error:
        print(f"case-corpus: {error}", file=sys.stderr)
        return 2
    raise AssertionError(f"unhandled command: {arguments.command}")


if __name__ == "__main__":
    raise SystemExit(main())
