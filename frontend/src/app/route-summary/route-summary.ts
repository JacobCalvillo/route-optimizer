import { Component, input } from '@angular/core';
import { OptimizedRoute } from '../models';

/** Headline figures for the optimized route, including how much 2-opt improved on the greedy tour. */
@Component({
  selector: 'app-route-summary',
  imports: [],
  templateUrl: './route-summary.html',
  styleUrl: './route-summary.scss',
})
export class RouteSummary {
  readonly route = input.required<OptimizedRoute | null>();

  kilometres(meters: number): string {
    return (meters / 1000).toFixed(1);
  }

  duration(seconds: number): string {
    const total = Math.round(seconds / 60);
    const hours = Math.floor(total / 60);
    const minutes = total % 60;
    return hours > 0 ? `${hours} h ${minutes} min` : `${minutes} min`;
  }
}
