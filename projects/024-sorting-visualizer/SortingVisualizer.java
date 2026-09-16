package com.randomjava.projects.sortingvisualizer;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Sorting Visualiser - runs a sort and records a frame at every comparison and
 * swap, so the whole run can be stepped through afterwards.
 *
 * <p>Recording frames rather than animating in place is what lets the same code
 * drive a terminal and a browser. The algorithms do not know they are being
 * watched; they just call {@link #capture} as they go.
 */
public final class SortingVisualizer implements Project {

    public static final Meta META = new Meta(24, "sorting-visualizer", "Sorting Visualizer", "Algorithms and Data Structures", Kind.GRID,
            Difficulty.INTERMEDIATE, "Step through bubble, insertion, selection, merge and quick sort.",
            "", true);

    /** Stops a large array from recording a million frames. */
    private static final int MAX_FRAMES = 20_000;

    public static final List<String> ALGORITHMS =
            List.of("bubble", "insertion", "selection", "merge", "quick");

    private int[] values = new int[0];
    private String algorithm = "bubble";
    private final List<int[]> frames = new ArrayList<>();
    private final List<int[]> marks = new ArrayList<>();
    private int frameIndex;
    private int comparisons;
    private int writes;

    @Override
    public Meta meta() {
        return META;
    }

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    public void generate(int count) {
        int size = Math.max(4, Math.min(80, count));
        Random random = new Random();
        values = new int[size];
        for (int i = 0; i < size; i++) {
            values[i] = random.nextInt(99) + 1;
        }
        prepare();
    }

    public void setAlgorithm(String name) {
        String cleaned = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (!ALGORITHMS.contains(cleaned)) {
            throw new IllegalArgumentException(
                    "Choose one of: " + String.join(", ", ALGORITHMS));
        }
        algorithm = cleaned;
        prepare();
    }

    /** Re-runs the chosen algorithm on a copy and records every frame. */
    private void prepare() {
        frames.clear();
        marks.clear();
        frameIndex = 0;
        comparisons = 0;
        writes = 0;
        if (values.length == 0) {
            return;
        }
        int[] working = values.clone();
        capture(working, -1, -1);
        switch (algorithm) {
            case "bubble" -> bubbleSort(working);
            case "insertion" -> insertionSort(working);
            case "selection" -> selectionSort(working);
            case "merge" -> mergeSort(working, 0, working.length - 1, new int[working.length]);
            default -> quickSort(working, 0, working.length - 1);
        }
        capture(working, -1, -1);
    }

    private void capture(int[] snapshot, int a, int b) {
        if (frames.size() >= MAX_FRAMES) {
            return;
        }
        frames.add(snapshot.clone());
        marks.add(new int[]{a, b});
    }

    // ------------------------------------------------------------------
    // The algorithms
    // ------------------------------------------------------------------

    private void bubbleSort(int[] data) {
        for (int end = data.length - 1; end > 0; end--) {
            boolean swapped = false;
            for (int i = 0; i < end; i++) {
                comparisons++;
                capture(data, i, i + 1);
                if (data[i] > data[i + 1]) {
                    swap(data, i, i + 1);
                    swapped = true;
                    capture(data, i, i + 1);
                }
            }
            if (!swapped) {
                return;
            }
        }
    }

    private void insertionSort(int[] data) {
        for (int i = 1; i < data.length; i++) {
            int value = data[i];
            int j = i - 1;
            while (j >= 0) {
                comparisons++;
                capture(data, j, i);
                if (data[j] <= value) {
                    break;
                }
                data[j + 1] = data[j];
                writes++;
                j--;
                capture(data, j + 1, i);
            }
            data[j + 1] = value;
            writes++;
            capture(data, j + 1, -1);
        }
    }

    private void selectionSort(int[] data) {
        for (int i = 0; i < data.length - 1; i++) {
            int smallest = i;
            for (int j = i + 1; j < data.length; j++) {
                comparisons++;
                capture(data, smallest, j);
                if (data[j] < data[smallest]) {
                    smallest = j;
                }
            }
            if (smallest != i) {
                swap(data, i, smallest);
                capture(data, i, smallest);
            }
        }
    }

    private void mergeSort(int[] data, int low, int high, int[] scratch) {
        if (low >= high) {
            return;
        }
        int middle = (low + high) >>> 1;
        mergeSort(data, low, middle, scratch);
        mergeSort(data, middle + 1, high, scratch);

        System.arraycopy(data, low, scratch, low, high - low + 1);
        int left = low;
        int right = middle + 1;
        for (int i = low; i <= high; i++) {
            comparisons++;
            if (left > middle) {
                data[i] = scratch[right++];
            } else if (right > high) {
                data[i] = scratch[left++];
            } else if (scratch[left] <= scratch[right]) {
                data[i] = scratch[left++];
            } else {
                data[i] = scratch[right++];
            }
            writes++;
            capture(data, i, -1);
        }
    }

    private void quickSort(int[] data, int low, int high) {
        if (low >= high) {
            return;
        }
        int pivot = data[high];
        int boundary = low - 1;
        for (int i = low; i < high; i++) {
            comparisons++;
            capture(data, i, high);
            if (data[i] <= pivot) {
                boundary++;
                if (boundary != i) {
                    swap(data, boundary, i);
                    capture(data, boundary, i);
                }
            }
        }
        swap(data, boundary + 1, high);
        capture(data, boundary + 1, high);
        quickSort(data, low, boundary);
        quickSort(data, boundary + 2, high);
    }

    private void swap(int[] data, int a, int b) {
        int temp = data[a];
        data[a] = data[b];
        data[b] = temp;
        writes += 2;
    }

    // ------------------------------------------------------------------
    // Playback
    // ------------------------------------------------------------------

    public boolean step() {
        if (frameIndex < frames.size() - 1) {
            frameIndex++;
            return true;
        }
        return false;
    }

    public void jumpToEnd() {
        frameIndex = Math.max(0, frames.size() - 1);
    }

    public void rewind() {
        frameIndex = 0;
    }

    public int[] currentFrame() {
        return frames.isEmpty() ? values : frames.get(frameIndex);
    }

    private List<Integer> highlighted() {
        List<Integer> out = new ArrayList<>();
        if (frames.isEmpty()) {
            return out;
        }
        for (int index : marks.get(frameIndex)) {
            if (index >= 0) {
                out.add(index);
            }
        }
        return out;
    }

    public String status() {
        boolean capped = frames.size() >= MAX_FRAMES;
        return algorithm + " sort  |  frame " + (frameIndex + 1) + " of " + frames.size()
                + "  |  " + comparisons + " comparisons, " + writes + " writes"
                + (capped ? "  (frame capture capped)" : "");
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(io.askInt("how many values:", 4, 80, 16));
        List<String> options = new ArrayList<>(ALGORITHMS);
        int choice = io.menu("Algorithm", options);
        setAlgorithm(choice < 0 ? "bubble" : options.get(choice));

        io.println();
        draw(io);
        while (true) {
            int action = io.menu("Playback", List.of(
                    "Step forward", "Skip 10 frames", "Run to the end", "Rewind", "New values"));
            if (action < 0) {
                return;
            }
            switch (action) {
                case 0 -> step();
                case 1 -> {
                    for (int i = 0; i < 10; i++) {
                        step();
                    }
                }
                case 2 -> jumpToEnd();
                case 3 -> rewind();
                default -> generate(values.length);
            }
            io.println();
            draw(io);
        }
    }

    /** Draws the current frame as horizontal bars, marking the active indices. */
    private void draw(ConsoleUI io) {
        int[] frame = currentFrame();
        List<Integer> active = highlighted();
        int max = Arrays.stream(frame).max().orElse(1);
        for (int i = 0; i < frame.length; i++) {
            int width = Math.max(1, frame[i] * 40 / Math.max(1, max));
            String bar = (active.contains(i) ? "*" : "#").repeat(width);
            io.println(String.format("  %3d %s", frame[i], bar));
        }
        io.muted(status());
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    generate(Json.integer(body, "size", 24));
                    setAlgorithm(Json.str(body, "algorithm", algorithm));
                }
                case "algorithm" -> setAlgorithm(Json.str(body, "algorithm", algorithm));
                case "step" -> step();
                case "run" -> jumpToEnd();
                case "rewind" -> rewind();
                case "state" -> {
                    // fall through to the shared response below
                }
                default -> {
                    return Json.error("Unknown action: " + action);
                }
            }
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
        int[] frame = currentFrame();
        List<Integer> bars = new ArrayList<>(frame.length);
        for (int value : frame) {
            bars.add(value);
        }
        boolean finished = frameIndex >= frames.size() - 1;
        return Json.ok(
                "bars", bars,
                "highlight", highlighted(),
                "result", finished ? "Sorted" : algorithm + " sort running",
                "detail", status());
    }
}
