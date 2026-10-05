#!/usr/bin/env python3
"""Small repeatable HTTP load probe for the public doctor-detail endpoint."""

import argparse
import concurrent.futures
import datetime
import json
import platform
import threading
import time
from collections import Counter

import requests


THREAD_LOCAL = threading.local()


def session_for_thread():
    session = getattr(THREAD_LOCAL, "session", None)
    if session is None:
        session = requests.Session()
        THREAD_LOCAL.session = session
    return session


def percentile(values, ratio):
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, int(round((len(ordered) - 1) * ratio))))
    return round(ordered[index], 3)


def request_once(url, timeout):
    started = time.perf_counter()
    try:
        response = session_for_thread().get(url, timeout=timeout)
        elapsed_ms = (time.perf_counter() - started) * 1000
        valid = response.status_code == 200
        if valid:
            try:
                valid = response.json().get("success") is True
            except (ValueError, AttributeError):
                valid = False
        return elapsed_ms, response.status_code, valid, None if valid else response.text[:200]
    except requests.RequestException as exc:
        elapsed_ms = (time.perf_counter() - started) * 1000
        return elapsed_ms, 0, False, str(exc)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:8081")
    parser.add_argument("--doctor-id", type=int, default=1)
    parser.add_argument("--requests", type=int, default=2000)
    parser.add_argument("--concurrency", type=int, default=20)
    parser.add_argument("--warmup", type=int, default=100)
    parser.add_argument("--timeout", type=float, default=5.0)
    parser.add_argument("--output", help="Optional path to write the JSON result")
    args = parser.parse_args()

    if args.requests < 1 or args.concurrency < 1 or args.warmup < 0:
        parser.error("requests and concurrency must be positive; warmup cannot be negative")

    url = args.url.rstrip("/") + "/doctor/" + str(args.doctor_id)
    for _ in range(args.warmup):
        _, status, valid, error = request_once(url, args.timeout)
        if not valid:
            raise SystemExit("Warm-up request failed: HTTP {} {}".format(status, error or ""))

    start_gate = threading.Event()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = [pool.submit(lambda: (start_gate.wait(), request_once(url, args.timeout))[1])
                   for _ in range(args.requests)]
        started = time.perf_counter()
        wall_started = datetime.datetime.now(datetime.timezone.utc).astimezone().isoformat()
        start_gate.set()
        results = [future.result() for future in futures]
        elapsed_s = time.perf_counter() - started

    latencies = [item[0] for item in results]
    statuses = Counter(str(item[1]) for item in results)
    failures = [item for item in results if not item[2]]
    report = {
        "started_at": wall_started,
        "target": url,
        "host": platform.node(),
        "platform": platform.platform(),
        "python": platform.python_version(),
        "requests_library": requests.__version__,
        "warmup_requests": args.warmup,
        "measured_requests": args.requests,
        "concurrency": args.concurrency,
        "elapsed_seconds": round(elapsed_s, 3),
        "throughput_requests_per_second": round(args.requests / elapsed_s, 2),
        "latency_ms": {
            "mean": round(sum(latencies) / len(latencies), 3),
            "p50": percentile(latencies, 0.50),
            "p95": percentile(latencies, 0.95),
            "p99": percentile(latencies, 0.99),
            "max": round(max(latencies), 3),
        },
        "http_status_counts": dict(statuses),
        "error_count": len(failures),
        "sample_errors": [item[3] for item in failures[:5]],
        "note": "Single-host demo probe; includes client and local network overhead and is not a production capacity claim.",
    }
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    print(rendered)
    if args.output:
        with open(args.output, "w", encoding="utf-8") as result_file:
            result_file.write(rendered + "\n")
    if failures:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
