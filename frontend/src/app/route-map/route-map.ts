import {
  Component,
  ElementRef,
  afterNextRender,
  effect,
  input,
  viewChild,
} from '@angular/core';
import * as L from 'leaflet';
import { OptimizedRoute, Order } from '../models';

/** Mexico City, used until there is anything to fit the view to. */
const FALLBACK_CENTER: L.LatLngExpression = [19.4326, -99.1332];

/**
 * Leaflet map showing the depot, the stops numbered in visit order, and the route polyline.
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
      })
        .bindTooltip(`${stop.sequence}. ${stop.label} — ETA ${stop.eta}`)
        .addTo(overlay);
    });

    if (route.geometry?.length) {
      // Road geometry from the routing provider: the line follows actual streets.
      L.polyline(route.geometry as L.LatLngExpression[], {
        color: '#2563eb',
        weight: 5,
        opacity: 0.8,
      }).addTo(overlay);
      return;
    }

    // No road data. Draw the tour as dashed straight segments so it reads as the approximation it
    // is, rather than as a route that happens to ignore every street.
    const straight: L.LatLngExpression[] = [
      [route.depotLat, route.depotLon],
      ...route.stops.map((stop) => [stop.lat, stop.lon] as L.LatLngExpression),
      [route.depotLat, route.depotLon],
    ];
    L.polyline(straight, {
      color: '#2563eb',
      weight: 3,
      opacity: 0.65,
      dashArray: '8 8',
    })
      .bindTooltip('Straight-line approximation — no road data for this route')
      .addTo(overlay);
  }
}
