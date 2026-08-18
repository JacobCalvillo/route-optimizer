import { Component, input } from '@angular/core';
import { OptimizedRoute, RouteStop } from '../models';

/** The optimized sequence, in the same order the map numbers the markers. */
@Component({
  selector: 'app-stop-list',
  imports: [],
  templateUrl: './stop-list.html',
  styleUrl: './stop-list.scss',
})
export class StopList {
  readonly route = input.required<OptimizedRoute | null>();

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
