"""Export pinned public research models for the isolated Android benchmark.

Requires host torch >= 2.6, onnx 1.19.0 and onnxruntime 1.26.0. Run the model
evaluator first. Files under build/ are generated artifacts; production staging
accepts only the approved Mini INT8 graph and vocabulary with pinned hashes.
No remote Python or model code is loaded.
"""
import argparse
import hashlib
import importlib.util
import json
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(ROOT / "build/model-evaluation/python-deps"))

import numpy as np
import onnx
import onnxruntime as ort
import torch
from onnx import TensorProto, helper, numpy_helper

SPEC = importlib.util.spec_from_file_location("model_evaluation", ROOT / "tools/evaluate-small-model.py")
evaluation = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(evaluation)
MAXIMUM_CHOICES = (
    "提高团队工作效率", "改善公司管理制度", "加强项目质量管理", "完善用户反馈渠道",
    "建设绿色生态城市", "保障人民生活安全", "推动科学技术创新", "发展现代农业生产",
)


class ExportScorer(torch.nn.Module):
    def __init__(self, scorer):
        super().__init__()
        self.scorer = scorer
        unique = {}
        for name, value in scorer.weights.items():
            key = evaluation.storage_key(value)
            if key not in unique:
                buffer_name = f"weight_{len(unique)}"
                self.register_buffer(buffer_name, value)
                unique[key] = buffer_name
            scorer.weights[name] = getattr(self, unique[key])

    def forward(self, input_ids, positions, target_ids):
        predictions = self.scorer.forward(input_ids, positions)
        return predictions.log_softmax(-1).gather(1, target_ids.unsqueeze(1)).squeeze(1)


def encode(scorer, context, choices):
    # These public fixtures have equal-length choices; padding/masks are unnecessary.
    assert 1 <= len(choices) <= 8 and len({len(word) for word in choices}) == 1
    rows, positions, targets = [], [], []
    for choice in choices:
        assert 1 <= len(choice) <= 8
        tokens = [scorer.vocab["[CLS]"]] + [scorer.vocab[ch] for ch in context[-24:] + choice]
        tokens += [scorer.vocab["[SEP]"]]
        for position in range(len(tokens) - len(choice) - 1, len(tokens) - 1):
            row = tokens.copy()
            targets.append(row[position])
            row[position] = scorer.vocab["[MASK]"]
            rows.append(row)
            positions.append(position)
    return {"input_ids": rows, "positions": positions, "target_ids": targets}


def half_initializers(source, destination):
    graph = onnx.load(source)
    casts = []
    for initializer in graph.graph.initializer:
        if initializer.data_type != TensorProto.FLOAT:
            continue
        original_name = initializer.name
        half_name = original_name + "_fp16_storage"
        values = numpy_helper.to_array(initializer).astype(np.float16)
        initializer.CopyFrom(numpy_helper.from_array(values, half_name))
        casts.append(helper.make_node("Cast", [half_name], [original_name], to=TensorProto.FLOAT))
    nodes = list(graph.graph.node)
    del graph.graph.node[:]
    graph.graph.node.extend(casts + nodes)
    onnx.checker.check_model(graph)
    onnx.save(graph, destination)


def export_graph(scorer, cache, destination):
    model = ExportScorer(scorer).eval()
    encoded = encode(scorer, "投资者关注今天的", evaluation.EIGHT_CHOICES)
    args = tuple(torch.tensor(encoded[name]) for name in ("input_ids", "positions", "target_ids"))
    fp32_path = cache / "scorer-fp32.onnx"
    torch.onnx.export(
        model, args, fp32_path, input_names=["input_ids", "positions", "target_ids"],
        output_names=["scores"], opset_version=17, dynamo=False, do_constant_folding=False,
        dynamic_axes={"input_ids": {0: "rows", 1: "length"}, "positions": {0: "rows"},
                      "target_ids": {0: "rows"}, "scores": {0: "rows"}},
    )
    half_initializers(fp32_path, destination)
    return model


def quantize_graph(cache, destination):
    from onnxruntime.quantization import QuantType, quantize_dynamic
    optimized = cache / "scorer-basic.onnx"
    options = ort.SessionOptions()
    options.intra_op_num_threads = 1
    options.inter_op_num_threads = 1
    options.log_severity_level = 4
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_BASIC
    options.optimized_model_filepath = str(optimized)
    session = ort.InferenceSession(str(cache / "scorer-fp32.onnx"), options, providers=["CPUExecutionProvider"])
    del session
    graph = onnx.load(optimized)
    initializers = {value.name: value for value in graph.graph.initializer}
    for node in graph.graph.node:
        if node.op_type == "Gather" and node.input[0] in initializers:
            # Quantized MatMul may transpose its initializer. Gather requires the
            # original embedding layout, so it needs separate quantized storage.
            original = initializers[node.input[0]]
            duplicate = onnx.TensorProto()
            duplicate.CopyFrom(original)
            duplicate.name = original.name + "_gather"
            graph.graph.initializer.append(duplicate)
            node.input[0] = duplicate.name
    onnx.save(graph, optimized)
    quantize_dynamic(optimized, destination, per_channel=True, weight_type=QuantType.QInt8,
                     op_types_to_quantize=["MatMul", "Gather"])
    onnx.checker.check_model(onnx.load(destination))


