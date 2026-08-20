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

/** One driver's route within the day. */
export interface Shift {
  name: string;
  start: string;
  end: string;
  hours: number;
  stops: RouteStop[];
  totalDistanceMeters: number;
  totalDurationSeconds: number;
  returnToDepotMeters: number;
  lateStopCount: number;
  /** One road polyline per leg; null when no road data was available. */
  legs: [number, number][][] | null;
}

export interface UnscheduledStop {
  orderId: number | null;
  label: string;
  lat: number;
  lon: number;
  priority: Priority;
  reason: string;
}

export interface OptimizedRoute {
  depotLat: number;
  depotLon: number;
  depotLabel: string;
  shifts: Shift[];
  unscheduled: UnscheduledStop[];
  totalDistanceMeters: number;
  totalDurationSeconds: number;
  /** Greedy giant tour, before 2-opt and before the split. */
  initialDistanceMeters: number;
  /** The same tour after 2-opt, still uncut. improvementPercent compares these two. */
  improvedTourDistanceMeters: number;
  /** Extra distance the split costs: one depot return per shift. */
  splitOverheadMeters: number;
  improvementPercent: number;
  scheduledStopCount: number;
  lateStopCount: number;
  totalLateMinutes: number;
  matrixProvider: string;
  geometrySource: string | null;
  warnings: string[];
}

export interface OptimizeRequest {
  /** Either an address to geocode, or explicit coordinates. `address` also accepts "lat, lon". */
  depot: { address?: string; lat?: number; lon?: number; label?: string };
  /** Optional: the shifts define when drivers actually leave. */
  departureTime?: string;
  orderIds?: number[];
}

export interface Health {
  status: string;
  aiParserAvailable: boolean;
  model: string;
  matrixProvider: string;
  orderCount: number;
}
