"""Developer-only, local screening of a pinned Chinese masked LM on public fixtures.

Requires the host's PyTorch >= 2.6; no Android dependency or runtime download is added.
Run with --download once to fetch and verify the research artifacts under build/.
Supports only the pinned UER Tiny and Mini WWM BERT configurations.
FP16 storage is measured independently of FP32 CPU execution and Android packaging.
"""
import argparse
import hashlib
import json
import math
import platform
import time
import urllib.request
from pathlib import Path

import torch
import torch.nn.functional as F

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "build" / "model-evaluation"
MODELS = {
    "tiny": {
        "revision": "6a4e70f3f8bcc2a64ce655e92ceeebb10ad1cafa",
        "shape": (128, 2, 2),
        "weights": (12841159, "sha256", "35f53ca6d452c8d5be3f7886817cdbe898256959086f756bec666200bc72d4de"),
        "config": (621, "git", "b3127dc1a8ce87832b094e2dd59e8dd27a1c1e56"),
    },
    "mini": {
        "revision": "5e567169018f5cf84cf1d85a2b391c8d51211eda",
        "shape": (256, 4, 4),
        "weights": (35184327, "sha256", "47727c489bfa5c57b53149c83b85a35655b5e49f7355202e272ec50c9c4467d6"),
        "config": (622, "git", "45b99f791abaa4d9ca8ad93c6f7907b762cca7bf"),
    },
}
SHARED_ARTIFACTS = {
    "vocab.txt": (109540, "git", "ca4f9781030019ab9b253c6dcb8c7878b6dc87a5"),
}
# Constructed homophone choices; correct item is first for labels only, never for scoring.
FIXTURES = [
    ("我买了一件新", "衣服", "依附"), ("老师正在传授", "知识", "芝士"),
    ("面包上放了一片", "芝士", "知识"), ("请遵守交通", "规则", "归责"),
    ("我们需要提高工作", "效率", "效力"), ("这个团队很有", "实力", "视力"),
    ("医生检查我的", "视力", "势力"), ("周末我们一起去", "公园", "公元"),
    ("地球围绕太阳", "公转", "工专"), ("小船停在河流", "岸边", "案编"),
    ("老师让大家保持", "安静", "安靖"), ("我想表达我的", "意见", "易见"),
    ("两位朋友终于再次", "相见", "镶嵌"), ("桌上的花瓶是一件", "古董", "股东"),
    ("他购买股票成为公司", "股东", "古董"), ("我们坐在一起讨论", "事情", "实情"),
    ("请把事情的", "实情", "诗情"), ("清晨的树林非常", "宁静", "凝净"),
    ("工程师正在设计", "程序", "承续"), ("他在图书馆认真", "学习", "雪洗"),
    ("乡村的空气十分", "清新", "倾心"), ("小朋友喜欢听童话", "故事", "股市"),
    ("投资者关注今天的", "股市", "故事"), ("我们需要互相", "理解", "礼节"),
]
EIGHT_CHOICES = ("股市", "故事", "古诗", "股式", "固始", "鼓师", "谷食", "古事")


def verify_artifacts(model, cache, download):
    spec = MODELS[model]
    artifacts = dict(SHARED_ARTIFACTS, **{"pytorch_model.bin": spec["weights"], "config.json": spec["config"]})
    cache.mkdir(parents=True, exist_ok=True)
    for name, (size, algorithm, expected) in artifacts.items():
        target = cache / name
        if not target.exists() and download:
            url = f"https://huggingface.co/uer/roberta-{model}-wwm-chinese-cluecorpussmall/resolve/{spec['revision']}/{name}"
            with urllib.request.urlopen(url, timeout=60) as response:
                data = response.read(size + 1)
        else:
            if target.stat().st_size != size:
                raise ValueError("Research artifact size mismatch")
            data = target.read_bytes()
        digest = hashlib.sha256(data).hexdigest() if algorithm == "sha256" else hashlib.sha1(
            f"blob {len(data)}\0".encode() + data).hexdigest()
        if len(data) != size or digest != expected:
            raise ValueError("Research artifact verification failed")
        if not target.exists():
            temporary = target.with_suffix(target.suffix + ".part")
            temporary.write_bytes(data)
            temporary.replace(target)
    return sum(specification[0] for specification in artifacts.values())


def storage_key(value):
    return (value.untyped_storage().data_ptr(), value.storage_offset(), tuple(value.shape), tuple(value.stride()))


def convert_weights(weights, dtype):
    # Preserve the tied embedding/decoder and prediction bias to avoid doubling storage.
    converted = {}
    result = {}
    for name, value in weights.items():
        key = storage_key(value)
        if key not in converted:
            converted[key] = value.to(dtype=dtype) if value.is_floating_point() else value.clone()
        result[name] = converted[key]
    return result


