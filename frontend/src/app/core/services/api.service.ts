import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { IncidentInvestigationResponse, IncidentTimelineResponse } from '../models/incident.model';
import { ReplayVerificationResponse } from '../models/replay.model';
import { HealthResponse } from '../models/health.model';
import { ApiErrorResponse } from '../models/api-error.model';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private readonly baseUrl = environment.apiBaseUrl;

  constructor(private http: HttpClient) {}

  getHealth(): Observable<HealthResponse> {
    return this.http
      .get<HealthResponse>(`${this.baseUrl}/api/v1/health`)
      .pipe(catchError(this.handleError));
  }

  getIncidentInvestigation(traceId: string): Observable<IncidentInvestigationResponse> {
    const cleanId = encodeURIComponent(traceId.trim());
    return this.http
      .get<IncidentInvestigationResponse>(`${this.baseUrl}/api/v1/incidents/traces/${cleanId}`)
      .pipe(catchError(this.handleError));
  }

  getIncidentTimeline(traceId: string): Observable<IncidentTimelineResponse> {
    const cleanId = encodeURIComponent(traceId.trim());
    return this.http
      .get<IncidentTimelineResponse>(`${this.baseUrl}/api/v1/incidents/traces/${cleanId}/timeline`)
      .pipe(catchError(this.handleError));
  }

  getReplayVerification(verificationId: string): Observable<ReplayVerificationResponse> {
    const cleanId = encodeURIComponent(verificationId.trim());
    return this.http
      .get<ReplayVerificationResponse>(`${this.baseUrl}/api/v1/replay/verifications/${cleanId}`)
      .pipe(catchError(this.handleError));
  }

  getVerificationsForScenario(scenarioId: string): Observable<ReplayVerificationResponse[]> {
    const cleanId = encodeURIComponent(scenarioId.trim());
    return this.http
      .get<ReplayVerificationResponse[]>(`${this.baseUrl}/api/v1/replay/scenarios/${cleanId}/verifications`)
      .pipe(catchError(this.handleError));
  }

  private handleError(error: HttpErrorResponse): Observable<never> {
    let apiError: ApiErrorResponse;

    if (error.status === 0) {
      apiError = {
        status: 0,
        error: 'NETWORK_ERROR',
        errorCode: 'BACKEND_UNAVAILABLE',
        message: 'Could not connect to the ChaosReplay backend. Please ensure the backend is running on port 8080 and reachable.',
        path: error.url || undefined
      };
    } else if (error.error && typeof error.error === 'object' && 'message' in error.error) {
      const backendErr = error.error as ApiErrorResponse;
      apiError = {
        timestamp: backendErr.timestamp,
        status: backendErr.status || error.status,
        error: backendErr.error || error.statusText,
        message: backendErr.message || 'An error occurred while communicating with the backend',
        path: backendErr.path,
        errorCode: backendErr.errorCode,
        details: backendErr.details,
        correlationId: backendErr.correlationId
      };
    } else {
      apiError = {
        status: error.status,
        error: error.statusText || 'HTTP_ERROR',
        errorCode: `HTTP_${error.status}`,
        message: typeof error.error === 'string' && error.error ? error.error : `HTTP request failed with status ${error.status}`,
        path: error.url || undefined
      };
    }

    return throwError(() => apiError);
  }
}

