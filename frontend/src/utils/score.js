/**
 * Score bands for display only. They describe the amount of overlap the algorithms measured,
 * not whether anyone copied anything.
 */
export const SCORE_BANDS = [
  { key: 'low', min: 0, max: 0.3, label: 'Low overlap', description: 'Little shared code structure.' },
  { key: 'moderate', min: 0.3, max: 0.6, label: 'Moderate overlap', description: 'Some shared sequences; often boilerplate or common idioms.' },
  { key: 'high', min: 0.6, max: 0.8, label: 'High overlap', description: 'Substantial shared structure worth a look.' },
  { key: 'very-high', min: 0.8, max: 1.0001, label: 'Very high overlap', description: 'Most of the code structure is shared; review in context.' },
];

export function scoreBand(fraction) {
  const v = Number(fraction) || 0;
  return SCORE_BANDS.find((b) => v >= b.min && v < b.max) ?? SCORE_BANDS[0];
}
