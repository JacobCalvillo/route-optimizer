/**
 * Colour ramp for route legs.
 *
 * <p>Legs are an *ordinal* variable — they have an order and a direction — so the encoding is a
 * single hue running light to dark, not a set of unrelated colours. A rainbow would read as
 * "unrelated categories" and destroy the one thing worth showing: which way the vehicle travels.
 *
 * <p>These five steps are the most a single hue can carry on the OpenStreetMap tile background and
 * still keep neighbouring legs apart: validated for monotone lightness, adjacent lightness gaps
 * >= 0.06, and a light end that clears 2:1 contrast against the tiles (#f2efe9). Anything finer
 * failed the adjacent-gap check — nine evenly spaced steps sat 0.047 apart, which nobody can tell
 * apart on a map.
 *
 * <p>With more than five legs the ramp is interpolated, so neighbouring legs stop being reliably
 * distinguishable and the colour carries *progression* rather than identity. Identity is on the
 * numbered markers and the hover tooltip, which is why colour is never the only channel here.
 */
const RAMP = ['#6da7ec', '#3987e5', '#256abf', '#184f95', '#0d366b'] as const;

/**
 * The return-to-depot leg is not a delivery, so it stays outside the ramp entirely.
 *
 * <p>Black rather than a ramp step because it has no position in the sequence to encode — it is
 * the drive home. It has to be drawn at full opacity to actually read as black: at the 0.6 the
 * other legs use, black over the map tiles composites to a mid grey.
 */
export const RETURN_LEG_COLOR = '#000000';

/** Colour for leg {@code index} of {@code total}, spread across the full ramp. */
export function legColor(index: number, total: number): string {
  if (total <= 1) {
    return RAMP[RAMP.length - 1];
  }
  const position = (index / (total - 1)) * (RAMP.length - 1);
  return interpolate(RAMP[Math.floor(position)], RAMP[Math.ceil(position)], position % 1);
}

/** The ramp endpoints, for the map legend. */
export function rampCss(): string {
  return `linear-gradient(to right, ${RAMP.join(', ')})`;
}

function interpolate(from: string, to: string, ratio: number): string {
  if (ratio === 0) {
    return from;
  }
  const [r1, g1, b1] = channels(from);
  const [r2, g2, b2] = channels(to);
  const mix = (a: number, b: number) => Math.round(a + (b - a) * ratio);
  return `rgb(${mix(r1, r2)}, ${mix(g1, g2)}, ${mix(b1, b2)})`;
}

function channels(hex: string): [number, number, number] {
  return [
    parseInt(hex.slice(1, 3), 16),
    parseInt(hex.slice(3, 5), 16),
    parseInt(hex.slice(5, 7), 16),
  ];
}
