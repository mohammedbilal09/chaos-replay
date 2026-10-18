import { ReplayVerificationStatus } from './replay.model';

export type Severity = 'INFO' | 'WARN' | 'ERROR' | 'FATAL';

export type EventType =
  | 'REQUEST'
  | 'RESPONSE'
  | 'DATABASE'
  | 'EXTERNAL_CALL'
  | 'LOG'
  | 'ERROR'
  | 'START'
  | 'END'
  | 'HEARTBEAT'
  | 'HTTP_REQUEST'
  | 'HTTP_RESPONSE'
  | 'DB_QUERY'
  | 'RPC_CALL'
  | 'CACHE_ACCESS'
  | 'MESSAGE_PUBLISH'
  | 'MESSAGE_CONSUME'
  | 'RECOVERY';

export type IncidentEntryCategory =
  | 'NORMAL_EVENT'
  | 'ERROR_EVENT'
  | 'FATAL_EVENT'
  | 'DATABASE_FAILURE'
  | 'EXTERNAL_CALL_FAILURE'
  | 'TIMEOUT_SIGNAL'
  | 'DOWNSTREAM_ERROR'
  | 'REPLAY_DIVERGENCE'
  | 'ROOT_CAUSE'
  | 'FIRST_FAILURE'
  | 'FAILURE_PROPAGATION'
  | 'CORRELATED_EVENT';

export type FailureAnalysisStatus =
  | 'COMPLETED'
  | 'ANALYZED'
  | 'INSUFFICIENT_EVIDENCE'
  | 'NO_FAILURE_DETECTED';

export type FailureAnalysisConclusion =
  | 'ROOT_CAUSE_CANDIDATE'
  | 'MULTIPLE_POSSIBLE_CAUSES'
  | 'REPLAY_DIVERGENCE'
  | 'PRIMARY_FAILURE'
  | 'CASCADING_FAILURE'
  | 'CONCURRENT_FAILURE'
  | 'TRANSIENT_ERROR'
  | 'INDETERMINATE'
  | 'NO_FAILURE_OBSERVED'
  | 'INSUFFICIENT_EVIDENCE';

export type FailureCandidateType =
  | 'DATABASE_FAILURE'
  | 'EXTERNAL_DEPENDENCY_FAILURE'
  | 'APPLICATION_ERROR'
  | 'TIMEOUT'
  | 'SERVICE_FAILURE'
  | 'DOWNSTREAM_FAILURE'
  | 'UNKNOWN_FAILURE'
  | 'FIRST_OBSERVED_FAILURE'
  | 'EXPLICIT_FAILURE_EVENT'
  | 'ROOT_CAUSE_CANDIDATE'
  | 'CORRELATED_ANOMALY';

export type FailureEvidenceType =
  | 'FIRST_FAILURE'
  | 'ERROR_EVENT'
  | 'FATAL_EVENT'
  | 'DOWNSTREAM_ERROR'
  | 'TIMEOUT_SIGNAL'
  | 'DATABASE_ERROR'
  | 'EXTERNAL_CALL_ERROR'
  | 'REPLAY_MISMATCH'
  | 'SERVICE_PROPAGATION'
  | 'TEMPORAL_PRECEDENCE'
  | 'FIRST_CHRONOLOGICAL_FAILURE'
  | 'HIGH_SEVERITY_ERROR'
  | 'OBSERVED_REPLAY_DIFFERENCE'
  | 'DOWNSTREAM_PROPAGATION'
  | 'TEMPORAL_SEQUENCE';

export type ReplayScenarioStatus =
  | 'CREATED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'GENERATED'
  | 'SIMULATED'
  | 'VERIFIED'
  | 'FAILED';

export interface IncidentTimelineEntryResponse {
  sequenceNumber: number;
  eventId: string;
  timestamp: string;
  offsetMs: number;
  serviceName: string;
  serviceInstance?: string;
  eventType: EventType;
  severity: Severity;
  operation?: string;
  message?: string;
  entryCategory: IncidentEntryCategory;
  failureObserved: boolean;
  failurePropagation: boolean;
  relatedCandidateRank?: number;
  evidenceReferences: string[];
}

export interface IncidentTimelineResponse {
  traceId: string;
  eventCount: number;
  failureCount: number;
  failureObserved: boolean;
  firstEventAt?: string;
  lastEventAt?: string;
  durationMs: number;
  analysisAvailable: boolean;
  verificationAvailable: boolean;
  rootCauseStatus?: FailureAnalysisStatus;
  conclusion?: FailureAnalysisConclusion;
  primaryServiceName?: string;
  primaryEventId?: string;
  confidenceScore?: number;
  timeline: IncidentTimelineEntryResponse[];
  generatedAt?: string;
}

export interface FailureAnalysisEvidenceResponse {
  sequenceNumber: number;
  eventId: string;
  evidenceType: FailureEvidenceType;
  evidenceValue: string;
}

export interface FailureAnalysisCandidateResponse {
  rank: number;
  serviceName: string;
  eventId: string;
  eventType: string;
  severity: string;
  candidateType: FailureCandidateType;
  confidenceScore: number;
  firstObservedAt: string;
  description: string;
  evidence: FailureAnalysisEvidenceResponse[];
}

export interface IncidentInvestigationResponse {
  traceId: string;
  timeline: IncidentTimelineResponse;
  correlationAvailable: boolean;
  scenarioAvailable: boolean;
  scenarioId?: string;
  executionAvailable: boolean;
  executionStatus?: ReplayScenarioStatus;
  verificationAvailable: boolean;
  verificationId?: string;
  verificationStatus?: ReplayVerificationStatus;
  verificationSummary?: string;
  analysisAvailable: boolean;
  analysisId?: string;
  analysisStatus?: FailureAnalysisStatus;
  conclusion?: FailureAnalysisConclusion;
  confidenceScore?: number;
  primaryServiceName?: string;
  primaryEventId?: string;
  candidates: FailureAnalysisCandidateResponse[];
  evidenceSummary: string[];
  limitations: string[];
}

