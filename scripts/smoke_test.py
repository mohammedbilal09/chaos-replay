#!/usr/bin/env python3
"""
ChaosReplay End-to-End Smoke Test

Validates complete distributed failure replay lifecycle:
 1. Health check verification (Backend & Actuator)
 2. Telemetry ingestion of a unique distributed incident trace
 3. Chronological incident timeline retrieval & ordering verification
 4. Deterministic replay scenario generation
 5. Deterministic replay verification & difference inspection
 6. Evidence-backed failure analysis & root-cause ranking
 7. Consolidated incident investigation verification
 8. Frontend development proxy accessibility check (optional/default)

Usage:
  python3 scripts/smoke_test.py [--backend-url http://localhost:8080] [--frontend-url http://localhost:4200] [--skip-frontend]
"""

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
import uuid

# Terminal formatting
GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
CYAN = "\033[96m"
BOLD = "\033[1m"
RESET = "\033[0m"


def log_step(step_num: int, title: str):
    print(f"\n{BOLD}{CYAN}[Step {step_num}]{RESET} {BOLD}{title}{RESET}")


def log_pass(message: str):
    print(f"  {GREEN}✔ PASS:{RESET} {message}")


def log_fail(message: str):
    print(f"  {RED}✖ FAIL:{RESET} {message}", file=sys.stderr)


def log_info(message: str):
    print(f"  {YELLOW}ℹ INFO:{RESET} {message}")


def send_http(method: str, url: str, body: dict = None, timeout_sec: int = 10) -> tuple:
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "User-Agent": "ChaosReplay-SmokeTest/1.0"
    }
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout_sec) as resp:
            content = resp.read().decode("utf-8")
            parsed = json.loads(content) if content else {}
            return resp.status, parsed
    except urllib.error.HTTPError as e:
        content = e.read().decode("utf-8")
        try:
            parsed = json.loads(content)
        except Exception:
            parsed = {"raw": content}
        return e.code, parsed
    except Exception as e:
        return 0, {"error": str(e)}


