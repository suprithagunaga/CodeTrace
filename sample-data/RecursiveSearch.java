package samples;

/** Recursive binary search: same idea as BinarySearch.java, different structure. */
public class RecursiveSearch {

    public static int find(int[] sorted, int target) {
        return find(sorted, target, 0, sorted.length - 1);
    }

    private static int find(int[] sorted, int target, int from, int to) {
        if (from > to) {
            return -1;
        }
        int middle = (from + to) >>> 1;
        if (sorted[middle] == target) {
            return middle;
        }
        return sorted[middle] < target
                ? find(sorted, target, middle + 1, to)
                : find(sorted, target, from, middle - 1);
    }
}
