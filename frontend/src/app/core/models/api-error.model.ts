export interface ValidationErrorDetail {
  field: string;
  message: string;
  rejectedValue?: unknown;
}

export interface ApiErrorResponse {
  timestamp?: string;
  status: number;
  error: string;
  message: string;
  path?: string;
  errorCode?: string;
  details?: ValidationErrorDetail[];
  correlationId?: string;
}

