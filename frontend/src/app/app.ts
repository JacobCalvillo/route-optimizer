import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from './api.service';
import { Health, OptimizedRoute, Order } from './models';
import { OrderInput } from './order-input/order-input';
import { OrderList } from './order-list/order-list';
import { RouteMap } from './route-map/route-map';
import { RouteSummary } from './route-summary/route-summary';
import { StopList } from './stop-list/stop-list';

@Component({
  selector: 'app-root',
  imports: [FormsModule, OrderInput, OrderList, RouteMap, RouteSummary, StopList],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly api = inject(ApiService);

  readonly orders = signal<Order[]>([]);
  readonly route = signal<OptimizedRoute | null>(null);
  readonly health = signal<Health | null>(null);
  readonly optimizing = signal(false);
  readonly error = signal<string | null>(null);

  // Defaults to the centre of Mexico City so the example orders route sensibly out of the box.
  readonly depotLat = signal(19.4326);
  readonly depotLon = signal(-99.1332);
  readonly depotLabel = signal('Central Warehouse');
  readonly departureTime = signal('08:00');

  constructor() {
    this.refreshHealth();
    this.refreshOrders();
  }

  refreshHealth(): void {
    this.api.health().subscribe({
      next: (health) => this.health.set(health),
      error: () => this.health.set(null),
    });
  }

  refreshOrders(): void {
    this.api.listOrders().subscribe({
      next: (orders) => {
        this.orders.set(orders);
        // A stale route would keep pointing at deleted or edited stops.
        this.route.set(null);
      },
      error: (err) => this.error.set(err?.error?.message ?? 'Could not load orders.'),
    });
  }

  routableCount(): number {
    return this.orders().filter((order) => order.geocodeStatus === 'OK').length;
  }

  optimize(): void {
    this.optimizing.set(true);
    this.error.set(null);
    this.api
      .optimize({
        depot: { lat: this.depotLat(), lon: this.depotLon(), label: this.depotLabel() },
        departureTime: this.departureTime(),
      })
      .subscribe({
        next: (route) => {
          this.route.set(route);
          this.optimizing.set(false);
        },
        error: (err) => {
          this.error.set(err?.error?.message ?? 'Optimization failed.');
          this.optimizing.set(false);
        },
      });
  }
}
