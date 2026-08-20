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
  /** Set while editing an order that only ever had a single-string address. */
  readonly editingLegacy = signal(false);

  startEdit(order: Order): void {
    this.editingId.set(order.id);
    this.addressDraft.set(this.seedAddress(order));
    this.draft.set({
      priority: order.priority,
      timeFrom: order.timeFrom,
      timeTo: order.timeTo,
      serviceMinutes: order.serviceMinutes,
      customerName: order.customerName,
      phone: order.phone,
      notes: order.notes,
    });
  }

  /**
   * Fills the form from whatever the order actually has.
   *
   * <p>An order created before the address was split — or through free-form manual entry — has no
   * parts at all, only a single string. Opening a blank form for it loses the one piece of
   * information that exists, so the string is put in the street box: nothing is thrown away, and
   * the dispatcher can move the pieces into the right boxes from there.
   *
   * <p>Every field is coerced to a string. Binding {@code null} into a text input leaves it empty,
   * which looks the same as "not loaded" and is exactly the confusion this method exists to avoid.
   */
  private seedAddress(order: Order): AddressInput {
    const parts = order.address ?? {};
    const hasParts = !!(parts.street || parts.city || parts.postalCode || parts.neighborhood);
    this.editingLegacy.set(!hasParts && !!order.rawAddress);

    return {
      street: (hasParts ? parts.street : order.rawAddress) ?? '',
      exteriorNumber: parts.exteriorNumber ?? '',
      interiorNumber: parts.interiorNumber ?? '',
      neighborhood: parts.neighborhood ?? '',
      postalCode: parts.postalCode ?? '',
      city: parts.city ?? '',
      state: parts.state ?? '',
    };
  }

  cancelEdit(): void {
    this.editingId.set(null);
    this.editingLegacy.set(false);
    this.draft.set({});
    this.addressDraft.set({ ...EMPTY_ADDRESS });
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
