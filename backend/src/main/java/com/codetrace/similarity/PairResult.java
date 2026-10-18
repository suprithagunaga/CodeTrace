package com.codetrace.similarity;

import java.util.List;

/**
 * Similarity of one unordered file pair.
 *
 * @param jaccard              Jaccard similarity of token n-gram shingle sets, [0,1]
 * @param winnowing            Jaccard similarity of the two Winnowing fingerprint hash sets, [0,1]
 * @param overall              combined score, see {@link SimilarityEngine#combine}
 * @param sharedFingerprints   |FA ∩ FB|
 * @param fingerprintsA        |FA| (distinct hashes)
 * @param fingerprintsB        |FB| (distinct hashes)
 * @param containmentA         |FA ∩ FB| / |FA| — share of A's fingerprints also found in B (directional)
 * @param containmentB         |FA ∩ FB| / |FB| — share of B's fingerprints also found in A (directional)
 * @param coverageA            share of A's tokens inside matching regions
 * @param coverageB            share of B's tokens inside matching regions
 * @param regions              matching regions mapped to source positions
 * @param warnings             human-readable caveats for this pair
 */
public record PairResult(
        double jaccard,
        double winnowing,
        double overall,
        int sharedFingerprints,
        int fingerprintsA,
        int fingerprintsB,
        double containmentA,
        double containmentB,
        double coverageA,
        double coverageB,
        List<MatchedRegion> regions,
        List<String> warnings) {
}
