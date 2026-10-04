#!/usr/bin/env bash
set -uo pipefail
BASE=http://localhost:8080
PASS=0; FAIL=0
pass() { echo "✓ PASS: $1"; PASS=$((PASS+1)); }
fail() { echo "✗ FAIL: $1"; FAIL=$((FAIL+1)); }

echo "=========================================="
echo "  COMPREHENSIVE TEST SUITE"
echo "=========================================="

# ---- 1. HEALTH ----
echo ""; echo "--- 1. Health & Actuator ---"
HEALTH=$(curl -sf $BASE/actuator/health)
echo "$HEALTH" | python3 -c "import sys,json; d=json.load(sys.stdin); assert d['status']=='UP'" 2>/dev/null \
  && pass "Health endpoint returns UP" || fail "Health endpoint not UP"

PROM=$(curl -sf $BASE/actuator/prometheus)
echo "$PROM" | grep -q "jvm_memory" \
  && pass "Prometheus metrics endpoint works" || fail "Prometheus metrics missing"

# ---- 2. VALIDATION ----
echo ""; echo "--- 2. Input Validation ---"
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/shows \
  -H "Content-Type: application/json" -d '{}')
[ "$CODE" = "400" ] && pass "Empty show request → 400" || fail "Expected 400, got $CODE"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/shows \
  -H "Content-Type: application/json" -d '{"name":"","seats":[]}')
[ "$CODE" = "400" ] && pass "Blank name + empty seats → 400" || fail "Expected 400, got $CODE"

# ---- 3. AUTH ----
echo ""; echo "--- 3. Authentication ---"
SHOW=$(curl -sf -X POST $BASE/shows -H "Content-Type: application/json" \
  -d '{"name":"Auth Test","seats":["B1","B2","B3"],"price_paise":25000}')
SID=$(echo "$SHOW" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/shows/$SID/reserve \
  -H "Content-Type: application/json" \
  -d '{"seats":["B1"],"idempotency_key":"no-auth"}')
[ "$CODE" = "401" ] && pass "Reserve without auth → 401" || fail "Expected 401, got $CODE"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/reservations/999/cancel)
[ "$CODE" = "401" ] && pass "Cancel without auth → 401" || fail "Expected 401, got $CODE"

# ---- 4. SHOW NOT FOUND ----
echo ""; echo "--- 4. Not Found ---"
CODE=$(curl -s -o /dev/null -w "%{http_code}" $BASE/shows/9999)
[ "$CODE" = "404" ] && pass "Non-existent show → 404" || fail "Expected 404, got $CODE"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/shows/9999/reserve \
  -H "Content-Type: application/json" -H "Authorization: Bearer user-1" \
  -d '{"seats":["X1"],"idempotency_key":"nf-1"}')
[ "$CODE" = "404" ] && pass "Reserve on non-existent show → 404" || fail "Expected 404, got $CODE"

# ---- 5. SEAT NOT FOUND ----
echo ""; echo "--- 5. Seat Not Found ---"
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/shows/$SID/reserve \
  -H "Content-Type: application/json" -H "Authorization: Bearer user-1" \
  -d '{"seats":["Z99"],"idempotency_key":"snf-1"}')
[ "$CODE" = "404" ] && pass "Non-existent seat → 404" || fail "Expected 404, got $CODE"

# ---- 6. RESERVE + CONFLICT ----
echo ""; echo "--- 6. Reservation & Double-sell Prevention ---"
RESP=$(curl -sf -X POST $BASE/shows/$SID/reserve \
  -H "Content-Type: application/json" -H "Authorization: Bearer user-10" \
  -d '{"seats":["B1"],"idempotency_key":"res-1"}')
echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); assert d['status']=='confirmed'" 2>/dev/null \
  && pass "Reserve B1 → confirmed" || fail "Reserve B1 failed"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/shows/$SID/reserve \
  -H "Content-Type: application/json" -H "Authorization: Bearer user-20" \
  -d '{"seats":["B1"],"idempotency_key":"res-2"}')
[ "$CODE" = "409" ] && pass "Double-sell attempt → 409" || fail "Expected 409, got $CODE"

# ---- 7. IDEMPOTENCY ----
echo ""; echo "--- 7. Idempotency ---"
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/shows/$SID/reserve \
  -H "Content-Type: application/json" -H "Authorization: Bearer user-10" \
  -d '{"seats":["B1"],"idempotency_key":"res-1"}')