class MaskedScorer:
    def __init__(self, model, cache, checkpoint="pytorch_model.bin"):
        self.model = model
        self.weights = torch.load(cache / checkpoint, map_location="cpu", weights_only=True)
        self.weights = convert_weights(self.weights, torch.float32)
        with (cache / "vocab.txt").open(encoding="utf-8") as stream:
            self.vocab = {word.rstrip("\r\n"): index for index, word in enumerate(stream)}
        config = json.loads((cache / "config.json").read_text(encoding="utf-8"))
        self.hidden, self.layers, self.heads = MODELS[model]["shape"]
        assert (config["hidden_size"], config["num_hidden_layers"], config["num_attention_heads"]) == MODELS[model]["shape"]
        assert config["intermediate_size"] == self.hidden * 4 and config["hidden_act"] == "gelu"
        assert config["vocab_size"] == len(self.vocab) == 21128
        assert config["layer_norm_eps"] == 1e-12

    def verify_published_example(self):
        tokens = list("北京是") + ["[MASK]"] + list("国的首都。")
        ids = torch.tensor([[self.vocab["[CLS]"]] + [self.vocab[ch] for ch in tokens] + [self.vocab["[SEP]"]]])
        probabilities = self.forward(ids)[0, 4].softmax(-1)
        assert probabilities.argmax().item() == self.vocab["中"]
        # The example printed in both model cards actually uses Tiny, not Mini.
        if self.model == "tiny":
            assert abs(probabilities[self.vocab["中"]].item() - 0.2942287) < 0.001

    def linear(self, values, name):
        return F.linear(values, self.weights[name + ".weight"], self.weights[name + ".bias"])

    def norm(self, values, name):
        return F.layer_norm(values, (self.hidden,), self.weights[name + ".weight"], self.weights[name + ".bias"], 1e-12)

    def forward(self, ids, masked_positions=None):
        batch, length = ids.shape
        w = self.weights
        values = F.embedding(ids, w["bert.embeddings.word_embeddings.weight"])
        values += w["bert.embeddings.position_embeddings.weight"][:length]
        values += w["bert.embeddings.token_type_embeddings.weight"][0]
        values = self.norm(values, "bert.embeddings.LayerNorm")
        for layer in range(self.layers):
            prefix = f"bert.encoder.layer.{layer}"
            projected = [self.linear(values, prefix + ".attention.self." + name)
                         .reshape(batch, length, self.heads, 64).transpose(1, 2) for name in ("query", "key", "value")]
            query, key, value = projected
            attention = torch.softmax(query @ key.transpose(-1, -2) / 8.0, dim=-1) @ value
            attention = attention.transpose(1, 2).reshape(batch, length, self.hidden)
            values = self.norm(values + self.linear(attention, prefix + ".attention.output.dense"), prefix + ".attention.output.LayerNorm")
            intermediate = F.gelu(self.linear(values, prefix + ".intermediate.dense"))
            values = self.norm(values + self.linear(intermediate, prefix + ".output.dense"), prefix + ".output.LayerNorm")
        if masked_positions is not None:
            values = values[torch.arange(batch), masked_positions]
        values = self.norm(F.gelu(self.linear(values, "cls.predictions.transform.dense")), "cls.predictions.transform.LayerNorm")
        return self.linear(values, "cls.predictions.decoder")

    def score(self, context, choice):
        if not 1 <= len(choice) <= 8:
            raise ValueError("Screening candidate length out of bounds")
        tokens = [self.vocab[ch] for ch in context[-24:] + choice]
        ids = torch.tensor([[self.vocab["[CLS]"]] + tokens + [self.vocab["[SEP]"]]]).repeat(len(choice), 1)
        positions = torch.arange(len(tokens) - len(choice) + 1, len(tokens) + 1)
        rows = torch.arange(len(choice))
        expected = ids[rows, positions].clone()
        ids[rows, positions] = self.vocab["[MASK]"]
        predictions = self.forward(ids, positions)
        return predictions.log_softmax(-1)[rows, expected].mean().item()


def percentile(values, fraction):
    return round(sorted(values)[max(0, math.ceil(len(values) * fraction) - 1)], 3)


def verify_projection(scorer):
    ids = torch.tensor([[scorer.vocab[ch] for ch in ("[CLS]", "北", "京", "[MASK]", "[SEP]")]])
    full = scorer.forward(ids)[0, 3]
    selected = scorer.forward(ids, torch.tensor([3]))[0]
    torch.testing.assert_close(selected, full, rtol=1e-4, atol=1e-4)


