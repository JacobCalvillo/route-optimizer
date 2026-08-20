import { Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../api.service';
import { Order, OrderUpdate, Priority } from '../models';

/** The stored orders, with inline correction for anything the geocoder could not resolve. */
@Component({
  selector: 'app-order-list',
  imports: [FormsModule],
  templateUrl: './order-list.html',
  styleUrl: './order-list.scss',
})
export class OrderList {
  private readonly api = inject(ApiService);

  readonly orders = input.required<Order[]>();
  readonly changed = output<void>();

  readonly priorities: Priority[] = ['URGENT', 'NORMAL', 'LOW'];
  readonly editingId = signal<number | null>(null);
  readonly draft = signal<OrderUpdate>({});
  readonly busy = signal(false);

  startEdit(order: Order): void {
    this.editingId.set(order.id);
    this.draft.set({
      address: order.rawAddress,
      priority: order.priority,
      timeFrom: order.timeFrom,
      timeTo: order.timeTo,
      customerName: order.customerName,
    });
  }

  cancelEdit(): void {
    this.editingId.set(null);
    this.draft.set({});
  }

  patchDraft(changes: OrderUpdate): void {
    this.draft.update((current) => ({ ...current, ...changes }));
  }

  save(order: Order): void {
    this.busy.set(true);
    // Empty strings from the time inputs mean "clear the bound", so normalize them to null.
    const draft = this.draft();
    this.api
      .updateOrder(order.id, {
        ...draft,
        timeFrom: draft.timeFrom || null,
        timeTo: draft.timeTo || null,
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.cancelEdit();
          this.changed.emit();
        },
        error: () => this.busy.set(false),
      });
  }

  remove(order: Order): void {
    this.busy.set(true);
    this.api.deleteOrder(order.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.changed.emit();
      },
      error: () => this.busy.set(false),
    });
  }

  retryGeocoding(): void {
    this.busy.set(true);
    this.api.retryGeocoding().subscribe({
      next: () => {
        this.busy.set(false);
        this.changed.emit();
      },
      error: () => this.busy.set(false),
    });
  }

  /** Only orders with no usable coordinates. APPROXIMATE ones route fine; they just need a look. */
  unresolvedCount(): number {
    return this.orders().filter(
      (order) => order.geocodeStatus !== 'OK' && order.geocodeStatus !== 'APPROXIMATE',
    ).length;
  }

  approximateCount(): number {
    return this.orders().filter((order) => order.geocodeStatus === 'APPROXIMATE').length;
  }

  window(order: Order): string {
    if (order.timeFrom && order.timeTo) {
      return `${order.timeFrom} – ${order.timeTo}`;
    }
    if (order.timeTo) {
      return `by ${order.timeTo}`;
    }
    if (order.timeFrom) {
      return `from ${order.timeFrom}`;
    }
    return 'any time';
  }
}
