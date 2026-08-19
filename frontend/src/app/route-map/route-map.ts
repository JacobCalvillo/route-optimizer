import {
  Component,
  ElementRef,
  afterNextRender,
  effect,
  input,
  viewChild,
} from '@angular/core';
import * as L from 'leaflet';
import { OptimizedRoute, Order, RouteStop } from '../models';
import { RETURN_LEG_COLOR, legColor, rampCss } from './leg-colors';

/** Mexico City, used until there is anything to fit the view to. */
const FALLBACK_CENTER: L.LatLngExpression = [19.4326, -99.1332];

const LEG_WEIGHT = 5;
const LEG_WEIGHT_HOVER = 9;

/**
 * Leaflet map showing the depot, the stops numbered in visit order, and the route itself.
 *
 * <p>Each leg is drawn as its own polyline so it can carry its position in the sequence as colour:
 * a single hue running light to dark, so the direction of travel is readable without chasing
 * marker numbers. See {@link legColor} for why it is one hue and not a set of unrelated ones.
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
    this.map = L.map(this.mapEl().nativeElement, {
      center: FALLBACK_CENTER,
      zoom: 12,
    });

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
      this.renderRoute(overlay, route);
      const points: L.LatLngExpression[] = [
        [route.depotLat, route.depotLon],
        ...route.stops.map((stop) => [stop.lat, stop.lon] as L.LatLngExpression),
      ];
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
      const bounds = L.latLngBounds(located.map((o) => [o.lat!, o.lon!] as L.LatLngExpression));
      map.fitBounds(bounds.pad(0.2));
    }
  }

  private renderRoute(overlay: L.LayerGroup, route: OptimizedRoute): void {
    this.renderLegs(overlay, route);

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

    route.stops.forEach((stop) => {
      const lateClass = stop.lateMinutes > 0 ? ' pin-late' : '';
      L.marker([stop.lat, stop.lon], {
        icon: L.divIcon({
          className: '',
          html: `<div class="pin pin-${stop.priority.toLowerCase()}${lateClass}">${stop.sequence}</div>`,
          iconSize: [28, 28],
          iconAnchor: [14, 14],
        }),
        zIndexOffset: 500,
      })
        .bindTooltip(`${stop.sequence}. ${stop.label} — ETA ${stop.eta}`)
        .addTo(overlay);
    });
  }

  private renderLegs(overlay: L.LayerGroup, route: OptimizedRoute): void {
    if (!route.legs?.length) {
      // No road data. Draw the tour as dashed straight segments so it reads as the approximation
      // it is, rather than as a route that happens to ignore every street. Still coloured by
      // position, so the direction of travel survives the degradation.
      const points: [number, number][] = [
        [route.depotLat, route.depotLon],
        ...route.stops.map((stop) => [stop.lat, stop.lon] as [number, number]),
        [route.depotLat, route.depotLon],
      ];
      for (let i = 0; i < points.length - 1; i++) {
        this.addLeg(overlay, [points[i], points[i + 1]], i, points.length - 1, route, true);
      }
      this.addLegend(route.stops.length, true);
      return;
    }

    route.legs.forEach((leg, index) => {
      if (leg.length >= 2) {
        this.addLeg(overlay, leg, index, route.legs!.length, route, false);
      }
    });
    this.addLegend(route.stops.length, false);
  }

  private addLeg(
    overlay: L.LayerGroup,
    points: [number, number][],
    index: number,
    total: number,
    route: OptimizedRoute,
    approximate: boolean,
  ): void {
    // The final leg returns to the depot without delivering anything, so it stays out of the ramp.
    const isReturn = index === total - 1;
    const color = isReturn ? RETURN_LEG_COLOR : legColor(index, Math.max(1, total - 1));

    const line = L.polyline(points as L.LatLngExpression[], {
      color,
      weight: isReturn ? 3 : LEG_WEIGHT,
      opacity: isReturn ? 0.6 : 0.85,
      dashArray: approximate || isReturn ? '8 8' : undefined,
    });

    line.bindTooltip(this.legLabel(index, total, route, approximate), { sticky: true });

    // Hover is what makes an individual leg identifiable past the five steps the ramp can hold.
    line.on('mouseover', () => line.setStyle({ weight: LEG_WEIGHT_HOVER, opacity: 1 }));
    line.on('mouseout', () =>
      line.setStyle({ weight: isReturn ? 3 : LEG_WEIGHT, opacity: isReturn ? 0.6 : 0.85 }),
    );

    line.addTo(overlay);
  }

  private legLabel(
    index: number,
    total: number,
    route: OptimizedRoute,
    approximate: boolean,
  ): string {
    const suffix = approximate ? ' (straight-line estimate)' : '';
    if (index === total - 1) {
      return `Return to ${route.depotLabel || 'depot'}${suffix}`;
    }
    const to: RouteStop = route.stops[index];
    const from = index === 0 ? route.depotLabel || 'Depot' : `Stop ${index}`;
    const km = (to.distanceFromPreviousMeters / 1000).toFixed(1);
    return `Leg ${index + 1}: ${from} → stop ${to.sequence} · ${km} km · arrives ${to.eta}${suffix}`;
  }

  private addLegend(stopCount: number, approximate: boolean): void {
    if (!this.map || stopCount < 2) {
      return;
    }
    const control = new L.Control({ position: 'bottomleft' });
    control.onAdd = () => {
      const box = L.DomUtil.create('div', 'leg-legend');
      box.innerHTML = `
        <span class="leg-legend-label">Departure</span>
        <span class="leg-legend-ramp" style="background:${rampCss()}"></span>
        <span class="leg-legend-label">Last stop</span>`;
      if (approximate) {
        box.title = 'Dashed: straight-line estimate, no road data';
      }
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
