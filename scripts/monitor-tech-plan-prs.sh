#!/usr/bin/env bash
set -euo pipefail

# Configurable detection settings.
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-600}"
GH_REPO="${GH_REPO:-}"
STATE_FILE="${STATE_FILE:-.codex/processed-tech-plan-prs.json}"
RUN_ONCE="${RUN_ONCE:-0}"

TITLE_KEYWORDS=(
  "technical plan"
  "tech plan"
  "technical-plan"
  "tech-plan"
)

LABEL_KEYWORDS=(
  "technical-plan"
  "tech-plan"
  "architecture"
  "planning"
)

BODY_KEYWORDS=(
  "Technical Plan"
  "Tech Plan"
  "Implementation Plan"
  "Proposed Solution"
)

READY_DRAFT_LABELS=(
  "ready-for-implementation"
)

log() {
  printf '[%s] %s\n' "$(date -Is)" "$*"
}

die() {
  log "ERROR: $*"
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

resolve_repo() {
  if [[ -n "$GH_REPO" ]]; then
    printf '%s\n' "$GH_REPO"
    return
  fi

  local remote_url
  remote_url="$(git remote get-url origin 2>/dev/null || true)"
  [[ -n "$remote_url" ]] || die "Unable to infer repository. Set GH_REPO=owner/repo."

  case "$remote_url" in
    git@github.com:*)
      remote_url="${remote_url#git@github.com:}"
      remote_url="${remote_url%.git}"
      ;;
    https://github.com/*)
      remote_url="${remote_url#https://github.com/}"
      remote_url="${remote_url%.git}"
      ;;
    *)
      die "Unsupported origin URL for repo inference: $remote_url. Set GH_REPO=owner/repo."
      ;;
  esac

  printf '%s\n' "$remote_url"
}

ensure_state_file() {
  mkdir -p "$(dirname "$STATE_FILE")"
  if [[ ! -f "$STATE_FILE" ]]; then
    printf '[]\n' >"$STATE_FILE"
  fi
}

json_array() {
  python3 - "$@" <<'PY'
import json
import sys

print(json.dumps(sys.argv[1:]))
PY
}

poll_once() {
  local repo="$1"
  local title_keywords_json label_keywords_json body_keywords_json ready_draft_labels_json
  title_keywords_json="$(json_array "${TITLE_KEYWORDS[@]}")"
  label_keywords_json="$(json_array "${LABEL_KEYWORDS[@]}")"
  body_keywords_json="$(json_array "${BODY_KEYWORDS[@]}")"
  ready_draft_labels_json="$(json_array "${READY_DRAFT_LABELS[@]}")"

  log "Polling open pull requests for $repo"

  local prs_json
  prs_json="$(
    gh pr list \
      --repo "$repo" \
      --state open \
      --limit 100 \
      --json number,title,author,labels,url,isDraft,body,files
  )"

  PRS_JSON="$prs_json" python3 - \
    "$STATE_FILE" \
    "$title_keywords_json" \
    "$label_keywords_json" \
    "$body_keywords_json" \
    "$ready_draft_labels_json" <<'PY'
import json
import os
import sys

state_file = sys.argv[1]
title_keywords = json.loads(sys.argv[2])
label_keywords = json.loads(sys.argv[3])
body_keywords = json.loads(sys.argv[4])
ready_draft_labels = {label.lower() for label in json.loads(sys.argv[5])}

prs = json.loads(os.environ["PRS_JSON"])

try:
    with open(state_file, "r", encoding="utf-8") as handle:
        processed = set(json.load(handle))
except (FileNotFoundError, json.JSONDecodeError):
    processed = set()

def contains_any(value, keywords, *, case_sensitive=False):
    if value is None:
        value = ""
    haystack = value if case_sensitive else value.lower()
    for keyword in keywords:
        needle = keyword if case_sensitive else keyword.lower()
        if needle in haystack:
            return True
    return False

def label_names(pr):
    return [label.get("name", "") for label in pr.get("labels", [])]

def is_technical_plan(pr):
    labels = label_names(pr)
    labels_text = "\n".join(labels)
    return (
        contains_any(pr.get("title", ""), title_keywords)
        or contains_any(labels_text, label_keywords)
        or contains_any(pr.get("body", ""), body_keywords, case_sensitive=True)
    )

def is_ready_draft(pr):
    labels = {label.lower() for label in label_names(pr)}
    return bool(labels & ready_draft_labels)

candidate = None
for pr in sorted(prs, key=lambda item: item["number"]):
    number = str(pr["number"])
    if number in processed:
        continue
    if pr.get("isDraft") and not is_ready_draft(pr):
        continue
    if is_technical_plan(pr):
        candidate = pr
        break

print(f"OPEN_PRS={len(prs)}")
if candidate is None:
    print("DETECTED=0")
    sys.exit(0)

number = str(candidate["number"])
processed.add(number)
with open(state_file, "w", encoding="utf-8") as handle:
    json.dump(sorted(processed, key=int), handle, indent=2)
    handle.write("\n")

author = candidate.get("author") or {}
labels = label_names(candidate)
files = candidate.get("files") or []

print("DETECTED=1")
print(f"PR_NUMBER={candidate['number']}")
print(f"PR_TITLE={candidate.get('title', '')}")
print(f"PR_AUTHOR={author.get('login', '')}")
print(f"PR_LABELS={', '.join(labels) if labels else '(none)'}")
print(f"PR_URL={candidate.get('url', '')}")
print("PR_CHANGED_FILES_BEGIN")
for file in files:
    print(file.get("path", ""))
print("PR_CHANGED_FILES_END")
print("PR_BODY_BEGIN")
print(candidate.get("body") or "")
print("PR_BODY_END")
PY
}

main() {
  require_command git
  require_command gh
  require_command python3

  local repo
  repo="$(resolve_repo)"
  ensure_state_file

  log "Starting technical plan PR monitor"
  log "Repository: $repo"
  log "Poll interval: ${POLL_INTERVAL_SECONDS}s"
  log "State file: $STATE_FILE"

  while true; do
    local output
    if ! output="$(poll_once "$repo")"; then
      log "Polling failed; will retry after ${POLL_INTERVAL_SECONDS}s"
      printf '%s\n' "$output"
    else
      printf '%s\n' "$output"
      if grep -q '^DETECTED=1$' <<<"$output"; then
        log "New technical plan PR detected. Monitoring loop paused for implementation workflow."
        exit 0
      fi
    fi

    if [[ "$RUN_ONCE" == "1" ]]; then
      log "RUN_ONCE=1 set; exiting after one polling cycle"
      exit 0
    fi

    log "Sleeping for ${POLL_INTERVAL_SECONDS}s"
    sleep "$POLL_INTERVAL_SECONDS"
  done
}

main "$@"
