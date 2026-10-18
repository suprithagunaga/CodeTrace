# Sample dataset

Non-sensitive Java files for trying CodeTrace. Upload all six on the **New Analysis** page
(6 files → 15 pairs). With the default settings (identifier normalization, k = 5, w = 4, 3-token shingles)
you should see:

| Pair | What it demonstrates | Expected overall score |
|------|----------------------|------------------------|
| `BubbleSort.java` ↔ `ArraySorter.java` | Same algorithm with renamed identifiers, reformatting and different comments | 100% |
| `BubbleSort.java` ↔ `LibraryManager.java` | A method copied into an otherwise unrelated class | moderate (partial overlap, one long highlighted region) |
| `ArraySorter.java` ↔ `LibraryManager.java` | Same as above, through renaming | moderate |
| `BinarySearch.java` ↔ `RecursiveSearch.java` | Same idea, different structure (iterative vs. recursive) | low — CodeTrace compares token sequences, not semantics |
| `TemperatureStats.java` ↔ others | Unrelated code; also contains `"// ..."` inside a string literal | low |

Exact values are listed in the main README ("Verified results on the sample dataset").
