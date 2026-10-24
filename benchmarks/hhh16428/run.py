#!/usr/bin/env python3
"""Bounded, single-runner paired experiment. No timing assertion or publication."""

import argparse
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import signal
import statistics
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import zipfile


BASELINE_COMMIT = "0b6f2013a64886d48b7d5341f2f10c987598d921"
BASELINE_TREE = "fb03055a2cdd51460d565a35f53ed5fe90ec7bf0"
TEST_CLASSES = ["org.hibernate.orm.test.graph." + name for name in
                ("EntityGraphsTest", "LegacySyntaxEntityGraphsTest", "SubgraphMapEqualityTest")]
SOURCE = "hibernate-core/src/main/java/org/hibernate/graph/EntityGraphs.java"
HARNESS_SOURCE = "hibernate-core/src/jmh/java/org/hibernate/graph/EntityGraphsMapBenchmark.java"
BENCHMARK = "org.hibernate.graph.EntityGraphsMapBenchmark"
OLD_LOOP = "for ( var key : aKeys ) {\n\t\t\t\t\tif ( !areEqual( a.get( key ), b.get( key ) ) ) {"
NEW_LOOP = "for ( var entry : a.entrySet() ) {\n\t\t\t\t\tif ( !areEqual( entry.getValue(), b.get( entry.getKey() ) ) ) {"
MAP_CASES = ["EMPTY"] + [f"{kind}_{size}" for kind in
    ("EQUAL", "KEY_MISMATCH", "VALUE_MISMATCH") for size in (1, 4, 16)]
CASES = [("mapEquality", "caseName", case) for case in MAP_CASES] + [
    ("rootGraphEquality", "size", str(size)) for size in (1, 4, 16)]
JVM_ARGS = ("-Xms512m -Xmx512m -Duser.language=en -Duser.country=US "
            "-Duser.timezone=UTC -Dfile.encoding=UTF-8 "
            "-Dlog4j.configurationFile=file:src/jmh/resources/log4j2.properties")


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_json(path, value):
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")
    temporary.replace(path)


