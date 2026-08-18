import { Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../api.service';
import { ManualOrder, ParsedOrder, Priority } from '../models';

const EMPTY_MANUAL: ManualOrder = {
  address: '',
  customerName: null,
  priority: 'NORMAL',
  timeFrom: null,
  timeTo: null,
};

const EXAMPLE = `entregar en Av. Insurgentes Sur 1602, CDMX, urgente
paquete para Juan en Paseo de la Reforma 222, sin prisa
Masaryk 111, Polanco, antes de las 13:00`;

/**
 * Free-form order entry. "Parse" shows what the model extracted without touching the database,
 * so the dispatcher can sanity-check the interpretation before committing to geocoding.
 */
@Component({
  selector: 'app-order-input',
  imports: [FormsModule],
  templateUrl: './order-input.html',
  styleUrl: './order-input.scss',
})
export class OrderInput {
  private readonly api = inject(ApiService);

  readonly ordersCreated = output<void>();

  readonly priorities: Priority[] = ['URGENT', 'NORMAL', 'LOW'];

  readonly text = signal('');
  readonly preview = signal<ParsedOrder[] | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  /** Manual entry is the way in when there is no ANTHROPIC_API_KEY, or the address is already clean. */
  readonly manualOpen = signal(false);
  readonly manual = signal<ManualOrder>({ ...EMPTY_MANUAL });

  patchManual(changes: Partial<ManualOrder>): void {
    this.manual.update((current) => ({ ...current, ...changes }));
  }

  addManual(): void {
    const draft = this.manual();
    if (!draft.address.trim()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api
      .createManualOrder({
        ...draft,
        timeFrom: draft.timeFrom || null,
        timeTo: draft.timeTo || null,
        customerName: draft.customerName || null,
      })
      .subscribe({
        next: () => {
          this.manual.set({ ...EMPTY_MANUAL });
          this.busy.set(false);
          this.ordersCreated.emit();
        },
        error: (err) => {
          this.error.set(err?.error?.message ?? 'Could not save the order.');
          this.busy.set(false);
        },
      });
  }

  parse(): void {
    const value = this.text().trim();
    if (!value) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.parseOrders(value).subscribe({
      next: (orders) => {
        this.preview.set(orders);
        this.busy.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? 'Could not reach the parser.');
        this.busy.set(false);
      },
    });
  }

  confirm(): void {
    const value = this.text().trim();
    if (!value) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.createOrders(value).subscribe({
      next: () => {
        this.text.set('');
        this.preview.set(null);
        this.busy.set(false);
        this.ordersCreated.emit();
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? 'Could not save the orders.');
        this.busy.set(false);
      },
    });
  }

  loadExample(): void {
    this.text.set(EXAMPLE);
    this.preview.set(null);
  }

  clear(): void {
    this.text.set('');
    this.preview.set(null);
    this.error.set(null);
  }

  window(order: ParsedOrder): string {
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
