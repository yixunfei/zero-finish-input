"""Calibrate on the old public examples, then evaluate disjoint Rime reading groups.

Uses only the frozen public fixture file and the candidate capture produced by
ModelCandidateFixtureTest. No editor contents or personal dictionary are accepted.
The held-out split is evaluated once after selecting the confidence threshold.
"""
import hashlib
import importlib.util
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(ROOT / "build/model-evaluation/python-deps"))
import numpy as np
import onnxruntime as ort

SPEC = importlib.util.spec_from_file_location("model_export", ROOT / "tools/export-model-benchmark.py")
export = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(export)
CACHE = ROOT / "build/model-evaluation"


def read_public_capture():
    fixture_path = ROOT / "tools/model-quality-fixtures/cases.json"
    fixture_hash = hashlib.sha256(fixture_path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()
    assert fixture_hash == "010100daf7acbf9366f12c537288923b501f9400bb7827a61c3ad5a659841e79"
    source = json.loads(fixture_path.read_text(encoding="utf-8"))
    captured = json.loads((CACHE / "rime-candidates.json").read_text(encoding="utf-8"))
    assert len(source) == len(captured) == 118
    for expected, actual in zip(source, captured):
        assert all(actual[key] == value for key, value in expected.items())
        assert len(actual["candidates"]) <= 8
    calibration = {row["pinyin"] for row in source if row["split"] == "calibration"}
    heldout = {row["pinyin"] for row in source if row["split"] == "heldout"}
    assert not calibration & heldout
    return captured, fixture_hash


def candidate_reading(candidate):
    return candidate["comment"].replace(" ", "").replace("'", "")


def score_row(row, tokenizer, session):
    context = row["context"][-16:]
    candidates = row["candidates"]
    texts = [candidate["text"] for candidate in candidates]
    eligible = [index for index, candidate in enumerate(candidates)
                if candidate["kind"] == "STANDARD" and len(candidate["text"]) == 2
                and candidate_reading(candidate) == row["pinyin"]
                and all(ch in tokenizer.vocab for ch in candidate["text"])]
    scores = []
    if 0 in eligible and len(eligible) >= 2 and all(ch in tokenizer.vocab for ch in context):
        encoded = export.encode(tokenizer, context, [texts[index] for index in eligible])
        inputs = {key: np.array(value, dtype=np.int64) for key, value in encoded.items()}
        scores = session.run(None, inputs)[0].reshape(len(eligible), 2).mean(axis=1).tolist()
        assert all(np.isfinite(scores))
    return dict(row, eligible=eligible, scores=scores, texts=texts)


def select(row, margin):
    if len(row["scores"]) < 2:
        return 0
    order = sorted(range(len(row["scores"])), key=lambda index: -row["scores"][index])
    first, second = order[:2]
    if row["scores"][first] - row["scores"][second] < margin:
        return 0
    return row["eligible"][first]


def summarize(rows, margin):
    baseline = correct = regressions = promotions = covered = scoreable = 0
    for row in rows:
        expected, texts = row["expected"], row["texts"]
        chosen = select(row, margin)
        before = bool(texts) and texts[0] == expected
        after = bool(texts) and texts[chosen] == expected
        baseline += before
        correct += after
        regressions += before and not after
        promotions += chosen != 0
        covered += expected in texts
        scoreable += bool(row["scores"])
    return {"samples": len(rows), "baseline_correct": baseline, "model_correct": correct,
            "baseline_correct_regressions": regressions, "promotions": promotions,
            "expected_in_first_eight": covered, "scoreable": scoreable,
            "top_one_gain_points": (correct - baseline) * 100 / len(rows),
            "clean_regression_points": regressions * 100 / len(rows)}


def main():
    captured, fixture_hash = read_public_capture()
    model = CACHE / "android-assets/mini-int8/model.onnx"
    model_hash = hashlib.sha256(model.read_bytes()).hexdigest()
    assert model_hash == "5fb4dbe2c618e8757258253e10481ea9181e8a7b9a8efea03ee70c3a5ca19446"
    options = ort.SessionOptions()
    options.intra_op_num_threads = 1
    options.inter_op_num_threads = 1
    options.log_severity_level = 4
    session = ort.InferenceSession(str(model), options, providers=["CPUExecutionProvider"])
    tokenizer = export.evaluation.MaskedScorer("mini", CACHE / "mini")
    calibration = [score_row(row, tokenizer, session) for row in captured if row["split"] == "calibration"]
    trials = [(step / 4, summarize(calibration, step / 4)) for step in range(33)]
    eligible = [(margin, report) for margin, report in trials if report["baseline_correct_regressions"] == 0]
    margin, training = max(eligible, key=lambda trial: (trial[1]["model_correct"], trial[0]))
    policy = {"model_sha256": model_hash, "fixtures_sha256": fixture_hash,
              "minimum_winning_margin": margin, "context_characters": 16,
              "candidate_characters": 2, "candidate_limit": 8, "calibration": training}
    # Freeze before reading any model outputs for the held-out split.
    (CACHE / "rime-ranking-policy.json").write_text(json.dumps(policy, indent=2) + "\n", encoding="utf-8")
    heldout = [score_row(row, tokenizer, session) for row in captured if row["split"] == "heldout"]
    report = dict(policy, heldout=summarize(heldout, margin))
    report["quality_gate_passed"] = (report["heldout"]["top_one_gain_points"] >= 2
                                     and report["heldout"]["clean_regression_points"] <= .5)
    report["limitations"] = ["Constructed contexts, not population IME accuracy",
                             "Short Chinese words only; no personal candidates or continuous typing quality",
                             "Training-corpus overlap cannot be excluded for pretrained models",
                             "Host scoring uses the graph numerically checked by Android public-fixture tests"]
    (CACHE / "rime-quality-result.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