[ "$CODE" = "201" ] && pass "Idempotent retry → 201" || fail "Expected 201, got $CODE"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/shows/$SID/reserve \
  -H "Content-Type: application/json" -H "Authorization: Bearer user-10" \
  -d '{"seats":["B2"],"idempotency_key":"res-1"}')
[ "$CODE" = "409" ] && pass "Same key + different seats → 409" || fail "Expected 409, got $CODE"

# ---- 8. PER-USER LIMIT ----
echo ""; echo "--- 8. Per-User Limit (max 4) ---"
BIGSHOW=$(curl -sf -X POST $BASE/shows -H "Content-Type: application/json" \
  -d '{"name":"Limit Test","seats":["L1","L2","L3","L4","L5","L6"],"price_paise":10000}')
BSID=$(echo "$BIGSHOW" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/shows/$BSID/reserve \
  -H "Content-Type: application/json" -H "Authorization: Bearer user-limit" \
  -d '{"seats":["L1","L2","L3","L4"],"idempotency_key":"limit-4"}')
[ "$CODE" = "201" ] && pass "Reserve 4 seats (at max) → 201" || fail "Expected 201, got $CODE"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/shows/$BSID/reserve \
  -H "Content-Type: application/json" -H "Authorization: Bearer user-limit" \
  -d '{"seats":["L5"],"idempotency_key":"limit-5"}')
[ "$CODE" = "409" ] && pass "5th seat exceeds limit → 409" || fail "Expected 409, got $CODE"

# ---- 9. CANCELLATION ----
echo ""; echo "--- 9. Cancellation ---"
CANCEL_RESP=$(curl -sf -X POST $BASE/shows/$SID/reserve \
  -H "Content-Type: application/json" -H "Authorization: Bearer user-cancel" \
  -d '{"seats":["B2"],"idempotency_key":"cancel-1"}')
RES_ID=$(echo "$CANCEL_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['reservation_id'])")
pass "Reserved B2 for cancellation test (reservation $RES_ID)"

CANCELLED=$(curl -sf -X POST $BASE/reservations/$RES_ID/cancel \
  -H "Authorization: Bearer user-cancel")
STATUS=$(echo "$CANCELLED" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
[ "$STATUS" = "cancelled" ] && pass "Cancel → status cancelled" || fail "Expected cancelled, got $STATUS"

SHOW_STATE=$(curl -sf $BASE/shows/$SID)
B2_STATUS=$(echo "$SHOW_STATE" | python3 -c "
import sys,json
seats = json.load(sys.stdin)['seats']
b2 = [s for s in seats if s['label']=='B2'][0]
print(b2['status'])")
[ "$B2_STATUS" = "available" ] && pass "Cancelled seat B2 → available" || fail "Expected available, got $B2_STATUS"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/reservations/$RES_ID/cancel \
  -H "Authorization: Bearer user-cancel")
[ "$CODE" = "409" ] && pass "Re-cancel → 409" || fail "Expected 409, got $CODE"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/reservations/$RES_ID/cancel \
  -H "Authorization: Bearer user-wrong")
[ "$CODE" = "404" ] && pass "Wrong user cancel → 404" || fail "Expected 404, got $CODE"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/reservations/99999/cancel \
  -H "Authorization: Bearer user-cancel")
[ "$CODE" = "404" ] && pass "Cancel non-existent → 404" || fail "Expected 404, got $CODE"

# ---- 10. HOLD EXPIRY ----
echo ""; echo "--- 10. Hold Expiry ---"
curl -sf -X POST $BASE/shows/$SID/reserve \
  -H "Content-Type: application/json" -H "Authorization: Bearer user-expiry" \
  -d '{"seats":["B3"],"idempotency_key":"expiry-1"}' > /dev/null

