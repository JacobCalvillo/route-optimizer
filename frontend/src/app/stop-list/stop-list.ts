import { Component, input } from '@angular/core';
import { OptimizedRoute, RouteStop } from '../models';
import { shiftColor } from '../route-map/leg-colors';

/** The plan, grouped by driver, in the same order the map numbers each shift's markers. */
@Component({
  selector: 'app-stop-list',
  imports: [],
  templateUrl: './stop-list.html',
  styleUrl: './stop-list.scss',
})
export class StopList {
  readonly route = input.required<OptimizedRoute | null>();

  colorFor(shiftIndex: number): string {
    return shiftColor(shiftIndex);
  }

  kilometres(meters: number): string {
    return (meters / 1000).toFixed(1);
  }

  window(stop: RouteStop): string | null {
    if (stop.timeFrom && stop.timeTo) {
      return `${stop.timeFrom} – ${stop.timeTo}`;
    }
    if (stop.timeTo) {
      return `by ${stop.timeTo}`;
    }
    if (stop.timeFrom) {
      return `from ${stop.timeFrom}`;
    }
    return null;
  }
}
