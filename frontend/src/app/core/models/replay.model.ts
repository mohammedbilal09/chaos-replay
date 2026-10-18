export type ReplayVerificationStatus = 'PASSED' | 'FAILED' | 'PARTIAL' | 'SKIPPED';

export type VerificationDifferenceType =
  | 'EVENT_MISSING'
  | 'EVENT_UNEXPECTED'
  | 'EVENT_TYPE_MISMATCH'
  | 'SEVERITY_MISMATCH'
  | 'SERVICE_MISMATCH'
  | 'MESSAGE_MISMATCH'
  | 'OFFSET_DRIFT';

export interface ReplayVerificationDifferenceResponse {
  sequenceNumber: number;
  differenceType: VerificationDifferenceType;
  expectedEventId?: string;
  actualEventId?: string;
  expectedServiceName?: string;
  actualServiceName?: string;
  expectedEventType?: string;
  actualEventType?: string;
  expectedSeverity?: string;
  actualSeverity?: string;
  message?: string;
}

export interface ReplayVerificationResponse {
  verificationId: string;
  scenarioId: string;
  status: ReplayVerificationStatus;
  originalEventCount: number;
  replayedEventCount: number;
  originalFailureCount: number;
  replayedFailureCount: number;
  originalDurationMs: number;
  replayedDurationMs: number;
  eventsMatched: number;
  eventsMissing: number;
  eventsUnexpected: number;
  severityMismatches: number;
  eventTypeMismatches: number;
  serviceMismatches: number;
  resultMessage?: string;
  createdAt: string;
  differences: ReplayVerificationDifferenceResponse[];
}

