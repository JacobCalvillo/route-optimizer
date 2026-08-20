import {
  Component,
  ElementRef,
  afterNextRender,
  effect,
  input,
  viewChild,
} from '@angular/core';
import * as L from 'leaflet';
import { OptimizedRoute, Order, Shift } from '../models';
import { RETURN_LEG_COLOR, legColor, rampCss } from './leg-colors';

/** Mexico City, used until there is anything to fit the view to. */
const FALLBACK_CENTER: L.LatLngExpression = [19.4326, -99.1332];

const LEG_WEIGHT = 5;
const LEG_WEIGHT_HOVER = 9;
const RETURN_LEG_WEIGHT = 3;

/**
 * Leaflet map showing the depot and one route per driver shift.
 *
 * <p>Each leg is drawn as its own polyline so it can carry two facts at once: the shift that
 * drives it, as a hue, and its position in that shift's sequence, as light-to-dark within the hue.
 * See {@link legColor} for why position is one hue rather than a set of unrelated colours.
 *
 * <p>Markers are built with {@link L.divIcon} rather than image icons: it renders the sequence
 * number directly, and it sidesteps Leaflet's default-icon path problem in bundled apps entirely.
 * Their CSS lives in the global stylesheet because Leaflet creates these elements outside the
 * component's template, so Angular's style encapsulation never reaches them.
 */
@Component({
  selector: 'app-route-map',
  template: '<div class="map" #mapEl></div>',
  styles: `
    :host {
      display: block;
      height: 100%;
    }
    .map {
      height: 100%;
      min-height: 420px;
      border-radius: 8px;
    }
  `,
})
export class RouteMap {
  readonly route = input<OptimizedRoute | null>(null);
  readonly orders = input<Order[]>([]);

  private readonly mapEl = viewChild.required<ElementRef<HTMLDivElement>>('mapEl');

  private map?: L.Map;
  private overlay?: L.LayerGroup;
  private legend?: L.Control;

  constructor() {
    afterNextRender(() => this.initMap());
    effect(() => {
      // Reading both inputs registers this effect as a dependency of each.
      const route = this.route();
      const orders = this.orders();
      if (this.map) {
        this.render(route, orders);
      }
    });
  }

