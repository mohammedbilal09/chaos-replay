export interface HealthResponse {
  status: 'UP' | 'DOWN' | string;
  service: string;
}

