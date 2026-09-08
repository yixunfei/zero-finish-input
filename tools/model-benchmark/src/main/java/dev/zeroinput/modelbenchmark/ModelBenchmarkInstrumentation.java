package dev.zeroinput.modelbenchmark;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Looper;
import android.os.Process;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtLoggingLevel;
import ai.onnxruntime.OrtSession;

/** Isolated, public-fixture-only evaluation. Never attaches to an input editor. */
public final class ModelBenchmarkInstrumentation extends Instrumentation {
    private static final int SAMPLE_COUNT = 100;
    private String selectedModel = "mini";

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        if (arguments != null && "tiny".equals(arguments.getString("model"))) selectedModel = "tiny";
        if (arguments != null && "mini-int8".equals(arguments.getString("model"))) selectedModel = "mini-int8";
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException();
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            JSONObject report = benchmark(selectedModel);
            result.putString("stream", "\n" + report.toString(2) + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Exception failure) {
            // Model/fixture failures cannot expose editor text: this app has no editor APIs.
            result.putString("stream", "Benchmark failed: " + failure.getClass().getSimpleName());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private JSONObject benchmark(String name) throws Exception {
        JSONObject manifest = new JSONObject(readText(name + "/manifest.json"));
        JSONArray fixtures = new JSONArray(readText(name + "/fixtures.json"));
        File model = copyVerifiedModel(name, manifest.getString("graph_sha256"));
        int baselinePss = pssKb();
        long loadStart = System.nanoTime();
        JSONObject report = new JSONObject();
        OrtEnvironment environment = OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_FATAL);
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(1);
            options.setInterOpNumThreads(1);
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
            try (OrtSession session = environment.createSession(model.getAbsolutePath(), options)) {
                double loadMs = (System.nanoTime() - loadStart) / 1_000_000.0;
                int correct = verifyFixtures(environment, session, fixtures);
                Fixture eight = new Fixture(fixtures.getJSONObject(fixtures.length() - 2));
                for (int iteration = 0; iteration < 10; iteration++) infer(environment, session, eight);
                long cpuStart = Process.getElapsedCpuTime();
                double[] durations = new double[SAMPLE_COUNT];
                for (int iteration = 0; iteration < SAMPLE_COUNT; iteration++) {
                    long start = System.nanoTime();
                    infer(environment, session, eight);
                    durations[iteration] = (System.nanoTime() - start) / 1_000_000.0;
                }
                long cpuMs = Process.getElapsedCpuTime() - cpuStart;
                Arrays.sort(durations);
                double p95 = durations[(int) Math.ceil(SAMPLE_COUNT * .95) - 1];
                report.put("model", name).put("api", Build.VERSION.SDK_INT).put("android", Build.VERSION.RELEASE);
                report.put("abi", Build.SUPPORTED_ABIS[0]).put("runtime", "onnxruntime-android:1.26.0");
                report.put("threads", 1).put("graph_bytes", model.length()).put("load_ms", loadMs);
                report.put("quality_fixtures", fixtures.length() - 2).put("context_model_correct", correct);
                report.put("samples", SAMPLE_COUNT).put("eight_candidates_p50_ms", durations[SAMPLE_COUNT / 2 - 1]);
                report.put("context_characters", 16).put("candidate_characters", 2);
                report.put("eight_candidates_p95_ms", p95).put("eight_candidates_max_ms", durations[SAMPLE_COUNT - 1]);
                report.put("eight_candidates_cpu_ms", cpuMs).put("baseline_pss_kb", baselinePss);
                report.put("loaded_pss_kb", pssKb()).put("short_latency_gate_passed", p95 <= 10.0);
                Fixture maximum = new Fixture(fixtures.getJSONObject(fixtures.length() - 1));
                JSONObject maximumReport = sampleMaximum(environment, session, maximum);
                report.put("maximum_workload", maximumReport);
                report.put("latency_gate_passed", p95 <= 10.0 && maximumReport.getDouble("p95_ms") <= 10.0);
                report.put("graph_sha256", manifest.getString("graph_sha256"));
                report.put("integration_approved", false);
            }
        }
        report.put("closed_pss_kb", pssKb());
        return report;
    }

    private JSONObject sampleMaximum(OrtEnvironment environment, OrtSession session, Fixture fixture) throws Exception {
        for (int iteration = 0; iteration < 5; iteration++) infer(environment, session, fixture);
        long cpuStart = Process.getElapsedCpuTime();
        double[] durations = new double[50];
        for (int iteration = 0; iteration < durations.length; iteration++) {
            long start = System.nanoTime();
            infer(environment, session, fixture);
            durations[iteration] = (System.nanoTime() - start) / 1_000_000.0;
        }
        long cpuMs = Process.getElapsedCpuTime() - cpuStart;
        Arrays.sort(durations);
        return new JSONObject().put("samples", durations.length).put("context_characters", 24)
                .put("candidate_count", 8).put("candidate_characters", 8)
                .put("p50_ms", durations[24]).put("p95_ms", durations[47])
                .put("max_ms", durations[49]).put("cpu_ms", cpuMs).put("pss_kb", pssKb());
    }

    private int verifyFixtures(OrtEnvironment environment, OrtSession session, JSONArray fixtures) throws Exception {
        int correct = 0;
        for (int index = 0; index < fixtures.length(); index++) {
            JSONObject data = fixtures.getJSONObject(index);
            Fixture fixture = new Fixture(data);
            float[] scores = infer(environment, session, fixture);
            JSONArray expected = data.getJSONArray("expected_scores");
            if (scores.length != expected.length()) throw new IllegalStateException();
            for (int row = 0; row < scores.length; row++) {
                if (!Float.isFinite(scores[row]) || Math.abs(scores[row] - expected.getDouble(row)) > .025) {
                    throw new IllegalStateException();
                }
            }
            if (data.getBoolean("quality_fixture")) {
                int length = data.getInt("candidate_length");
                float first = 0, second = 0;
                for (int letter = 0; letter < length; letter++) {
                    first += scores[letter];
                    second += scores[length + letter];
                }
                if (first > second) correct++;
            }
        }
        return correct;
    }

    private float[] infer(OrtEnvironment environment, OrtSession session, Fixture fixture) throws Exception {
        long[] shape = {fixture.positions.length};
        try (OnnxTensor ids = OnnxTensor.createTensor(environment, fixture.ids);
             OnnxTensor positions = OnnxTensor.createTensor(environment, LongBuffer.wrap(fixture.positions), shape);
             OnnxTensor targets = OnnxTensor.createTensor(environment, LongBuffer.wrap(fixture.targets), shape)) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", ids);
            inputs.put("positions", positions);
            inputs.put("target_ids", targets);
            try (OrtSession.Result result = session.run(inputs)) {
                return (float[]) result.get(0).getValue();
            }
        }
    }

    private File copyVerifiedModel(String name, String expected) throws Exception {
        File destination = new File(getTargetContext().getCacheDir(), name + ".onnx");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        int total = 0;
        try (InputStream source = getContext().getAssets().open(name + "/model.onnx");
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) != -1) {
                total += count;
                if (total > 20_000_000) throw new IllegalStateException();
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
        }
        StringBuilder actual = new StringBuilder();
        for (byte value : digest.digest()) actual.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        if (!actual.toString().equals(expected)) throw new IllegalStateException();
        return destination;
    }

    private String readText(String name) throws Exception {
        try (InputStream source = getContext().getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) != -1) {
                if (output.size() + count > 1_000_000) throw new IllegalStateException();
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static int pssKb() {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        return info.getTotalPss();
    }

    private static final class Fixture {
        final long[][] ids;
        final long[] positions;
        final long[] targets;

        Fixture(JSONObject source) throws Exception {
            JSONArray rows = source.getJSONArray("input_ids");
            JSONArray sourcePositions = source.getJSONArray("positions");
            JSONArray sourceTargets = source.getJSONArray("target_ids");
            if (rows.length() < 1 || rows.length() > 64 || rows.length() != sourcePositions.length()
                    || rows.length() != sourceTargets.length()) throw new IllegalStateException();
            ids = new long[rows.length()][];
            positions = new long[rows.length()];
            targets = new long[rows.length()];
            for (int row = 0; row < rows.length(); row++) {
                JSONArray columns = rows.getJSONArray(row);
                if (columns.length() < 3 || columns.length() > 34
                        || (row > 0 && columns.length() != ids[0].length)) throw new IllegalStateException();
                ids[row] = new long[columns.length()];
                for (int col = 0; col < columns.length(); col++) {
                    ids[row][col] = columns.getLong(col);
                    if (ids[row][col] < 0 || ids[row][col] >= 21128) throw new IllegalStateException();
                }
                positions[row] = sourcePositions.getLong(row);
                targets[row] = sourceTargets.getLong(row);
                if (positions[row] < 1 || positions[row] >= columns.length() - 1
                        || targets[row] < 0 || targets[row] >= 21128) throw new IllegalStateException();
            }
        }
    }
}