def evaluate(model, cache, checkpoint, source_bytes):
    start = time.perf_counter()
    scorer = MaskedScorer(model, cache, checkpoint)
    load_ms = (time.perf_counter() - start) * 1000
    counts = {}
    for line in (ROOT / "engine-rime/src/main/assets/rime/essay.txt").read_text(encoding="utf-8").splitlines():
        columns = line.split("\t")
        if len(columns) == 2:
            counts[columns[0]] = float(columns[1])
    def prior(word):
        return sum(math.log1p(counts.get(ch, 0)) for ch in word) / len(word)
    correct = prior_correct = 0
    durations = []
    pair_scores = []
    with torch.inference_mode():
        scorer.verify_published_example()
        verify_projection(scorer)
        for _ in range(5):
            scorer.score("北京是", "中国")
        for context, expected, alternative in FIXTURES:
            start = time.perf_counter()
            scores = [scorer.score(context, word) for word in (expected, alternative)]
            durations.append((time.perf_counter() - start) * 1000)
            pair_scores.append(scores)
            correct += scores[0] > scores[1]
            prior_correct += prior(expected) > prior(alternative)
        eight_durations = []
        for iteration in range(55):
            start = time.perf_counter()
            for choice in EIGHT_CHOICES:
                scorer.score("投资者关注今天的", choice)
            if iteration >= 5:
                eight_durations.append((time.perf_counter() - start) * 1000)
    storages = {value.untyped_storage().data_ptr(): value.untyped_storage().nbytes() for value in scorer.weights.values()}
    weights_bytes = (cache / checkpoint).stat().st_size
    report = {
        "model": f"uer/roberta-{model}-wwm-chinese-cluecorpussmall", "revision": MODELS[model]["revision"],
        "host": platform.machine(), "torch": torch.__version__, "checkpoint": checkpoint,
        "threads": 1, "execution_dtype": "float32", "source_artifact_bytes": source_bytes,
        "weights_and_vocab_bytes": weights_bytes + (cache / "vocab.txt").stat().st_size + (cache / "config.json").stat().st_size,
        "checkpoint_sha256": hashlib.sha256((cache / checkpoint).read_bytes()).hexdigest(),
        "unique_tensor_storage_bytes": sum(storages.values()), "load_ms": round(load_ms, 3),
        "fixtures": len(FIXTURES), "context_model_correct": correct, "character_frequency_prior_correct": prior_correct,
        "pair_p50_ms": percentile(durations, .5), "pair_p95_ms": percentile(durations, .95),
        "eight_candidates_p50_ms": percentile(eight_durations, .5),
        "eight_candidates_p95_ms": percentile(eight_durations, .95), "eight_candidates_samples": len(eight_durations),
        "integration_approved": False,
        "limitations": ["Constructed screening set, not held-out IME accuracy", "No Android inference or memory measurement",
                        "Model repository has no explicit weight license", "Frequency prior is not the Rime decoder",
                        "FP16 checkpoint storage does not establish Android operator support or FP16 execution speed"],
    }
    return report, pair_scores


def evaluate_variants(model, cache, source_bytes, fp16):
    baseline, original_scores = evaluate(model, cache, "pytorch_model.bin", source_bytes)
    reports = [baseline]
    if fp16:
        weights = torch.load(cache / "pytorch_model.bin", map_location="cpu", weights_only=True)
        converted = convert_weights(weights, torch.float16)
        torch.save(converted, cache / "pytorch_model_fp16.bin")
        del weights, converted
        reduced, reduced_scores = evaluate(model, cache, "pytorch_model_fp16.bin", source_bytes)
        reduced["fp32_pair_order_changes"] = sum(
            (before[0] > before[1]) != (after[0] > after[1]) for before, after in zip(original_scores, reduced_scores))
        reduced["max_pair_score_delta"] = round(max(
            abs(before - after) for pair_a, pair_b in zip(original_scores, reduced_scores)
            for before, after in zip(pair_a, pair_b)), 6)
        reports.append(reduced)
    return reports


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--download", action="store_true")
    parser.add_argument("--model", choices=MODELS, default="mini")
    parser.add_argument("--fp16", action="store_true", help="Also serialize FP16 weights, reload as FP32 and compare")
    args = parser.parse_args()
    assert tuple(map(int, torch.__version__.split("+")[0].split(".")[:2])) >= (2, 6)
    cache = CACHE / args.model
    source_bytes = verify_artifacts(args.model, cache, args.download)
    torch.set_num_threads(1)
    report = evaluate_variants(args.model, cache, source_bytes, args.fp16)
    (cache / "result.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