  private initMap(): void {
    this.map = L.map(this.mapEl().nativeElement, { center: FALLBACK_CENTER, zoom: 12 });

    // Attribution is required by the OpenStreetMap tile usage policy.
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    }).addTo(this.map);

    this.overlay = L.layerGroup().addTo(this.map);
    this.render(this.route(), this.orders());
  }

  private render(route: OptimizedRoute | null, orders: Order[]): void {
    const map = this.map;
    const overlay = this.overlay;
    if (!map || !overlay) {
      return;
    }
    overlay.clearLayers();
    this.removeLegend();

    if (route) {
      this.renderPlan(overlay, route);
      const points: L.LatLngExpression[] = [[route.depotLat, route.depotLon]];
      route.shifts.forEach((shift) =>
        shift.stops.forEach((stop) => points.push([stop.lat, stop.lon])),
      );
      route.unscheduled.forEach((stop) => points.push([stop.lat, stop.lon]));
      map.fitBounds(L.latLngBounds(points).pad(0.15));
      return;
    }

    // No route yet: show whichever orders already have coordinates.
    const located = orders.filter((order) => order.lat != null && order.lon != null);
    located.forEach((order) => {
      L.marker([order.lat!, order.lon!], {
        icon: L.divIcon({
          className: '',
          html: '<div class="pin pin-pending"></div>',
          iconSize: [16, 16],
          iconAnchor: [8, 8],
        }),
      })
        .bindTooltip(order.rawAddress ?? '')
        .addTo(overlay);
    });

    if (located.length > 0) {
      map.fitBounds(
        L.latLngBounds(located.map((o) => [o.lat!, o.lon!] as L.LatLngExpression)).pad(0.2),
      );
    }
  }

  private renderPlan(overlay: L.LayerGroup, route: OptimizedRoute): void {
    route.shifts.forEach((shift, shiftIndex) => this.renderShift(overlay, route, shift, shiftIndex));

    // Stops nobody had room for. Hollow and grey so they read as "not planned", not as a stop
    // whose number you missed.
    route.unscheduled.forEach((stop) => {
      L.marker([stop.lat, stop.lon], {
        icon: L.divIcon({
          className: '',
          html: '<div class="pin pin-unscheduled">!</div>',
          iconSize: [26, 26],
          iconAnchor: [13, 13],
        }),
      })
        .bindTooltip(`Not scheduled — ${stop.label}`)
        .addTo(overlay);
    });

    L.marker([route.depotLat, route.depotLon], {
      icon: L.divIcon({
        className: '',
        html: '<div class="pin pin-depot">D</div>',
        iconSize: [30, 30],
        iconAnchor: [15, 15],
      }),
      zIndexOffset: 1000,
    })
      .bindTooltip(route.depotLabel || 'Depot')
      .addTo(overlay);

    this.addLegend(route);
  }

  private renderShift(
    overlay: L.LayerGroup,
    route: OptimizedRoute,
    shift: Shift,
    shiftIndex: number,
  ): void {
    if (shift.legs?.length) {
      shift.legs.forEach((leg, index) => {
        if (leg.length >= 2) {
          this.addLeg(overlay, leg, index, shift.legs!.length, shift, shiftIndex, false);
        }
      });
    } else {
      // No road data. Dashed straight segments, so it reads as the approximation it is rather
      // than a route that happens to ignore every street.
      const points: [number, number][] = [
        [route.depotLat, route.depotLon],
        ...shift.stops.map((stop) => [stop.lat, stop.lon] as [number, number]),
        [route.depotLat, route.depotLon],
      ];
      for (let i = 0; i < points.length - 1; i++) {
        this.addLeg(
          overlay,
          [points[i], points[i + 1]],
          i,
          points.length - 1,
          shift,
          shiftIndex,
          true,
        );
      }
    }

    shift.stops.forEach((stop) => {
      const lateClass = stop.lateMinutes > 0 ? ' pin-late' : '';
      L.marker([stop.lat, stop.lon], {
        icon: L.divIcon({
          className: '',
          html:
            `<div class="pin pin-${stop.priority.toLowerCase()}${lateClass}"` +
            ` style="outline:2px solid ${legColor(shift.stops.length - 1, shift.stops.length, shiftIndex)}">` +
            `${stop.sequence}</div>`,
          iconSize: [28, 28],
          iconAnchor: [14, 14],
        }),
        zIndexOffset: 500,
      })
        .bindTooltip(`${shift.name} ${stop.sequence}. ${stop.label} — ETA ${stop.eta}`)
        .addTo(overlay);
    });
  }

  private addLeg(
    overlay: L.LayerGroup,
    points: [number, number][],
    index: number,
    total: number,
    shift: Shift,
    shiftIndex: number,
    approximate: boolean,
  ): void {
    // The final leg returns to the depot without delivering anything, so it stays out of the ramp.
    const isReturn = index === total - 1;
    const color = isReturn
      ? RETURN_LEG_COLOR
      : legColor(index, Math.max(1, total - 1), shiftIndex);

    // Full opacity on the return leg so its black reads as black rather than compositing to grey
    // against the tiles. The dashes and the thinner stroke are what set it apart, not a tint.
    const weight = isReturn ? RETURN_LEG_WEIGHT : LEG_WEIGHT;
    const opacity = isReturn ? 1 : 0.85;

    const line = L.polyline(points as L.LatLngExpression[], {
      color,
      weight,
      opacity,
      dashArray: approximate || isReturn ? '8 8' : undefined,
    });

    line.bindTooltip(this.legLabel(index, total, shift, approximate), { sticky: true });

    // Hover is what makes an individual leg identifiable past the five steps a ramp can hold.
    line.on('mouseover', () => line.setStyle({ weight: LEG_WEIGHT_HOVER, opacity: 1 }));
    line.on('mouseout', () => line.setStyle({ weight, opacity }));

    line.addTo(overlay);
  }

  private legLabel(index: number, total: number, shift: Shift, approximate: boolean): string {
    const suffix = approximate ? ' (straight-line estimate)' : '';
    if (index === total - 1) {
      return `${shift.name}: return to depot${suffix}`;
    }
    const to = shift.stops[index];
    const from = index === 0 ? 'depot' : `stop ${index}`;
    const km = (to.distanceFromPreviousMeters / 1000).toFixed(1);
    return `${shift.name} leg ${index + 1}: ${from} → stop ${to.sequence} · ${km} km · arrives ${to.eta}${suffix}`;
  }

  private addLegend(route: OptimizedRoute): void {
    if (!this.map || route.shifts.length === 0) {
      return;
    }
    const control = new L.Control({ position: 'bottomleft' });
    control.onAdd = () => {
      const box = L.DomUtil.create('div', 'leg-legend');
      box.innerHTML =
        route.shifts
          .map(
            (shift, index) =>
              `<span class="leg-legend-row">` +
              `<span class="leg-legend-label">${shift.name}</span>` +
              `<span class="leg-legend-ramp" style="background:${rampCss(index)}"></span>` +
              `</span>`,
          )
          .join('') +
        `<span class="leg-legend-row">` +
        `<span class="leg-legend-return" style="border-color:${RETURN_LEG_COLOR}"></span>` +
        `<span class="leg-legend-label">Return</span></span>`;
      return box;
    };
    control.addTo(this.map);
    this.legend = control;
  }

  private removeLegend(): void {
    if (this.legend && this.map) {
      this.map.removeControl(this.legend);
      this.legend = undefined;
    }
  }
}
