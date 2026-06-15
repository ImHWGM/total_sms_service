#!/usr/bin/env bash
# =====================================================================
# CI Guard: legacy user.STATUS write 신규 발생 차단
# =====================================================================
# 목적: PR1 이후 코드에서 legacy `STATUS` 한글 enum 컬럼에 write/비교가
#       새로 추가되지 않도록 차단. lifecycle_status 단일 진입점 강제.
#
# 통과 조건: 매칭 count <= BASELINE 환경변수 (또는 .legacy-status-baseline)
# 실패 시:    exit 1 + 위반 위치 출력
#
# 사용법:
#   bash scripts/check-legacy-status-writes.sh
#   BASELINE=5 bash scripts/check-legacy-status-writes.sh
#
# Allowlist (검사 제외):
#   - docs/sql/migrations/        (마이그레이션 SQL 본체)
#   - domain/user/entity/User.java   (legacy fallback 메서드 보유)
#   - 본 스크립트 자체
# =====================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

BASELINE="${BASELINE:-0}"
if [ -f .legacy-status-baseline ]; then
  BASELINE="$(cat .legacy-status-baseline | tr -d '[:space:]')"
fi

# 검사 패턴 (legacy 한글 STATUS 컬럼 쓰기/유입만 대상)
# - Java setStatus("승인"|"미승인"|"탈퇴") — User entity 의 한글 STATUS 컬럼 직접 쓰기
# - Java .status = "승인" 직접 대입
# - SQL UPDATE user SET STATUS — 마이그레이션 외부에서 STATUS 컬럼 쓰기
PATTERNS=(
  'setStatus\(\s*"(승인|미승인|탈퇴)"'
  'set\s*\(\s*"STATUS"'
  '\.status\s*=\s*"(승인|미승인|탈퇴)"'
  'UPDATE\s+`?user`?\s+SET\s+`?STATUS`?\s*='
)

EXCLUDES=(
  'docs/sql/migrations/'
  'src/main/java/kr/wisead/domain/user/entity/User.java'
  'scripts/check-legacy-status-writes.sh'
  '.legacy-status-baseline'
)

EXCLUDE_ARGS=()
for ex in "${EXCLUDES[@]}"; do
  EXCLUDE_ARGS+=("--glob" "!${ex}")
done

if ! command -v rg >/dev/null 2>&1; then
  echo "[guard] ripgrep (rg) 가 필요합니다. 설치 후 재시도하세요." >&2
  exit 2
fi

count=0
violations=$(mktemp)
trap 'rm -f "$violations"' EXIT

for pat in "${PATTERNS[@]}"; do
  # shellcheck disable=SC2068
  if hits="$(rg -n --no-heading --color never -e "$pat" ${EXCLUDE_ARGS[@]} 2>/dev/null)"; then
    if [ -n "$hits" ]; then
      echo "[pattern] $pat" >>"$violations"
      echo "$hits" >>"$violations"
      echo "" >>"$violations"
      this=$(echo "$hits" | wc -l | tr -d '[:space:]')
      count=$((count + this))
    fi
  fi
done

echo "[guard] legacy STATUS write 매칭 수: $count (baseline: $BASELINE)"
if [ "$count" -gt "$BASELINE" ]; then
  echo "[guard] FAIL: baseline 초과. 위반 위치:" >&2
  cat "$violations" >&2
  echo "" >&2
  echo "fix 안내:" >&2
  echo "  - lifecycle_status (LifecycleStatus enum) 사용으로 전환" >&2
  echo "  - 진정 fallback 이 필요하면 User.isActive() 내부에만 한정" >&2
  echo "  - 마이그레이션 SQL 은 docs/sql/migrations/ 하위에 두어 allowlist" >&2
  exit 1
fi

echo "[guard] OK"
