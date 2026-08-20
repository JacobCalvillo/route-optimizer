import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from './api.service';
import { AddressInput, Depot, EMPTY_ADDRESS, Health, OptimizedRoute, Order } from './models';
import { AddressForm } from './address-form/address-form';
import { OrderInput } from './order-input/order-input';
import { OrderList } from './order-list/order-list';
import { RouteMap } from './route-map/route-map';
import { RouteSummary } from './route-summary/route-summary';
import { StopList } from './stop-list/stop-list';

@Component({
  selector: 'app-root',
  imports: [
    FormsModule,
    AddressForm,
    OrderInput,
    OrderList,
    RouteMap,
    RouteSummary,
    StopList,
  ],
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

  /** A new depot in parts, which is what the structured geocoder searches by. */
  readonly depotAddress = signal<AddressInput>({ ...EMPTY_ADDRESS });
  /** An escape hatch for a pasted "lat, lon" pair, used only when the parts are empty. */
  readonly depotCoordinates = signal('');
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

  /** Whether the typed parts are enough for the geocoder to search by. */
  hasDepotAddress(): boolean {
    const a = this.depotAddress();
    return !!(a.street?.trim() || a.city?.trim() || a.postalCode?.trim());
  }

  /** Saves the typed depot under its name, then selects it. */
  saveDepot(): void {
    const name = this.depotLabel().trim();
    const coordinates = this.depotCoordinates().trim();
    if (!name || (!this.hasDepotAddress() && !coordinates)) {
      this.error.set('A saved depot needs a name and either an address or coordinates.');
      return;
    }
    this.savingDepot.set(true);
    this.error.set(null);
    // Parts when there are parts; the pasted pair otherwise.
    const parts = this.hasDepotAddress() ? this.depotAddress() : null;
    this.api.saveDepot(name, parts, coordinates || undefined).subscribe({
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
    return (
      this.routableCount() > 0 &&
      (this.selectedDepotId() !== null ||
        this.hasDepotAddress() ||
        !!this.depotCoordinates().trim())
    );
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

  /** A saved id, then typed parts, then a pasted coordinate pair. */
  private depotForRequest() {
    if (this.selectedDepotId() !== null) {
      return { id: this.selectedDepotId()! };
    }
    if (this.hasDepotAddress()) {
      const a = this.depotAddress();
      return {
        address: [a.street, a.exteriorNumber, a.neighborhood, a.postalCode, a.city, a.state]
          .filter((part) => !!part?.trim())
          .join(', '),
        label: this.depotLabel(),
      };
    }
    return { address: this.depotCoordinates().trim(), label: this.depotLabel() };
  }

  optimize(): void {
    this.optimizing.set(true);
    this.error.set(null);
    this.api
      .optimize({
        depot: this.depotForRequest(),
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
