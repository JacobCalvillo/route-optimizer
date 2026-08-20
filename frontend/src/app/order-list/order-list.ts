import { Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../api.service';
import { AddressInput, EMPTY_ADDRESS, Order, OrderUpdate, Priority } from '../models';
import { AddressForm } from '../address-form/address-form';

/** The stored orders, with inline correction for anything the geocoder could not resolve. */
@Component({
  selector: 'app-order-list',
  imports: [FormsModule, AddressForm],
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
  readonly error = signal<string | null>(null);

  readonly addressDraft = signal<AddressInput>({ ...EMPTY_ADDRESS });

  startEdit(order: Order): void {
    this.editingId.set(order.id);
    // Seed from the stored parts. A legacy order has none, so the form starts empty and whatever
    // is typed becomes its first structured address.
    this.addressDraft.set({ ...EMPTY_ADDRESS, ...(order.address ?? {}) });
    this.draft.set({
      priority: order.priority,
      timeFrom: order.timeFrom,
      timeTo: order.timeTo,
      customerName: order.customerName,
      phone: order.phone,
      notes: order.notes,
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
    this.error.set(null);
    // Empty strings from the time inputs mean "clear the bound", so normalize them to null.
    const draft = this.draft();
    this.api
      .updateOrder(order.id, {
        ...draft,
        address: this.addressDraft(),
        timeFrom: draft.timeFrom || null,
        timeTo: draft.timeTo || null,
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.cancelEdit();
          this.changed.emit();
        },
        error: (err) => this.handleFailure(err, 'Could not save the order.'),
      });
  }

  remove(order: Order): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.deleteOrder(order.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.changed.emit();
      },
      error: (err) => this.handleFailure(err, 'Could not delete the order.'),
    });
  }

  retryGeocoding(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.retryGeocoding().subscribe({
      next: () => {
        this.busy.set(false);
        this.changed.emit();
      },
      error: (err) => this.handleFailure(err, 'Could not retry geocoding.'),
    });
  }

  /**
   * Reports a failed mutation and reloads the list.
   *
   * <p>Errors used to be swallowed, which left the row on screen as if nothing had happened. A 404
   * is the case that made this matter: the order was already gone server-side, so the row was a
   * ghost, and every further click on it failed the same silent way. Reloading on *any* failure is
   * the self-correcting choice — whatever the client believed, the server's answer replaces it.
   */
  private handleFailure(error: unknown, fallback: string): void {
    this.busy.set(false);
    this.cancelEdit();

    const response = error as { status?: number; error?: { message?: string } };
    this.error.set(
      response?.status === 404
        ? 'That order no longer exists — the list has been refreshed.'
        : (response?.error?.message ?? fallback),
    );
    this.changed.emit();
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
