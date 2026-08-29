import hashlib
import importlib.util
import json
import subprocess
import sys
import tempfile
import types
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "case_corpus.py"
if MODULE_PATH.exists():
    spec = importlib.util.spec_from_file_location("case_corpus", MODULE_PATH)
    case_corpus = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = case_corpus
    spec.loader.exec_module(case_corpus)
else:
    case_corpus = types.SimpleNamespace()


def write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return path


def git_repo(path, files):
    path.mkdir(parents=True, exist_ok=True)
    subprocess.run(["git", "init", "-q", str(path)], check=True)
    subprocess.run(
        ["git", "config", "user.email", "case-corpus@example.invalid"],
        cwd=str(path),
        check=True,
    )
    subprocess.run(
        ["git", "config", "user.name", "Case Corpus Test"],
        cwd=str(path),
        check=True,
    )
    for relative, content in files.items():
        write(path / relative, content)
    subprocess.run(["git", "add", "."], cwd=str(path), check=True)
    subprocess.run(
        ["git", "commit", "-q", "-m", "fixture"], cwd=str(path), check=True
    )
    return subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=str(path), universal_newlines=True
    ).strip()


class InventoryTests(unittest.TestCase):
    def setUp(self):
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tempdir.name)
        self.zstack = self.root / "zstack"
        self.premium = self.root / "premium"

    def tearDown(self):
        self.tempdir.cleanup()

    def test_discovers_only_concrete_cases_and_records_methods(self):
        self.assertTrue(hasattr(case_corpus, "inventory_records"))
        source = write(
            self.zstack
            / "test/src/test/groovy/org/zstack/test/integration/core/FooCase.groovy",
            """package org.zstack.test.integration.core

class FooCase extends SubCase {
    void environment() {}
    void test() { helper() }
    private void helper() {}
    FooCase() {}
}
""",
        )
        write(
            self.zstack
            / "test/src/test/groovy/org/zstack/test/integration/core/HelperCase.groovy",
            """package org.zstack.test.integration.core
class HelperCase { void test() {} }
""",
        )
        write(
            self.zstack
            / "testlib/src/main/java/org/zstack/testlib/SubCase.groovy",
            """package org.zstack.testlib
abstract class SubCase extends Test implements Case {}
""",
        )

        records = case_corpus.inventory_records(
            self.zstack, self.premium, "z-head", "p-head"
        )

        self.assertEqual(1, len(records))
        record = records[0]
        self.assertEqual("zstack:org.zstack.test.integration.core.FooCase", record["id"])
        self.assertEqual("zstack", record["repository"])
        self.assertEqual("core", record["module"])
        self.assertEqual("SubCase", record["base_class"])
        self.assertEqual(["environment", "helper", "test"], record["declared_methods"])
        self.assertEqual("test", record["selected_method"])
        self.assertEqual("syntax-scan-v1", record["method_discovery"])
        self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), record["source_sha256"])
        self.assertFalse(record["network_deferred"])
        self.assertEqual("test", record["maven_module"])
        self.assertEqual("z-head", record["repository_commit"])

    def test_discovers_premium_cases_and_marks_network_deferred(self):
        self.assertTrue(hasattr(case_corpus, "inventory_records"))
        write(
            self.premium
            / "test-premium/src/test/groovy/org/zstack/test/integration/premium/network/FooCase.groovy",
            """package org.zstack.test.integration.premium.network
@Deprecated
class FooCase extends PremiumSubCase {
    void environment() {}
    void test() {}
    void clean() {}
}
""",
        )

        records = case_corpus.inventory_records(
            self.zstack, self.premium, "z-head", "p-head"
        )

        self.assertEqual(1, len(records))
        record = records[0]
        self.assertEqual("premium", record["repository"])
        self.assertEqual("network", record["module"])
        self.assertEqual("PremiumSubCase", record["base_class"])
        self.assertEqual("test-premium", record["maven_module"])
        self.assertEqual("p-head", record["repository_commit"])
        self.assertTrue(record["network_deferred"])
        self.assertEqual(["Deprecated"], record["exclusion_markers"])

    def test_inventory_is_sorted_and_canonical_jsonl_is_reproducible(self):
        self.assertTrue(hasattr(case_corpus, "inventory_records"))
        self.assertTrue(hasattr(case_corpus, "write_inventory"))
        write(
            self.zstack
            / "test/src/test/groovy/org/zstack/test/integration/identity/ZedCase.groovy",
            """package org.zstack.test.integration.identity
class ZedCase extends SubCase { void test() {} }
""",
        )
        write(
            self.zstack
            / "test/src/test/groovy/org/zstack/test/integration/compute/AlphaCase.groovy",
            """package org.zstack.test.integration.compute
class AlphaCase extends SubCase { void test() {} }
""",
        )

        records = case_corpus.inventory_records(
            self.zstack, self.premium, "z-head", "p-head"
        )
        first = self.root / "first.jsonl"
        second = self.root / "second.jsonl"
        case_corpus.write_inventory(records, first)
        case_corpus.write_inventory(list(reversed(records)), second)

        self.assertEqual(first.read_bytes(), second.read_bytes())
        ids = [json.loads(line)["id"] for line in first.read_text().splitlines()]
        self.assertEqual(sorted(ids), ids)

    def test_discovers_transitive_case_inheritance_and_excludes_abstract_stubs(self):
        write(
            self.premium
            / "test-premium/src/test/groovy/org/zstack/test/integration/crypto/CryptoAuthCaseStub.groovy",
            """package org.zstack.test.integration.crypto
abstract class CryptoAuthCaseStub extends PremiumSubCase {}
""",
        )
        write(
            self.premium
            / "test-premium/src/test/groovy/org/zstack/test/integration/crypto/CryptoChildCase.groovy",
            """package org.zstack.test.integration.crypto
class CryptoChildCase extends CryptoAuthCaseStub { void test() {} }
""",
        )
        write(
            self.premium
            / "testlib-premium/src/main/java/org/zstack/testlib/premium/PremiumFSMCase.groovy",
            """package org.zstack.testlib.premium
abstract class PremiumFSMCase extends TestPremium implements Case {}
""",
        )
        write(
            self.premium
            / "test-premium/src/test/groovy/org/zstack/test/integration/identity/AccountFSMCase.groovy",
            """package org.zstack.test.integration.identity
class AccountFSMCase extends PremiumFSMCase { void test() {} }
""",
        )

        records = case_corpus.inventory_records(
            self.zstack, self.premium, "z-head", "p-head"
        )

        self.assertEqual(
            [
                "premium:org.zstack.test.integration.crypto.CryptoChildCase",
                "premium:org.zstack.test.integration.identity.AccountFSMCase",
            ],
            [record["id"] for record in records],
        )
        by_class = {record["class_name"]: record for record in records}
        self.assertEqual("CryptoAuthCaseStub", by_class["CryptoChildCase"]["base_class"])
        self.assertEqual("PremiumSubCase", by_class["CryptoChildCase"]["case_contract_root"])
        self.assertEqual("PremiumFSMCase", by_class["AccountFSMCase"]["case_contract_root"])


