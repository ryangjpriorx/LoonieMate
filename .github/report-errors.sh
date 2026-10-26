#!/usr/bin/env bash
# Turns the important lines of a failed Gradle build into GitHub annotations, so the errors are
# readable from the run's summary page (and through the API) without downloading the full log.
log="$1"; [ -f "$log" ] || exit 0
enc() { sed -e 's/%/%25/g' -e 's#file:///home/runner/work/[^/]*/[^/]*/##g' | sed -e ':a;N;$!ba;s/\r//g;s/\n/%0A/g'; }
# GitHub keeps at most 10 error annotations per step: the full summary goes first
summary=$(grep -E '^e: |What went wrong|^> |error:|Exception|FAILED|Could not' "$log" | grep -v '^> Task ' | head -200 | enc)
[ -n "$summary" ] && echo "::error title=Build log::$summary"
# a failed test's own message (what it checked and what went wrong), which the summary above leaves out
grep -E -A10 'AssertionError|ComposeTimeoutException|AssertionFailedError|IllegalStateException|NoSuchElementException' "$log" | grep -vE '^\s+at (org\.junit|java\.|jdk\.|sun\.|org\.robolectric|androidx\.test)' | head -40 | enc | {
  read -r t; [ -n "$t" ] && echo "::error title=Test failure::$t"; }
grep -E '^e: ' "$log" | head -8 | while IFS= read -r line; do
  echo "::error title=Kotlin::$(printf '%s' "$line" | enc)"
done
exit 0
