#!/bin/sh
set -eu

: "${FLAG:?FLAG is not set}"
: "${CSA_TOKEN:?CSA_TOKEN is not set}"
: "${CSA_REGISTER_URL:?CSA_REGISTER_URL is not set}"
: "${CSA_REGISTER_SECRET:?CSA_REGISTER_SECRET is not set}"

TTL="${CSA_REGISTER_TTL_SECONDS:-86400}"
TEAM_ID="${TEAM_ID:-}"
CSA_REGISTER_NO_PROXY="${CSA_REGISTER_NO_PROXY:-*}"
CSA_REGISTER_CONNECT_TIMEOUT="${CSA_REGISTER_CONNECT_TIMEOUT:-3}"
CSA_REGISTER_MAX_TIME="${CSA_REGISTER_MAX_TIME:-8}"

if [ -z "${CSA_CLAIM_CALLBACK_SECRET:-}" ]; then
  CSA_CLAIM_CALLBACK_SECRET="$(head -c 32 /dev/urandom | sha256sum | cut -d ' ' -f 1)"
fi

if [ -z "${CSA_CLAIM_CALLBACK_URL:-}" ]; then
  pod_ip="$(hostname -i 2>/dev/null | awk '{print $1}')"
  CSA_CLAIM_CALLBACK_URL="http://${pod_ip:-127.0.0.1}:8080/claim"
fi

payload="$(jq -cn \
  --arg token "$CSA_TOKEN" \
  --arg flag "$FLAG" \
  --arg team_id "$TEAM_ID" \
  --arg callback_url "$CSA_CLAIM_CALLBACK_URL" \
  --arg callback_secret "$CSA_CLAIM_CALLBACK_SECRET" \
  --argjson ttl_seconds "$TTL" \
  '{token:$token,flag:$flag,team_id:$team_id,ttl_seconds:$ttl_seconds,callback_url:$callback_url,callback_secret:$callback_secret}')"

curl -fsS \
  --noproxy "$CSA_REGISTER_NO_PROXY" \
  --connect-timeout "$CSA_REGISTER_CONNECT_TIMEOUT" \
  --max-time "$CSA_REGISTER_MAX_TIME" \
  -X POST "$CSA_REGISTER_URL" \
  -H 'Content-Type: application/json' \
  -H "X-CSA-Secret: $CSA_REGISTER_SECRET" \
  --data "$payload"

printf '\nCSA_TOKEN=%s\n' "$CSA_TOKEN"
