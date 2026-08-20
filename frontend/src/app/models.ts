/** Mirrors the backend DTOs in com.routeopt.api.Dtos. */

export type Priority = 'URGENT' | 'NORMAL' | 'LOW';

export type GeocodeStatus = 'PENDING' | 'OK' | 'APPROXIMATE' | 'NO_ADDRESS' | 'FAILED';

/** A parsed-but-not-yet-stored order, as returned by POST /api/orders/parse. */
export interface ParsedOrder {
  customerName: string | null;
  address: string | null;
  priority: Priority;
  timeFrom: string | null;
  timeTo: string | null;
  notes: string | null;
}

/** Direct structured entry, used when no free-form parsing is needed. */
export interface ManualOrder {
  address: string;
  customerName?: string | null;
  priority: Priority;
  timeFrom?: string | null;
  timeTo?: string | null;
  serviceMinutes?: number | null;
  notes?: string | null;
}

export interface Order {
  id: number;
  customerName: string | null;
  rawAddress: string | null;
  normalizedAddress: string | null;
  lat: number | null;
  lon: number | null;
  priority: Priority;
  timeFrom: string | null;
  timeTo: string | null;
  serviceMinutes: number | null;
  geocodeStatus: GeocodeStatus;
  notes: string | null;
  createdAt: string;
}

export interface OrderUpdate {
  address?: string | null;
  priority?: Priority;
  timeFrom?: string | null;
  timeTo?: string | null;
  serviceMinutes?: number | null;
  customerName?: string | null;
  notes?: string | null;
}

export interface RouteStop {
  sequence: number;
  orderId: number | null;
  label: string;
  lat: number;
  lon: number;
  priority: Priority;
  eta: string;
  departure: string;
  timeFrom: string | null;
  timeTo: string | null;
  distanceFromPreviousMeters: number;
  durationFromPreviousSeconds: number;
  waitMinutes: number;
  lateMinutes: number;
}

export interface OptimizedRoute {
  depotLat: number;
  depotLon: number;
  depotLabel: string;
  departureTime: string;
  stops: RouteStop[];
  totalDistanceMeters: number;
  totalDurationSeconds: number;
  returnToDepotMeters: number;
  /** Distance of the greedy tour, before 2-opt ran. */
  initialDistanceMeters: number;
  improvementPercent: number;
  lateStopCount: number;
  totalLateMinutes: number;
  matrixProvider: string;
  /**
   * One road polyline per leg, each as [lat, lon] pairs. Leg i runs to stop i+1; the last one
   * returns to the depot. Null when no road data was available.
   */
  legs: [number, number][][] | null;
  geometrySource: string | null;
  warnings: string[];
}

export interface OptimizeRequest {
  /** Either an address to geocode, or explicit coordinates. `address` also accepts "lat, lon". */
  depot: { address?: string; lat?: number; lon?: number; label?: string };
  departureTime: string;
  orderIds?: number[];
}

export interface Health {
  status: string;
  aiParserAvailable: boolean;
  model: string;
  matrixProvider: string;
  orderCount: number;
}