class ManifestTests(unittest.TestCase):
    @staticmethod
    def records(count=120):
        return [
            {
                "id": f"zstack:org.zstack.test.integration.core.Case{index:04d}",
                "repository": "zstack",
                "repository_commit": "z-head",
                "source_sha256": hashlib.sha256(str(index).encode()).hexdigest(),
                "module": "core",
                "network_deferred": False,
            }
            for index in range(count)
        ]

    def test_hrw_assigns_each_case_once_and_is_input_order_independent(self):
        self.assertTrue(hasattr(case_corpus, "build_manifest"))
        records = self.records()
        sources = {"zstack": "z-head", "premium": "p-head"}

        first = case_corpus.build_manifest(
            records, ["dev-machine-3", "dev-machine-1", "dev-machine-2"], "seed-v1", sources
        )
        second = case_corpus.build_manifest(
            list(reversed(records)),
            ["dev-machine-2", "dev-machine-3", "dev-machine-1"],
            "seed-v1",
            sources,
        )

        self.assertEqual(first, second)
        assignments = first["assignments"]
        self.assertEqual(len(records), len(assignments))
        self.assertEqual(len(records), len({item["case_id"] for item in assignments}))
        self.assertEqual(
            {"dev-machine-1", "dev-machine-2", "dev-machine-3"},
            {item["shard"] for item in assignments},
        )
        self.assertEqual("hrw-sha256-v1", first["algorithm"])
        self.assertEqual(sources, first["sources"])
        self.assertRegex(first["inventory_sha256"], r"^[0-9a-f]{64}$")
        self.assertRegex(first["manifest_sha256"], r"^[0-9a-f]{64}$")
        counts = list(first["assignment_counts"].values())
        self.assertLess(max(counts) - min(counts), 30)

    def test_removing_shard_moves_only_its_previous_cases(self):
        self.assertTrue(hasattr(case_corpus, "build_manifest"))
        records = self.records(300)
        sources = {"zstack": "z-head", "premium": "p-head"}
        three = case_corpus.build_manifest(
            records, ["a", "b", "c"], "seed-v1", sources
        )
        two = case_corpus.build_manifest(records, ["a", "b"], "seed-v1", sources)
        before = {item["case_id"]: item["shard"] for item in three["assignments"]}
        after = {item["case_id"]: item["shard"] for item in two["assignments"]}

        for case_id, previous_shard in before.items():
            if previous_shard != "c":
                self.assertEqual(previous_shard, after[case_id])

    def test_manifest_rejects_duplicate_or_empty_shards(self):
        self.assertTrue(hasattr(case_corpus, "build_manifest"))
        with self.assertRaisesRegex(ValueError, "unique"):
            case_corpus.build_manifest(self.records(1), ["a", "a"], "seed-v1", {})
        with self.assertRaisesRegex(ValueError, "shard"):
            case_corpus.build_manifest(self.records(1), [], "seed-v1", {})


class RunnerTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue(hasattr(case_corpus, "run_case"))
        self.assertTrue(hasattr(case_corpus, "verify_evidence"))
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tempdir.name)
        self.zstack = self.root / "zstack"
        self.premium = self.root / "premium"
        self.source_relative = Path(
            "test/src/test/groovy/org/zstack/test/integration/core/FooCase.groovy"
        )
        self.source = """package org.zstack.test.integration.core
class FooCase extends SubCase {
    void environment() {}
    void test() { helper() }
    private void helper() {}
}
"""
        self.zhead = git_repo(self.zstack, {self.source_relative.as_posix(): self.source})
        self.phead = git_repo(self.premium, {"README": "premium fixture\n"})
        self.fake_maven = write(
            self.root / "fake_maven.py",
            """import pathlib
import sys
if "--no-log" not in sys.argv:
    pathlib.Path("management-server.log").write_text("new-case-log\\n", encoding="utf-8")
print("case stdout")
print("case stderr", file=sys.stderr)
raise SystemExit(3 if "--fail" in sys.argv else 0)
""",
        )
        self.inventory_path = self.root / "inventory.jsonl"
        self.manifest_path = self.root / "manifest.json"
        self.build_receipt = self.root / "build.json"
        self.evidence_root = self.root / "evidence"
        self.record = self._record([sys.executable, str(self.fake_maven)])
        self._write_inputs()

    def tearDown(self):
        self.tempdir.cleanup()

    def _record(self, argv):
        return {
            "schema_version": 1,
            "id": "zstack:org.zstack.test.integration.core.FooCase",
            "repository": "zstack",
            "repository_commit": self.zhead,
            "source_path": self.source_relative.as_posix(),
            "source_sha256": hashlib.sha256(
                (self.zstack / self.source_relative).read_bytes()
            ).hexdigest(),
            "package": "org.zstack.test.integration.core",
            "class_name": "FooCase",
            "fully_qualified_class": "org.zstack.test.integration.core.FooCase",
            "base_class": "SubCase",
            "declared_methods": ["environment", "helper", "test"],
            "selected_method": "test",
            "method_discovery": "syntax-scan-v1",
            "module": "core",
            "network_deferred": False,
            "exclusion_markers": [],
            "maven_module": "test",
            "run_requirements": {
                "working_directory": "test",
                "argv": argv,
                "serial_per_machine": True,
                "management_log": "management-server.log",
            },
        }

    def _write_inputs(self, shards=None, sources=None):
        shards = shards or ["dev-machine-1", "dev-machine-2", "dev-machine-3"]
        sources = sources or {"zstack": self.zhead, "premium": self.phead}
        case_corpus.write_inventory([self.record], self.inventory_path)
        manifest = case_corpus.build_manifest([self.record], shards, "seed-v1", sources)
        case_corpus.write_manifest(manifest, self.manifest_path)
        self.assigned_shard = manifest["assignments"][0]["shard"]
        self.build_receipt.write_text(
            json.dumps(
                {
                    "machine_alias": self.assigned_shard,
                    "zstack_commit": self.zhead,
                    "premium_commit": self.phead,
                    "exit_code": 0,
                    "command": ["./runMavenProfile", "premium"],
                    "started_at": "2026-08-29T00:00:00Z",
                    "ended_at": "2026-08-29T00:10:00Z",
                },
                sort_keys=True,
            ),
            encoding="utf-8",
        )

    def _run(self, **overrides):
        arguments = {
            "inventory_path": self.inventory_path,
            "manifest_path": self.manifest_path,
            "case_id": self.record["id"],
            "shard": self.assigned_shard,
            "machine_alias": self.assigned_shard,
            "zstack_root": self.zstack,
            "premium_root": self.premium,
            "evidence_root": self.evidence_root,
            "build_receipt_path": self.build_receipt,
        }
        arguments.update(overrides)
        return case_corpus.run_case(**arguments)

    def test_collects_non_mixed_complete_evidence_and_verifies_checksums(self):
        active_log = self.zstack / "test/management-server.log"
        write(active_log, "old-unrelated-log\n")

        result = self._run()
        run_dir = Path(result["evidence_dir"])

        self.assertEqual(0, result["runner_exit_code"])
        self.assertEqual("success", result["collection_status"])
        self.assertEqual("old-unrelated-log\n", (run_dir / "preexisting/management-server.log").read_text())
        self.assertEqual("new-case-log\n", (run_dir / "mn/management-server.log").read_text())
        self.assertEqual("case stdout\n", (run_dir / "runner/stdout.log").read_text())
        self.assertEqual("case stderr\n", (run_dir / "runner/stderr.log").read_text())
        self.assertEqual("0\n", (run_dir / "runner/exit-code.txt").read_text())
        self.assertEqual(self.source, (run_dir / "source/FooCase.groovy").read_text())
        methods = json.loads((run_dir / "source/methods.json").read_text())
        self.assertEqual("test", methods["selected_method"])
        self.assertEqual(["environment", "helper", "test"], methods["declared_methods"])
        metadata = json.loads((run_dir / "metadata.json").read_text())
        self.assertEqual(self.assigned_shard, metadata["machine_alias"])
        self.assertEqual(self.zhead, metadata["sources"]["zstack"])
        self.assertGreaterEqual(metadata["duration_seconds"], 0)
        self.assertEqual([], case_corpus.verify_evidence(run_dir))

        (run_dir / "runner/stdout.log").write_text("tampered", encoding="utf-8")
        errors = case_corpus.verify_evidence(run_dir)
        self.assertTrue(any("runner/stdout.log" in error for error in errors))

    def test_missing_management_log_cannot_be_success(self):
        self.record = self._record([sys.executable, str(self.fake_maven), "--no-log"])
        self._write_inputs()

        result = self._run()

        self.assertEqual(70, result["runner_exit_code"])
        self.assertEqual("missing-management-log", result["collection_status"])
        self.assertFalse((Path(result["evidence_dir"]) / "mn/management-server.log").exists())

    def test_refuses_wrong_shard_network_case_commit_mismatch_and_dirty_repo(self):
        wrong_shard = next(shard for shard in ["dev-machine-1", "dev-machine-2", "dev-machine-3"] if shard != self.assigned_shard)
        with self.assertRaisesRegex(case_corpus.CorpusError, "assigned"):
            self._run(shard=wrong_shard, machine_alias=wrong_shard)

        self.record["network_deferred"] = True
        self._write_inputs()
        with self.assertRaisesRegex(case_corpus.CorpusError, "network"):
            self._run()

        self.record["network_deferred"] = False
        self._write_inputs(sources={"zstack": "0" * 40, "premium": self.phead})
        with self.assertRaisesRegex(case_corpus.CorpusError, "HEAD"):
            self._run()

        self._write_inputs()
        write(self.zstack / self.source_relative, self.source + "// dirty\n")
        with self.assertRaisesRegex(case_corpus.CorpusError, "dirty"):
            self._run()

    def test_refuses_existing_machine_lock(self):
        lock = self.evidence_root / f".case-corpus-{self.assigned_shard}.lock"
        lock.mkdir(parents=True)
        with self.assertRaisesRegex(case_corpus.CorpusError, "lock"):
            self._run()


