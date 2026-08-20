/**
 * Colour for route legs, across two dimensions.
 *
 * <p>A leg carries two facts: which shift drives it (identity) and where it sits in that shift's
 * sequence (order). Those want different encodings, so they get them. **Shift** picks a hue -
 * blue for the first, orange for the second - which is the documented rule for two sequential
 * contexts on one chart. **Position** runs that hue light to dark.
 *
 * <p>Each ramp holds five visually distinct steps and no more. That is measured: on the
 * OpenStreetMap tile background, evenly spaced steps of one hue sit about 0.047 apart in
 * lightness, under the 0.06 needed to separate neighbours, and the lightest step of the source
 * ramp only reaches 1.84:1 contrast against the tiles. The steps below clear both floors.
 *
 * <p>Past five legs a ramp is interpolated, so colour carries *progression* rather than identity.
 * Identity lives on the numbered markers and the hover tooltip, neither of which degrades.
 */
const RAMPS: readonly (readonly string[])[] = [
  ['#6da7ec', '#3987e5', '#256abf', '#184f95', '#0d366b'], // shift 1, blue
  ['#f2915f', '#eb6834', '#c9521f', '#a03f16', '#732c0d'], // shift 2, orange
];

/**
 * The return-to-depot leg is not a delivery, so it stays outside every ramp.
 *
 * <p>Black rather than a ramp step because it has no position in the sequence to encode - it is
 * the drive home. It has to be drawn at full opacity to actually read as black: at the tint the
 * other legs use, black over the map tiles composites to a mid grey.
 */
export const RETURN_LEG_COLOR = '#000000';

/** Colour for leg {@code index} of {@code total} within shift {@code shiftIndex}. */
export function legColor(index: number, total: number, shiftIndex = 0): string {
  const ramp = RAMPS[shiftIndex % RAMPS.length];
  if (total <= 1) {
    return ramp[ramp.length - 1];
  }
  const position = (index / (total - 1)) * (ramp.length - 1);
  return interpolate(ramp[Math.floor(position)], ramp[Math.ceil(position)], position % 1);
}

/** The ramp for one shift, as a CSS gradient for the legend. */
export function rampCss(shiftIndex = 0): string {
  return `linear-gradient(to right, ${RAMPS[shiftIndex % RAMPS.length].join(', ')})`;
}

/** A single representative colour for a shift, for chips and list headers. */
export function shiftColor(shiftIndex: number): string {
  const ramp = RAMPS[shiftIndex % RAMPS.length];
  return ramp[Math.floor(ramp.length / 2)];
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
