import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from './api.service';
import { Depot, Health, OptimizedRoute, Order } from './models';
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

  readonly depots = signal<Depot[]>([]);
  /** Which saved depot is selected, or null while typing a new address. */
  readonly selectedDepotId = signal<number | null>(null);
  readonly savingDepot = signal(false);

  // Coordinates by default so the app works offline out of the box, but the field takes an
  // address too - the backend geocodes anything that is not a "lat, lon" pair.
  readonly depot = signal('19.4326, -99.1332');
  readonly depotLabel = signal('Central Warehouse');

  constructor() {
    this.refreshHealth();
    this.refreshOrders();
    this.refreshDepots();
  }

  refreshDepots(): void {
    this.api.listDepots().subscribe({
      next: (depots) => {
        this.depots.set(depots);
        // The list arrives most-recently-used first, so preselecting the first one is the same
        // as remembering where the dispatcher dispatched from last time.
        if (this.selectedDepotId() === null && depots.length > 0) {
          this.selectedDepotId.set(depots[0].id);
        }
      },
      error: () => this.depots.set([]),
    });
  }

  onDepotSelected(value: string): void {
    this.selectedDepotId.set(value === '' ? null : Number(value));
  }

  /** Saves the typed address under its label, then selects it. */
  saveDepot(): void {
    const address = this.depot().trim();
    const name = this.depotLabel().trim();
    if (!address || !name) {
      this.error.set('A saved depot needs both a name and an address.');
      return;
    }
    this.savingDepot.set(true);
    this.error.set(null);
    this.api.saveDepot(name, address).subscribe({
      next: (saved) => {
        this.savingDepot.set(false);
        this.selectedDepotId.set(saved.id);
        this.refreshDepots();
      },
      error: (err) => {
        this.savingDepot.set(false);
        this.error.set(err?.error?.message ?? 'Could not save the depot.');
      },
    });
  }

  deleteDepot(id: number): void {
    this.api.deleteDepot(id).subscribe({
      next: () => {
        if (this.selectedDepotId() === id) {
          this.selectedDepotId.set(null);
        }
        this.refreshDepots();
      },
      error: (err) => this.error.set(err?.error?.message ?? 'Could not delete the depot.'),
    });
  }

  canOptimize(): boolean {
    return this.routableCount() > 0 && (this.selectedDepotId() !== null || !!this.depot().trim());
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
        depot:
          this.selectedDepotId() !== null
            ? { id: this.selectedDepotId()! }
            : { address: this.depot().trim(), label: this.depotLabel() },
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
