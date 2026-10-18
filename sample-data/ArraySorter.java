package samples;

// Same algorithm as BubbleSort.java with renamed identifiers,
// different formatting and different comments.
public class ArraySorter
{
  /* sorts the numbers in place */
  public static void arrange(int[] numbers)
  {
    int len = numbers.length;
    for (int a = 0; a < len - 1; a++)
    {
      boolean changed = false;
      for (int b = 0; b < len - a - 1; b++)
      {
        if (numbers[b] > numbers[b + 1])
        {
          int holder = numbers[b];   // keep the left value
          numbers[b] = numbers[b + 1];
          numbers[b + 1] = holder;
          changed = true;
        }
      }
      if (!changed)
      {
        break;
      }
    }
  }

  public static void main(String[] args)
  {
    int[] input = {5, 3, 8, 1, 9, 2};
    arrange(input);
    for (int item : input)
    {
      System.out.print(item + " ");
    }
    System.out.println();
  }
}
