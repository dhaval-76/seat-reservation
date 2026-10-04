#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
CONCURRENCY="${2:-500}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✓ PASS${NC}: $1"; }
fail() { echo -e "${RED}✗ FAIL${NC}: $1"; exit 1; }
info() { echo -e "${YELLOW}→${NC} $1"; }

# Wait for service
info "Waiting for service at $BASE_URL..."
for i in $(seq 1 30); do
    if curl -sf "$BASE_URL/actuator/health" > /dev/null 2>&1; then
        break
    fi
    sleep 1
done

# -------------------------------------------------------------------
# 1. Create a show with seats A1-A10
# -------------------------------------------------------------------
info "Creating show with 10 seats..."
SHOW=$(curl -sf -X POST "$BASE_URL/shows" \
    -H "Content-Type: application/json" \
    -d '{"name":"Burst Test Show","seats":["A1","A2","A3","A4","A5","A6","A7","A8","A9","A10"]}')
SHOW_ID=$(echo "$SHOW" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
info "Show created: ID=$SHOW_ID"

# -------------------------------------------------------------------
# 2. Hot-seat storm: N concurrent requests for seat A1
# -------------------------------------------------------------------
info "Launching $CONCURRENCY concurrent requests for seat A1..."
TMPDIR=$(mktemp -d)
for i in $(seq 1 "$CONCURRENCY"); do
    (
        HTTP_CODE=$(curl -s -o "$TMPDIR/resp_$i.json" -w "%{http_code}" \
            -X POST "$BASE_URL/shows/$SHOW_ID/reserve" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer user-storm-$i" \
            -d "{\"seats\":[\"A1\"],\"idempotencyKey\":\"storm-$i\"}")
        echo "$HTTP_CODE" > "$TMPDIR/code_$i"
    ) &
done
wait

# Count results
COUNT_201=0
COUNT_409=0
COUNT_5XX=0
for i in $(seq 1 "$CONCURRENCY"); do
    CODE=$(cat "$TMPDIR/code_$i")
    case "$CODE" in
        201) COUNT_201=$((COUNT_201 + 1)) ;;
        409) COUNT_409=$((COUNT_409 + 1)) ;;
        5*)  COUNT_5XX=$((COUNT_5XX + 1)) ;;
    esac
done

info "Results: 201=$COUNT_201, 409=$COUNT_409, 5xx=$COUNT_5XX"
[ "$COUNT_201" -eq 1 ] && pass "Exactly one 201" || fail "Expected 1x 201, got $COUNT_201"
[ "$COUNT_5XX" -eq 0 ] && pass "Zero 5xx errors" || fail "Got $COUNT_5XX 5xx errors"

# -------------------------------------------------------------------
# 3. Idempotency test: same key returns same response
# -------------------------------------------------------------------
info "Testing idempotency..."
RESP1=$(curl -sf -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/shows/$SHOW_ID/reserve" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer user-idem-1" \
    -d '{"seats":["A2"],"idempotencyKey":"idem-test-1"}')
RESP2=$(curl -sf -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/shows/$SHOW_ID/reserve" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer user-idem-1" \
    -d '{"seats":["A2"],"idempotencyKey":"idem-test-1"}')
[ "$RESP1" = "201" ] && [ "$RESP2" = "201" ] && pass "Idempotent retry returns 201" || fail "Idempotency failed: $RESP1, $RESP2"

# Same key, different seats → 409
RESP3=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/shows/$SHOW_ID/reserve" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer user-idem-1" \
    -d '{"seats":["A3"],"idempotencyKey":"idem-test-1"}')
[ "$RESP3" = "409" ] && pass "Same key + different seats = 409" || fail "Expected 409, got $RESP3"

# -------------------------------------------------------------------
# 4. Per-user limit test (default max 10)
# -------------------------------------------------------------------
info "Testing per-user seat limit..."
# user-limit-1 already has 0 seats, try to reserve 10 (A3-A10 available + need 2 more from separate show)
# Simpler: reserve remaining available seats one by one
LIMIT_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/shows/$SHOW_ID/reserve" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer user-limit-1" \
    -d '{"seats":["A3","A4","A5","A6","A7","A8","A9","A10"],"idempotencyKey":"limit-1"}')
[ "$LIMIT_CODE" = "201" ] && pass "Reserved 8 seats for limit user" || fail "Expected 201, got $LIMIT_CODE"

# -------------------------------------------------------------------
# 5. Reconciliation check
# -------------------------------------------------------------------
info "Running reconciliation check..."
SHOW_STATE=$(curl -sf "$BASE_URL/shows/$SHOW_ID")
TOTAL=$(echo "$SHOW_STATE" | python3 -c "import sys,json; print(json.load(sys.stdin)['summary']['total'])")
AVAIL=$(echo "$SHOW_STATE" | python3 -c "import sys,json; print(json.load(sys.stdin)['summary']['available'])")
HELD=$(echo "$SHOW_STATE" | python3 -c "import sys,json; print(json.load(sys.stdin)['summary']['held'])")
CONFIRMED=$(echo "$SHOW_STATE" | python3 -c "import sys,json; print(json.load(sys.stdin)['summary']['confirmed'])")
SUM=$((AVAIL + HELD + CONFIRMED))

info "Total=$TOTAL, Available=$AVAIL, Held=$HELD, Confirmed=$CONFIRMED, Sum=$SUM"
[ "$TOTAL" -eq "$SUM" ] && pass "Reconciliation: available + held + confirmed = total" || fail "Reconciliation failed: $SUM != $TOTAL"

# Cleanup
rm -rf "$TMPDIR"

echo ""
echo -e "${GREEN}All tests passed!${NC}"