class Experiment:
    def __init__(self, args):
        self.baseline = args.baseline.resolve()
        self.candidate = args.candidate.resolve()
        self.output = args.output.resolve()
        self.harness = Path(__file__).resolve().parent
        self.output.mkdir(parents=True, exist_ok=False)
        for directory in ("logs", "raw", "jars", "sources", "candidate-tests"):
            (self.output / directory).mkdir()
        self.deadline = time.monotonic() + 35 * 60
        self.commands = []
        self.results = []
        self.manifest = {
            "status": "running", "baseline_commit": BASELINE_COMMIT,
            "evidence_commit": os.environ.get("EVIDENCE_COMMIT"),
            "run_id": os.environ.get("GITHUB_RUN_ID"),
            "run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
            "job": os.environ.get("GITHUB_JOB"),
            "baseline_semantic_evidence": {
                "run_url": "https://github.com/xfocus3/hibernate-work/actions/runs/40099352006",
                "tested_tree": BASELINE_TREE, "tests": 61,
                "classes": TEST_CLASSES,
            },
            "started_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "design": {"fork_pairs_per_case": 3, "warmup_iterations": 3,
                       "warmup_seconds": 1, "measurement_iterations": 5,
                       "measurement_seconds": 1, "threads": 1,
                       "mode": "avgt", "unit": "ns/op", "jvm_args": JVM_ARGS,
                       "case_count": len(CASES), "expected_invocations": len(CASES) * 6,
                       "order": "Adjacent pairs; BC/CB/BC, reversed on alternate cases.",
                       "notes": ["All builds and forks run sequentially in one Actions job.",
                                 "Three forks are experimental units, not fifteen independent samples.",
                                 "Class hashes and HashMap layouts can differ between JVMs.",
                                 "No universal speedup, asymptotic improvement, or end-to-end application claim."]},
            "trees": {}, "files": {}, "commands": self.commands,
            "completed_invocations": self.results,
        }
        self.save()

    def save(self):
        write_json(self.output / "manifest.json", self.manifest)

    def run(self, argv, cwd, label, timeout=30):
        remaining = self.deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError("Experiment reached its 35-minute deadline")
        timeout = min(timeout, remaining)
        log = self.output / "logs" / f"{len(self.commands):03d}-{label}.log"
        record = {"label": label, "argv": [str(value) for value in argv],
                  "log": log.relative_to(self.output).as_posix(),
                  "timeout_seconds": timeout, "status": "running"}
        self.commands.append(record)
        self.save()
        started = time.monotonic()
        with log.open("w") as stream:
            process = subprocess.Popen(argv, cwd=cwd, stdout=stream,
                                       stderr=subprocess.STDOUT, text=True,
                                       start_new_session=True)
            try:
                code = process.wait(timeout=timeout)
            except BaseException:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait(timeout=5)
                record.update(status="interrupted_or_timed_out",
                              elapsed_seconds=time.monotonic() - started)
                self.save()
                raise
        record.update(status="passed" if code == 0 else "failed", returncode=code,
                      elapsed_seconds=time.monotonic() - started)
        self.save()
        result = log.read_text(errors="replace")
        if code:
            raise RuntimeError(f"{label} exited {code}; see {record['log']}")
        return result.strip()

    def git(self, tree, *args, label="git"):
        return self.run(["git", *args], tree, label)

    def check_clean(self, tree, label):
        self.git(tree, "diff", "--exit-code", label=f"{label}-unstaged-diff")
        unexpected = self.git(tree, "ls-files", "--others", "--exclude-standard",
                              label=f"{label}-untracked")
        if unexpected:
            raise RuntimeError(f"{label} has unexpected untracked source files")

    def prepare(self):
        if self.candidate.exists():
            raise RuntimeError("Candidate destination must not already exist")
        actual = self.git(self.baseline, "rev-parse", "HEAD", label="baseline-revision")
        if actual != BASELINE_COMMIT:
            raise RuntimeError("Baseline does not match the pinned semantic commit")
        if self.git(self.baseline, "status", "--porcelain", label="baseline-status"):
            raise RuntimeError("Baseline checkout must be clean")
        self.git(self.baseline, "worktree", "add", "--detach", str(self.candidate),
                 BASELINE_COMMIT, label="create-candidate-worktree")
        self.manifest["trees"]["baseline_production"] = self.git(
            self.baseline, "rev-parse", "HEAD^{tree}", label="baseline-production-tree")
        if self.manifest["trees"]["baseline_production"] != BASELINE_TREE:
            raise RuntimeError("Baseline tree does not match independently verified semantic evidence")
        patch = self.harness / "candidate.patch"
        self.git(self.candidate, "apply", "--check", str(patch), label="check-candidate-patch")
        self.git(self.candidate, "apply", "--index", str(patch), label="apply-candidate-patch")
        changed = self.git(self.candidate, "diff", "--cached", "--name-only", label="candidate-files")
        counts = self.git(self.candidate, "diff", "--cached", "--numstat", label="candidate-line-counts")
        if changed != SOURCE or counts != f"2\t2\t{SOURCE}":
            raise RuntimeError("Candidate must contain only the expected two-line production change")
        original = (self.baseline / SOURCE).read_text()
        candidate = (self.candidate / SOURCE).read_text()
        if original.count(OLD_LOOP) != 1 or candidate != original.replace(OLD_LOOP, NEW_LOOP, 1):
            raise RuntimeError("Candidate implementation differs from the reviewed entry-iteration change")
        self.manifest["trees"]["candidate_production"] = self.git(
            self.candidate, "write-tree", label="candidate-production-tree")
        diff = self.git(self.candidate, "diff", "--cached", "--binary", label="candidate-production-diff")
        (self.output / "sources" / "production.patch").write_text(diff + "\n")
        for name in ("EntityGraphsMapBenchmark.java", "harness.init.gradle", "candidate.patch", "run.py"):
            path = self.harness / name
            self.manifest["files"][name] = sha256(path)
            shutil.copyfile(path, self.output / "sources" / name)
        for variant, tree in (("baseline", self.baseline), ("candidate", self.candidate)):
            target = tree / HARNESS_SOURCE
            if target.exists():
                raise RuntimeError("Temporary benchmark would overwrite an existing source file")
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(self.harness / "EntityGraphsMapBenchmark.java", target)
            self.git(tree, "add", "--", HARNESS_SOURCE, label=f"{variant}-stage-harness")
            self.manifest["trees"][f"{variant}_with_harness"] = self.git(
                tree, "write-tree", label=f"{variant}-harness-tree")
            shutil.copyfile(tree / SOURCE, self.output / "sources" / f"{variant}-EntityGraphs.java")
        self.run(["java", "-version"], self.baseline, "java-version")
        self.save()

    def build(self, variant, tree):
        print(f"Building {variant} from its verified source tree", flush=True)
        init = os.path.relpath(self.harness / "harness.init.gradle", tree)
        arguments = ["./gradlew", "--no-daemon", "--no-build-cache", "--no-configuration-cache",
                     "--no-scan", "--console=plain", "--max-workers=2", "-I", init,
                     ":hibernate-core:jmhJar", "-Pdb=h2"]
        if variant == "candidate":
            arguments.append(":hibernate-core:test")
            for test_class in TEST_CLASSES:
                arguments.extend(["--tests", test_class])
            arguments.append(":hibernate-core:formatChecks")
        try:
            self.run(arguments, tree, f"{variant}-build", timeout=600)
        finally:
            if variant == "candidate":
                for path in (tree / "hibernate-core").glob("**/TEST-*.xml"):
                    relative = path.relative_to(tree / "hibernate-core")
                    destination = self.output / "candidate-tests" / relative
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(path, destination)
        if variant == "candidate":
            self.verify_tests()
        self.check_clean(tree, f"{variant}-postbuild")
        after = self.git(tree, "write-tree", label=f"{variant}-postbuild-tree")
        if after != self.manifest["trees"][f"{variant}_with_harness"]:
            raise RuntimeError(f"{variant} source tree changed during the build")
        source_jar = tree / "hibernate-core/build/hhh16428/hhh16428-jmh.jar"
        jar = self.output / "jars" / f"{variant}.jar"
        shutil.copyfile(source_jar, jar)
        self.manifest["files"][f"{variant}.jar"] = sha256(jar)
        self.run(["javap", "-c", "-p", "-classpath", str(jar), "org.hibernate.graph.EntityGraphs"],
                 tree, f"{variant}-EntityGraphs-bytecode")
        self.save()

    def verify_tests(self):
        counts = {}
        for test_class in TEST_CLASSES:
            paths = list((self.output / "candidate-tests").glob(f"**/TEST-{test_class}.xml"))
            if len(paths) != 1:
                raise RuntimeError(f"Expected exactly one candidate XML report for {test_class}")
            suite = ET.parse(paths[0]).getroot()
            tests = int(suite.attrib.get("tests", "0"))
            failures = int(suite.attrib.get("failures", "0"))
            errors = int(suite.attrib.get("errors", "0"))
            skipped = int(suite.attrib.get("skipped", "0"))
            if tests <= 0 or failures or errors or skipped:
                raise RuntimeError(f"Candidate semantic tests incomplete or failing: {test_class}")
            counts[test_class] = tests
        if sum(counts.values()) != 61:
            raise RuntimeError("Candidate must execute the same 61 semantic tests as baseline")
        self.manifest["candidate_semantic_tests"] = {"tests": 61, "classes": counts, "formatChecks": "passed"}
        self.save()

    def verify_jars(self):
        def contents(variant):
            with zipfile.ZipFile(self.output / "jars" / f"{variant}.jar") as jar:
                entries = {}
                for info in jar.infolist():
                    if not info.is_dir():
                        entries.setdefault(info.filename, []).append(
                            hashlib.sha256(jar.read(info)).hexdigest())
                for name, hashes in entries.items():
                    if (len(hashes) > 1 and name.endswith(".class")
                            and name != "module-info.class" and not name.endswith("/module-info.class")):
                        raise RuntimeError(f"{variant} jar has duplicate loadable class {name}")
                return entries
        first, second = contents("baseline"), contents("candidate")
        if set(first) != set(second):
            raise RuntimeError("Jar entry sets differ between baseline and candidate")
        # Preserve normal JMH packaging, including duplicate resources. Compare
        # every occurrence in ZIP order, rather than reading only the last name.
        self.manifest["duplicate_jar_resources"] = {
            name: hashes for name, hashes in first.items() if len(hashes) > 1}
        differences = sorted(name for name in first if first[name] != second[name])
        self.manifest["jar_entry_differences"] = differences
        changed_classes = [name for name in differences if name.endswith(".class")]
        if changed_classes != ["org/hibernate/graph/EntityGraphs.class"]:
            raise RuntimeError("Bytecode differs outside the intended EntityGraphs class")
        # Dependency/resource divergence would also confound the comparison.
        if differences != changed_classes:
            raise RuntimeError("Non-class jar resources differ between the variants")
        self.save()

    def measure(self):
        for case_index, (method, parameter, value) in enumerate(CASES):
            case_id = f"{method}-{value}"
            for pair in range(3):
                order = ("baseline", "candidate") if (case_index + pair) % 2 == 0 else ("candidate", "baseline")
                print(f"Measuring {case_id}, pair {pair + 1}/3, {' then '.join(order)}", flush=True)
                for variant in order:
                    label = f"{case_id}-pair{pair + 1}-{variant}"
                    raw = self.output / "raw" / f"{label}.json"
                    tree = self.baseline if variant == "baseline" else self.candidate
                    cwd = tree / "hibernate-core"
                    jar = os.path.relpath(self.output / "jars" / f"{variant}.jar", cwd)
                    raw_relative = os.path.relpath(raw, cwd)
                    self.run(["java", "-jar", jar, "^" + re.escape(BENCHMARK + "." + method) + "$",
                              "-p", f"{parameter}={value}", "-f", "1", "-wi", "3", "-w", "1s",
                              "-i", "5", "-r", "1s", "-t", "1", "-bm", "avgt", "-tu", "ns",
                              "-foe", "true", "-jvmArgs", JVM_ARGS, "-rf", "json", "-rff", raw_relative],
                             cwd, label, timeout=60)
                    result = json.loads(raw.read_text())
                    if not isinstance(result, list) or len(result) != 1:
                        raise RuntimeError(f"Expected one JMH result for {label}")
                    result = result[0]
                    raw_data = result["primaryMetric"]["rawData"]
                    if (result["benchmark"] != BENCHMARK + "." + method
                            or result["params"] != {parameter: value}
                            or result["forks"] != 1 or result["threads"] != 1
                            or result["warmupIterations"] != 3 or result["measurementIterations"] != 5
                            or result["mode"] != "avgt" or result["primaryMetric"]["scoreUnit"] != "ns/op"
                            or len(raw_data) != 1 or len(raw_data[0]) != 5
                            or not all(isinstance(x, (int, float)) and math.isfinite(x) and x > 0 for x in raw_data[0])):
                        raise RuntimeError(f"Unexpected or incomplete JMH measurements for {label}")
                    self.results.append({"case": case_id, "variant": variant, "pair": pair + 1,
                                         "order": list(order), "raw_json": raw.relative_to(self.output).as_posix(),
                                         "raw_sha256": sha256(raw), "fork_mean_ns": statistics.mean(raw_data[0]),
                                         "iterations_ns": raw_data[0]})
                    self.save()

    def summarize(self):
        rows = []
        for method, _, value in CASES:
            case_id = f"{method}-{value}"
            selected = [result for result in self.results if result["case"] == case_id]
            baseline = [next(result["fork_mean_ns"] for result in selected
                             if result["variant"] == "baseline" and result["pair"] == pair) for pair in (1, 2, 3)]
            candidate = [next(result["fork_mean_ns"] for result in selected
                              if result["variant"] == "candidate" and result["pair"] == pair) for pair in (1, 2, 3)]
            ratios = [after / before for before, after in zip(baseline, candidate)]
            rows.append({"case": case_id, "baseline_fork_means_ns": baseline,
                         "candidate_fork_means_ns": candidate, "paired_candidate_over_baseline": ratios,
                         "geometric_mean_ratio": math.exp(statistics.mean(math.log(ratio) for ratio in ratios)),
                         "minimum_ratio": min(ratios), "maximum_ratio": max(ratios)})
        write_json(self.output / "summary.json", {"note": "Descriptive paired fork ratios only; no timing gate, p-value, or application-wide claim.", "cases": rows})
        with (self.output / "fork-means.csv").open("w", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(["case", "pair", "variant", "fork_mean_ns", "order"])
            for result in self.results:
                writer.writerow([result["case"], result["pair"], result["variant"],
                                 result["fork_mean_ns"], "/".join(result["order"])])

    def execute(self):
        try:
            self.prepare()
            self.build("baseline", self.baseline)
            self.build("candidate", self.candidate)
            self.verify_jars()
            self.measure()
            self.summarize()
            self.manifest["status"] = "complete"
            print(f"Completed {len(self.results)} single-fork measurements; see summary.json and raw JSON.", flush=True)
        except BaseException as error:
            self.manifest["status"] = "failed"
            self.manifest["error"] = f"{type(error).__name__}: {error}"
            raise
        finally:
            self.manifest["finished_utc"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
            self.save()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    Experiment(args).execute()


if __name__ == "__main__":
    main()