class CliTests(unittest.TestCase):
    def setUp(self):
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tempdir.name)
        self.zstack = self.root / "zstack"
        self.premium = self.root / "premium"
        self.zhead = git_repo(
            self.zstack,
            {
                "test/src/test/groovy/org/zstack/test/integration/core/FooCase.groovy":
                    "package org.zstack.test.integration.core\nclass FooCase extends SubCase {\n    void test() {}\n}\n"
            },
        )
        self.phead = git_repo(
            self.premium,
            {
                "test-premium/src/test/groovy/org/zstack/test/integration/premium/identity/BarCase.groovy":
                    "package org.zstack.test.integration.premium.identity\nclass BarCase extends PremiumSubCase {\n    void test() {}\n}\n"
            },
        )

    def tearDown(self):
        self.tempdir.cleanup()

    def run_cli(self, *arguments, check=True):
        return subprocess.run(
            [sys.executable, str(MODULE_PATH), *map(str, arguments)],
            check=check,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            universal_newlines=True,
        )

    def test_help_lists_all_supported_commands(self):
        process = self.run_cli("--help")
        self.assertIn("inventory", process.stdout)
        self.assertIn("manifest", process.stdout)
        self.assertIn("run", process.stdout)
        self.assertIn("verify", process.stdout)

    def test_inventory_and_manifest_cli_outputs_are_reproducible(self):
        first_inventory = self.root / "first.jsonl"
        second_inventory = self.root / "second.jsonl"
        first_summary = self.root / "first-summary.json"
        second_summary = self.root / "second-summary.json"
        common = [
            "--zstack-root", self.zstack,
            "--premium-root", self.premium,
        ]
        self.run_cli("inventory", *common, "--output", first_inventory, "--summary", first_summary)
        self.run_cli("inventory", *common, "--output", second_inventory, "--summary", second_summary)

        self.assertEqual(first_inventory.read_bytes(), second_inventory.read_bytes())
        self.assertEqual(first_summary.read_bytes(), second_summary.read_bytes())
        summary = json.loads(first_summary.read_text())
        self.assertEqual(2, summary["case_count"])
        self.assertEqual({"premium": 1, "zstack": 1}, summary["repository_counts"])
        self.assertEqual(self.zhead, summary["sources"]["zstack"])
        self.assertEqual(self.phead, summary["sources"]["premium"])

        first_manifest = self.root / "first-manifest.json"
        second_manifest = self.root / "second-manifest.json"
        self.run_cli(
            "manifest", "--inventory", first_inventory, "--output", first_manifest,
            "--seed", "seed-v1", "--shard", "b", "--shard", "a",
        )
        self.run_cli(
            "manifest", "--inventory", first_inventory, "--output", second_manifest,
            "--seed", "seed-v1", "--shard", "a", "--shard", "b",
        )
        self.assertEqual(first_manifest.read_bytes(), second_manifest.read_bytes())


class CompatibilityTests(unittest.TestCase):
    def test_cli_source_targets_python36_and_git18(self):
        for path in (MODULE_PATH, Path(__file__)):
            source = path.read_text(encoding="utf-8")
            self.assertNotIn("from __future__ import " + "annotations", source)
            self.assertNotRegex(source, r"\b(?:list|dict|set|tuple)\[")
            self.assertNotRegex(source, r"\s\|\s(?:None|str|Path)")
            self.assertNotIn("text" + "=True", source)
        tool_source = MODULE_PATH.read_text(encoding="utf-8")
        self.assertNotIn("subparsers(dest=\"command\", required=True)", tool_source)
        self.assertIn("cwd=str(root)", tool_source)
        self.assertNotIn("[\"git\", \"-C\"", tool_source)
        self.assertNotIn("\"branch\", \"--show-current\"", tool_source)
        self.assertIn("symbolic-ref", tool_source)


if __name__ == "__main__":
    unittest.main()
