package samples;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Unrelated code: a tiny library catalogue. Its sort helper (lines 34-52) was copied from
 * BubbleSort.java, so this file partially overlaps with the bubble sort submissions.
 */
public class LibraryManager {

    private final List<String> titles = new ArrayList<>();
    private final List<Integer> years = new ArrayList<>();

    public void addBook(String title, int year) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        titles.add(title.strip());
        years.add(year);
    }

    public Optional<String> findByPrefix(String prefix) {
        return titles.stream().filter(t -> t.startsWith(prefix)).findFirst();
    }

    public int[] sortedYears() {
        int[] values = years.stream().mapToInt(Integer::intValue).toArray();
        sort(values);
        return values;
    }

    private static void sort(int[] values) {
        int n = values.length;
        for (int i = 0; i < n - 1; i++) {
            boolean swapped = false;
            for (int j = 0; j < n - i - 1; j++) {
                if (values[j] > values[j + 1]) {
                    int tmp = values[j];
                    values[j] = values[j + 1];
                    values[j + 1] = tmp;
                    swapped = true;
                }
            }
            if (!swapped) {
                break;
            }
        }
    }

    public int size() {
        return titles.size();
    }
}