def compare_graph(reference, model_path, scorer, quantized):
    options = ort.SessionOptions()
    options.intra_op_num_threads = 1
    options.inter_op_num_threads = 1
    options.log_severity_level = 4
    session = ort.InferenceSession(str(model_path), options, providers=["CPUExecutionProvider"])
    fixtures, maximum_delta, correct = [], 0.0, 0
    reference_correct = order_changes = newly_correct = newly_incorrect = 0
    source = [(context, (expected, alternative)) for context, expected, alternative in evaluation.FIXTURES]
    source.append(("投资者正在密切关注近期交易活跃的", evaluation.EIGHT_CHOICES))
    source.append(("大家在会议上认真交流想法并且计划如何一起完成新的工作", MAXIMUM_CHOICES))
    for index, (context, choices) in enumerate(source):
        encoded = encode(scorer, context, choices)
        expected = reference(*(torch.tensor(encoded[name]) for name in encoded)).numpy()
        actual = session.run(None, {key: np.array(value, dtype=np.int64) for key, value in encoded.items()})[0]
        if quantized:
            assert np.all(np.isfinite(actual))
        else:
            np.testing.assert_allclose(actual, expected, atol=.025, rtol=.003)
        maximum_delta = max(maximum_delta, float(np.max(np.abs(actual - expected))))
        word_length = len(choices[0])
        if index < len(evaluation.FIXTURES):
            scores = actual.reshape(len(choices), word_length).mean(axis=1)
            before = expected.reshape(len(choices), word_length).mean(axis=1)
            was_correct, is_correct = bool(before[0] > before[1]), bool(scores[0] > scores[1])
            correct += is_correct
            reference_correct += was_correct
            order_changes += was_correct != is_correct
            newly_correct += is_correct and not was_correct
            newly_incorrect += was_correct and not is_correct
        fixtures.append(dict(encoded, expected_scores=actual.tolist(), candidate_length=word_length,
                             candidate_count=len(choices), quality_fixture=index < len(evaluation.FIXTURES)))
    workloads = {}
    for name, data in zip(("eight_short", "eight_maximum"), fixtures[-2:]):
        inputs = {key: np.array(data[key], dtype=np.int64) for key in ("input_ids", "positions", "target_ids")}
        durations = []
        for iteration in range(55):
            start = time.perf_counter()
            session.run(None, inputs)
            if iteration >= 5:
                durations.append((time.perf_counter() - start) * 1000)
        workloads[name] = {"p50_ms": evaluation.percentile(durations, .5),
                           "p95_ms": evaluation.percentile(durations, .95), "samples": len(durations)}
    return fixtures, {
        "runtime": ort.__version__,
        "execution": "single-thread CPU, dynamic INT8 MatMul/Gather" if quantized else "single-thread FP32 CPU, FP16 initializers",
        "graph_vs_reference_max_delta": maximum_delta, "context_model_correct": correct,
        "fixtures": len(evaluation.FIXTURES), "reference_correct": reference_correct,
        "pair_order_changes": order_changes, "newly_correct": newly_correct, "newly_incorrect": newly_incorrect,
        "workloads": workloads, "integration_approved": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", choices=evaluation.MODELS, default="mini")
    parser.add_argument("--int8", action="store_true", help="Measure dynamic MatMul/Gather quantization")
    args = parser.parse_args()
    cache = evaluation.CACHE / args.model
    evaluation.verify_artifacts(args.model, cache, download=False)
    torch.set_num_threads(1)
    variant = args.model + ("-int8" if args.int8 else "")
    destination = evaluation.CACHE / "android-assets" / variant
    destination.mkdir(parents=True, exist_ok=True)
    with torch.inference_mode():
        scorer = evaluation.MaskedScorer(args.model, cache)
        reference = export_graph(scorer, cache, destination / "model.onnx")
        if args.int8:
            quantize_graph(cache, destination / "model.onnx")
        fixtures, report = compare_graph(reference, destination / "model.onnx", scorer, args.int8)
    model_bytes = (destination / "model.onnx").read_bytes()
    report.update(model=variant, revision=evaluation.MODELS[args.model]["revision"],
                  graph_bytes=len(model_bytes), graph_sha256=hashlib.sha256(model_bytes).hexdigest(),
                  vocabulary_bytes=(cache / "vocab.txt").stat().st_size)
    assert report["graph_bytes"] + report["vocabulary_bytes"] <= 20_000_000
    (destination / "fixtures.json").write_text(json.dumps(fixtures) + "\n", encoding="utf-8")
    (destination / "manifest.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    report_name = "onnx-int8-result.json" if args.int8 else "onnx-result.json"
    (cache / report_name).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