def main():
    parser = argparse.ArgumentParser(description="ChaosReplay End-to-End Smoke Test")
    parser.add_argument("--backend-url", default="http://localhost:8080", help="Spring Boot backend base URL")
    parser.add_argument("--frontend-url", default="http://localhost:4200", help="Angular frontend dev server URL")
    parser.add_argument("--skip-frontend", action="store_true", help="Skip checking frontend development server")
    args = parser.parse_args()

    backend_url = args.backend_url.rstrip("/")
    frontend_url = args.frontend_url.rstrip("/")
    run_id = uuid.uuid4().hex[:8]
    trace_id = f"trace-smoke-{run_id}"

    print(f"{BOLD}========================================================================{RESET}")
    print(f"{BOLD}           ChaosReplay End-to-End Verification Smoke Test               {RESET}")
    print(f"{BOLD}========================================================================{RESET}")
    print(f"  Backend URL  : {backend_url}")
    print(f"  Frontend URL : {frontend_url if not args.skip_frontend else '(skipped)'}")
    print(f"  Generated Trace ID : {BOLD}{trace_id}{RESET}")

    # Step 1: Health check
    log_step(1, "Verifying Backend Health & Actuator Status")
    status, health_body = send_http("GET", f"{backend_url}/actuator/health")
    if status != 200 or health_body.get("status") != "UP":
        log_fail(f"Backend Actuator health check failed (Status {status}): {health_body}")
        sys.exit(1)
    components = list(health_body.get("components", {}).keys())
    log_pass(f"Backend is UP. Health components verified: {', '.join(components) if components else 'UP'}")

    # Step 2: Telemetry Ingestion
    log_step(2, f"Submitting Telemetry Events for Trace: {trace_id}")
    now_ms = int(time.time() * 1000)
    base_iso = time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(now_ms / 1000))

    events = [
        {
            "eventId": f"evt-{run_id}-01",
            "timestamp": f"{base_iso}.000Z",
            "serviceName": "order-service",
            "serviceInstance": "order-node-1",
            "eventType": "REQUEST",
            "severity": "INFO",
            "traceId": trace_id,
            "requestId": f"req-{run_id}",
            "operation": "POST /api/orders",
            "message": "Order placement initiated by customer",
            "metadata": {"cartId": f"cart-{run_id}", "items": 3}
        },
        {
            "eventId": f"evt-{run_id}-02",
            "timestamp": f"{base_iso}.080Z",
            "serviceName": "inventory-service",
            "serviceInstance": "inventory-node-a",
            "eventType": "EXTERNAL_CALL",
            "severity": "INFO",
            "traceId": trace_id,
            "requestId": f"req-{run_id}",
            "operation": "reserveStock",
            "message": "Reserving stock for SKU catalog",
            "metadata": {"sku": "SKU-9901"}
        },
        {
            "eventId": f"evt-{run_id}-03",
            "timestamp": f"{base_iso}.800Z",
            "serviceName": "inventory-service",
            "serviceInstance": "inventory-node-a",
            "eventType": "DATABASE",
            "severity": "FATAL",
            "traceId": trace_id,
            "requestId": f"req-{run_id}",
            "operation": "UPDATE inventory",
            "message": "Database query timed out after 3000ms: Lock wait timeout exceeded",
            "metadata": {"table": "inventory_locks", "timeoutMs": 3000}
        },
        {
            "eventId": f"evt-{run_id}-04",
            "timestamp": f"{base_iso}.860Z",
            "serviceName": "order-service",
            "serviceInstance": "order-node-1",
            "eventType": "RESPONSE",
            "severity": "ERROR",
            "traceId": trace_id,
            "requestId": f"req-{run_id}",
            "operation": "POST /api/orders",
            "message": "Order placement failed with downstream database error 500",
            "metadata": {"httpStatus": 500}
        }
    ]

    for evt in events:
        s, body = send_http("POST", f"{backend_url}/api/v1/telemetry/events", body=evt)
        if s != 201:
            log_fail(f"Failed to ingest event {evt['eventId']} (HTTP {s}): {body}")
            sys.exit(1)
    log_pass(f"Successfully ingested {len(events)} telemetry events across 2 microservices.")

    # Step 3: Incident Timeline Verification
    log_step(3, "Retrieving Incident Timeline & Validating Ordering")
    s, timeline_resp = send_http("GET", f"{backend_url}/api/v1/incidents/traces/{trace_id}/timeline")
    if s != 200:
        log_fail(f"Failed to retrieve incident timeline (HTTP {s}): {timeline_resp}")
        sys.exit(1)

    timeline_entries = timeline_resp.get("timeline", [])
    if len(timeline_entries) != len(events):
        log_fail(f"Expected {len(events)} timeline entries, got {len(timeline_entries)}")
        sys.exit(1)

    # Validate ascending offsets
    prev_offset = -1
    for idx, entry in enumerate(timeline_entries):
        curr_offset = entry.get("offsetMs", 0)
        if curr_offset < prev_offset:
            log_fail(f"Timeline offset disorder at #{idx + 1}: offset {curr_offset}ms < prev {prev_offset}ms")
            sys.exit(1)
        prev_offset = curr_offset

    first_offset = timeline_entries[0].get("offsetMs")
    if first_offset != 0:
        log_fail(f"First timeline event offset must be 0ms, found {first_offset}ms")
        sys.exit(1)

    log_pass(f"Timeline contains {len(timeline_entries)} events, strictly ordered from +0ms to +{prev_offset}ms.")
    log_pass(f"Failure count: {timeline_resp.get('failureCount')}, failures observed: {timeline_resp.get('failureObserved')}")

    # Step 4: Deterministic Replay Scenario Generation
    log_step(4, "Creating Replay Scenario from Trace")
    s, scenario_resp = send_http("POST", f"{backend_url}/api/v1/replay/scenarios/traces/{trace_id}")
    if s not in (200, 201):
        log_fail(f"Failed to create replay scenario (HTTP {s}): {scenario_resp}")
        sys.exit(1)

    scenario_id = scenario_resp.get("scenarioId")
    if not scenario_id or not scenario_id.startswith("scen-"):
        log_fail(f"Invalid or missing scenarioId in response: {scenario_resp}")
        sys.exit(1)
    log_pass(f"Generated Replay Scenario: {BOLD}{scenario_id}{RESET} (Snapshot events: {scenario_resp.get('eventCount')})")

    # Step 5: Deterministic Replay Verification
    log_step(5, f"Executing Replay Verification for Scenario: {scenario_id}")
    s, verify_resp = send_http("POST", f"{backend_url}/api/v1/replay/scenarios/{scenario_id}/verify")
    if s not in (200, 201):
        log_fail(f"Failed to execute replay verification (HTTP {s}): {verify_resp}")
        sys.exit(1)

    verification_id = verify_resp.get("verificationId")
    verdict = verify_resp.get("status")
    if not verification_id or not verification_id.startswith("verify-"):
        log_fail(f"Invalid or missing verificationId: {verify_resp}")
        sys.exit(1)
    if verdict not in ("PASSED", "PARTIAL", "FAILED"):
        log_fail(f"Unexpected verification verdict: {verdict}")
        sys.exit(1)
    log_pass(f"Replay Verification completed: {BOLD}{verdict}{RESET} (ID: {verification_id})")

    # Step 6: Verification Detail & Discrepancy Inspection
    log_step(6, f"Inspecting Replay Verification Metrics: {verification_id}")
    s, verify_detail = send_http("GET", f"{backend_url}/api/v1/replay/verifications/{verification_id}")
    if s != 200:
        log_fail(f"Failed to retrieve verification details (HTTP {s}): {verify_detail}")
        sys.exit(1)
    matched = verify_detail.get("eventsMatched")
    diffs = verify_detail.get("differences", [])
    log_pass(f"Matched events: {matched}, Difference items: {len(diffs)}")

    # Step 7: Deterministic Failure Analysis & Root Cause Evidence
    log_step(7, f"Running Deterministic Failure Analysis for Trace: {trace_id}")
    s, analysis_resp = send_http("POST", f"{backend_url}/api/v1/analysis/traces/{trace_id}")
    if s not in (200, 201):
        log_fail(f"Failed to run failure analysis (HTTP {s}): {analysis_resp}")
        sys.exit(1)

    analysis_id = analysis_resp.get("analysisId")
    conclusion = analysis_resp.get("conclusion")
    confidence = analysis_resp.get("confidenceScore", 0.0)
    candidates = analysis_resp.get("candidates", [])

    if not analysis_id or not analysis_id.startswith("analysis-"):
        log_fail(f"Invalid or missing analysisId: {analysis_resp}")
        sys.exit(1)
    if conclusion not in ("ROOT_CAUSE_CANDIDATE", "MULTIPLE_POSSIBLE_CAUSES"):
        log_fail(f"Unexpected analysis conclusion: {conclusion}")
        sys.exit(1)
    if not (0.0 < confidence <= 1.0):
        log_fail(f"Confidence score out of valid bounds (0.0, 1.0]: {confidence}")
        sys.exit(1)
    if not candidates:
        log_fail(f"Failure analysis returned no candidates: {analysis_resp}")
        sys.exit(1)

    top_candidate = candidates[0]
    log_pass(f"Analysis completed: {BOLD}{conclusion}{RESET} with Confidence: {BOLD}{confidence:.2f}{RESET}")
    log_pass(f"Top Candidate #{top_candidate.get('rank')}: {top_candidate.get('candidateType')} in '{top_candidate.get('serviceName')}' (Event: {top_candidate.get('eventId')})")
    evidence_count = len(top_candidate.get("evidence", []))
    log_pass(f"Top candidate backed by {evidence_count} empirical evidence items.")

    # Step 8: Consolidated Incident Investigation View
    log_step(8, f"Querying Consolidated Incident Investigation View: {trace_id}")
    s, incident_resp = send_http("GET", f"{backend_url}/api/v1/incidents/traces/{trace_id}")
    if s != 200:
        log_fail(f"Failed to retrieve consolidated investigation (HTTP {s}): {incident_resp}")
        sys.exit(1)

    assert incident_resp.get("scenarioAvailable") is True, "scenarioAvailable must be True"
    assert incident_resp.get("verificationAvailable") is True, "verificationAvailable must be True"
    assert incident_resp.get("analysisAvailable") is True, "analysisAvailable must be True"
    assert incident_resp.get("timeline") is not None, "timeline must be present"
    log_pass("Consolidated investigation aggregations verified: Timeline, Scenario, Verification, and Analysis.")

    # Step 9: Frontend & Proxy Check (if not skipped)
    if not args.skip_frontend:
        log_step(9, f"Validating Angular Frontend & Reverse Proxy: {frontend_url}")
        s, f_health = send_http("GET", f"{frontend_url}/api/v1/health")
        if s != 200 or f_health.get("status") != "UP":
            log_info(f"Angular dev server not running or proxy not reachable on {frontend_url} (HTTP {s}).")
            log_info("To run with frontend validation: cd frontend && npm start, then re-run smoke test.")
        else:
            log_pass(f"Frontend proxy `/api/v1/health` answered: {f_health}")
            s, index_html = send_http("GET", f"{frontend_url}/")
            if s == 200:
                log_pass("Frontend application index page is serving successfully.")
    else:
        log_info("Frontend check was skipped via --skip-frontend.")

    print(f"\n{BOLD}{GREEN}========================================================================{RESET}")
    print(f"{BOLD}{GREEN}      ALL END-TO-END SMOKE TEST ASSERTIONS PASSED SUCCESSFULLY!        {RESET}")
    print(f"{BOLD}{GREEN}========================================================================{RESET}")
    print(f"  Incident Trace ID : {BOLD}{trace_id}{RESET}")
    print(f"  Replay Scenario   : {scenario_id}")
    print(f"  Verification ID   : {verification_id} ({verdict})")
    print(f"  Analysis ID       : {analysis_id} ({conclusion}, {confidence * 100:.1f}%)")
    print(f"  Investigate in UI : {frontend_url} -> Search '{trace_id}'\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
