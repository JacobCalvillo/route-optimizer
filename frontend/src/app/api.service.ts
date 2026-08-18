import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Health,
  ManualOrder,
  OptimizeRequest,
  OptimizedRoute,
  Order,
  OrderUpdate,
  ParsedOrder,
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  health(): Observable<Health> {
    return this.http.get<Health>(`${this.base}/health`);
  }

  /** Extraction only: nothing is geocoded or stored. */
  parseOrders(text: string): Observable<ParsedOrder[]> {
    return this.http.post<ParsedOrder[]>(`${this.base}/orders/parse`, { text });
  }

  createOrders(text: string): Observable<Order[]> {
    return this.http.post<Order[]>(`${this.base}/orders`, { text });
  }

  /** Adds one order from structured fields, bypassing the model. */
  createManualOrder(order: ManualOrder): Observable<Order> {
    return this.http.post<Order>(`${this.base}/orders/manual`, order);
  }

  listOrders(): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.base}/orders`);
  }

  updateOrder(id: number, changes: OrderUpdate): Observable<Order> {
    return this.http.patch<Order>(`${this.base}/orders/${id}`, changes);
  }

  deleteOrder(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/orders/${id}`);
  }

  retryGeocoding(): Observable<Order[]> {
    return this.http.post<Order[]>(`${this.base}/orders/geocode-retry`, {});
  }

  optimize(request: OptimizeRequest): Observable<OptimizedRoute> {
    return this.http.post<OptimizedRoute>(`${this.base}/routes/optimize`, request);
  }
}
