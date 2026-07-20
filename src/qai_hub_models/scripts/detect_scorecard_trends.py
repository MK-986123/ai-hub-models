# ---------------------------------------------------------------------
# Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------
"""Detect sustained regressions by comparing current scorecard against historical runs.

Classifies regressions as:
- sustained: appears in >= (lookback - 1) of the last N runs AND in the current run
- new: in the current run but not seen in any of the last N runs
- recovered: appeared in history but NOT in the current run
- flaky: appears in only 1 of the last N runs

Usage:
    python -m qai_hub_models.scripts.detect_scorecard_trends \
        --current-regressions build/scorecard-yamls/123/perf-regressions-2x-2026-05-10.json \
        --lookback 4 \
        --output trend-report.json
"""

import argparse
import json
import logging
import tempfile
from collections import Counter
from pathlib import Path

from qai_hub_models.scorecard.history import (
    RecoveredRegression,
    TrendReport,
    TrendSummary,
)
from qai_hub_models.scorecard.results.performance_diff import SevereRegression
from qai_hub_models.scripts.download_scorecard_results import S3_PREFIX, list_runs
from qai_hub_models.utils.aws import (
    QAIHM_PRIVATE_S3_BUCKET,
    get_qaihm_s3_or_exit,
    s3_download,
    s3_file_exists,
)

logger = logging.getLogger(__name__)


def _load_historical_regressions(lookback: int) -> list[list[SevereRegression]]:
    """Download perf-regressions-2x.json from the last N runs."""
    bucket, _ = get_qaihm_s3_or_exit(QAIHM_PRIVATE_S3_BUCKET)
    manifests = list_runs(last=lookback)

    historical: list[list[SevereRegression]] = []
    for manifest in manifests:
        run_id = manifest.run_id
        # Construct key directly — layout is deterministic: {PREFIX}/{run_id}/file
        regression_key = f"{S3_PREFIX}/{run_id}/perf-regressions-2x.json"
        if not s3_file_exists(bucket, regression_key):
            logger.warning(f"No regressions file for run {run_id}, skipping")
            historical.append([])
            continue

        with tempfile.NamedTemporaryFile(suffix=".json") as tmp:
            s3_download(bucket, regression_key, tmp.name, verbose=False)
            with open(tmp.name) as f:
                entries = json.load(f)
        historical.append([SevereRegression.model_validate(e) for e in entries])

    return historical


def detect_trends(
    current_regressions: list[SevereRegression],
    historical: list[list[SevereRegression]],
    threshold: int = 2,
) -> TrendReport:
    """Classify regressions based on their frequency across historical runs."""
    current_keys = {r.key for r in current_regressions}

    # Count how many times each regression appears in history
    history_counts: Counter[str] = Counter()
    all_historical_keys: set[str] = set()
    for run_regressions in historical:
        run_keys = {r.key for r in run_regressions}
        all_historical_keys.update(run_keys)
        history_counts.update(run_keys)

    n = len(historical)
    sustained = []
    new = []
    flaky = []

    # Classify current regressions
    for r in current_regressions:
        count = history_counts.get(r.key, 0)
        if count >= threshold:
            sustained.append(r)
        elif count == 0:
            new.append(r)
        else:
            flaky.append(r)

    # Find recovered: in history (>= threshold) but not current
    recovered = [
        RecoveredRegression(key=key, count=history_counts[key], of=n)
        for key in all_historical_keys - current_keys
        if history_counts[key] >= threshold
    ]

    return TrendReport(
        sustained=sustained,
        new=new,
        recovered=recovered,
        flaky=flaky,
        summary=TrendSummary(
            lookback=n,
            threshold=threshold,
            total_current=len(current_regressions),
            sustained_count=len(sustained),
            new_count=len(new),
            flaky_count=len(flaky),
            recovered_count=len(recovered),
        ),
    )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Detect sustained regressions by comparing current scorecard against history."
    )
    parser.add_argument(
        "--current-regressions",
        type=Path,
        required=True,
        help="Path to current perf-regressions-2x-*.json",
    )
    parser.add_argument(
        "--lookback", type=int, default=4, help="Number of past runs to compare against"
    )
    parser.add_argument(
        "--threshold",
        type=int,
        default=2,
        help="Min appearances in history to be 'sustained'",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("trend-report.json"),
        help="Output path for trend report",
    )
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO)

    # Load current regressions
    with open(args.current_regressions) as f:
        current = [SevereRegression.model_validate(e) for e in json.load(f)]

    # Load historical data from S3
    logger.info(f"Fetching regressions from last {args.lookback} scorecard runs...")
    historical = _load_historical_regressions(args.lookback)

    # Detect trends
    report = detect_trends(current, historical, threshold=args.threshold)

    # Write output
    report.to_json(args.output, exclude_defaults=False, exclude_none=False)

    # Print summary
    s = report.summary
    print(
        f"Trend Report ({s.total_current} current regressions, {s.lookback} runs lookback):"
    )
    print(
        f"  Sustained: {s.sustained_count} (appeared in >={args.threshold} of last {s.lookback} runs)"
    )
    print(f"  New:       {s.new_count} (first time)")
    print(f"  Flaky:     {s.flaky_count} (intermittent)")
    print(f"  Recovered: {s.recovered_count} (were sustained, now gone)")
    print(f"Report written to {args.output}")


if __name__ == "__main__":
    main()
