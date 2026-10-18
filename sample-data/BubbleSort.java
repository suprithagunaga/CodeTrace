package samples;

/**
 * Original bubble sort implementation used as the reference submission.
 */
public class BubbleSort {

    public static void sort(int[] values) {
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

    public static void main(String[] args) {
        int[] data = {5, 3, 8, 1, 9, 2};
        sort(data);
        for (int value : data) {
            System.out.print(value + " ");
        }
        System.out.println();
    }
}