B3_BEFORE=$(curl -sf $BASE/shows/$SID | python3 -c "
import sys,json
seats = json.load(sys.stdin)['seats']
b3 = [s for s in seats if s['label']=='B3'][0]
print(b3['status'])")
[ "$B3_BEFORE" = "confirmed" ] && pass "B3 is confirmed before expiry" || fail "Expected confirmed, got $B3_BEFORE"

echo "  Waiting 16 seconds for hold to expire..."
sleep 16

B3_AFTER=$(curl -sf $BASE/shows/$SID | python3 -c "
import sys,json
seats = json.load(sys.stdin)['seats']
b3 = [s for s in seats if s['label']=='B3'][0]
print(b3['status'])")
[ "$B3_AFTER" = "available" ] && pass "B3 → available after expiry" || fail "Expected available, got $B3_AFTER"

# ---- 11. RECONCILIATION ----
echo ""; echo "--- 11. Reconciliation Invariant ---"
SHOW_FINAL=$(curl -sf $BASE/shows/$SID)
echo "$SHOW_FINAL" | python3 -c "
import sys,json
d = json.load(sys.stdin)
s = d['summary']
total = s['total']
computed = s['available'] + s['held'] + s['confirmed']
print(f'  total={total} available={s[\"available\"]} held={s[\"held\"]} confirmed={s[\"confirmed\"]} sum={computed}')
assert total == computed, f'{computed} != {total}'
" && pass "available + held + confirmed == total" || fail "Reconciliation broken"

# ---- 12. BURST TEST ----
echo ""; echo "--- 12. Burst Concurrency (200 requests, 1 seat) ---"
BURST_SHOW=$(curl -sf -X POST $BASE/shows -H "Content-Type: application/json" \
  -d '{"name":"Burst","seats":["HOT"],"price_paise":50000}')
BURST_ID=$(echo "$BURST_SHOW" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

TMPDIR=$(mktemp -d)
for i in $(seq 1 200); do
  (
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
      -X POST "$BASE/shows/$BURST_ID/reserve" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer user-b-$i" \
      -d "{\"seats\":[\"HOT\"],\"idempotency_key\":\"burst-$i\"}")
    echo "$HTTP_CODE" > "$TMPDIR/code_$i"
  ) &
done
wait

C201=0; C409=0; C5XX=0
for i in $(seq 1 200); do
  CODE=$(cat "$TMPDIR/code_$i")
  case "$CODE" in
    201) C201=$((C201+1)) ;;
    409) C409=$((C409+1)) ;;
    5*)  C5XX=$((C5XX+1)) ;;
  esac
done
rm -rf "$TMPDIR"

echo "  Results: 201=$C201, 409=$C409, 5xx=$C5XX"
[ "$C201" -eq 1 ] && pass "Exactly 1 winner" || fail "Expected 1x 201, got $C201"
[ "$C5XX" -eq 0 ] && pass "Zero 5xx" || fail "$C5XX server errors"

# ---- 13. PROMETHEUS METRICS CHECK ----
echo ""; echo "--- 13. Metrics Verification ---"
METRICS=$(curl -sf $BASE/actuator/prometheus)
echo "$METRICS" | grep -q "booking_reservations_confirmed_total" \
  && pass "Confirmed counter present" || fail "Confirmed counter missing"
echo "$METRICS" | grep -q "booking_reservations_declined_total" \
  && pass "Declined counter present" || fail "Declined counter missing"
echo "$METRICS" | grep -q "booking_seats_available" \
  && pass "Seats available gauge present" || fail "Seats available gauge missing"

# ---- 14. AMOUNT COMPUTATION ----
echo ""; echo "--- 14. Server-side Amount Computation ---"
AMOUNT_SHOW=$(curl -sf -X POST $BASE/shows -H "Content-Type: application/json" \
  -d '{"name":"Price Test","seats":["P1","P2","P3"],"price_paise":15000}')
AMOUNT_SID=$(echo "$AMOUNT_SHOW" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
PRICE=$(echo "$AMOUNT_SHOW" | python3 -c "import sys,json; print(json.load(sys.stdin)['price_paise'])")
[ "$PRICE" = "15000" ] && pass "Show stores price_paise" || fail "Expected 15000, got $PRICE"

AMOUNT_RES=$(curl -sf -X POST $BASE/shows/$AMOUNT_SID/reserve \
  -H "Content-Type: application/json" -H "Authorization: Bearer user-price" \
  -d '{"seats":["P1","P2"],"idempotency_key":"price-1"}')
AMOUNT=$(echo "$AMOUNT_RES" | python3 -c "import sys,json; print(json.load(sys.stdin)['amount_paise'])")
[ "$AMOUNT" = "30000" ] && pass "amount_paise = 2 × 15000 = 30000" || fail "Expected 30000, got $AMOUNT"

# ---- SUMMARY ----
echo ""
echo "=========================================="
echo "  RESULTS: $PASS passed, $FAIL failed"
echo "=========================================="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
