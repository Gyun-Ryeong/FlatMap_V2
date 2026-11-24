#!/usr/bin/env bash
# 저장소 위생 점검: 추적 파일에 비밀값·대용량 파일이 없는지 확인한다. 문제가 있으면 종료 코드 1.
#
#   bash scripts/check-repo-hygiene.sh
#
# 점검 항목
#   1. 대용량 추적 파일 (기본 1MiB 초과, MAX_BYTES 로 바꿀 수 있다)
#   2. 올라가면 안 되는 이름의 추적 파일 (.env, 키/인증서, DB 덤프, DEM 래스터, application-local.*)
#   3. 비밀값 패턴 검색 (개인 키 블록, 클라우드·토큰 접두사, 키/비밀번호 대입문)
#   4. 알려진 비밀값 정확 일치 검색 — 로컬에 .env / application-local.properties 가 있을 때만(CI 에는 없다).
#      이름이 KEY/SECRET/TOKEN/PASSWORD 인 항목의 값(8자 이상)이 추적 파일이나 git 이력에 있는지 본다.
#
# 비밀값 자체는 어디에도 출력하지 않는다. 걸리면 키 이름과 파일 경로·커밋 해시만 알려 준다.
set -u
cd "$(git rev-parse --show-toplevel)" || exit 2

MAX_BYTES="${MAX_BYTES:-1048576}"
problems=0

# 크기 기준을 넘어도 의도해서 추적하는 파일(이유를 같이 적는다). 새로 추가할 때는 이유 없이 늘리지 않는다.
#  - 행정안전부 급경사지 현황 CSV: 공개 데이터(공공데이터포털)이며 CsvImportService 가 클래스패스 리소스로 읽는다.
ALLOW_LARGE=(
  "backend/src/main/resources/data/행정안전부_급경사지 현황_20250617.csv"
)
is_allowed_large() {
  local f="$1" a
  for a in "${ALLOW_LARGE[@]}"; do [ "$f" = "$a" ] && return 0; done
  return 1
}

note() { printf '%s\n' "$*"; }
problem() { problems=$((problems + 1)); printf '  [문제] %s\n' "$*"; }

# ---------------------------------------------------------------------------
note "== 1. 대용량 추적 파일 (> ${MAX_BYTES} bytes) =="
big=0
while IFS= read -r -d '' f; do
  [ -f "$f" ] || continue
  size=$(wc -c < "$f")
  if [ "$size" -gt "$MAX_BYTES" ]; then
    if is_allowed_large "$f"; then
      note "  허용 목록: $f ($size bytes)"
    else
      big=$((big + 1))
      problem "$f ($size bytes)"
    fi
  fi
done < <(git ls-files -z)
[ "$big" -eq 0 ] && note "  허용 목록 밖의 대용량 파일 없음"

# ---------------------------------------------------------------------------
note "== 2. 올라가면 안 되는 이름의 추적 파일 =="
bad=$(git ls-files | grep -E '(^|/)\.env($|\.)|\.(pem|key|p12|jks|dump|tif|tiff)$|(^|/)application-local\.' | grep -vE '(^|/)\.env\.example$' || true)
if [ -n "$bad" ]; then
  while IFS= read -r f; do problem "$f"; done <<< "$bad"
else
  note "  없음"
fi

# ---------------------------------------------------------------------------
note "== 3. 비밀값 패턴 검색 =="
# 테스트·CI 용 가짜 값은 이름으로 구분한다(test-only / ci_only / example / placeholder / your-).
fake='test-only|ci_only|ci-only|never-leak|example|placeholder|your[-_]|changeme|dummy|xxxx'
pattern_hits=0
scan() { # 설명 정규식
  local label="$1" regex="$2" out
  out=$(git grep -I -n -E -e "$regex" -- . ':!*.lock' ':!frontend/package-lock.json' 2>/dev/null | grep -viE "$fake" || true)
  if [ -n "$out" ]; then
    while IFS= read -r line; do
      pattern_hits=$((pattern_hits + 1))
      # 값이 새지 않도록 "파일:줄" 까지만 출력한다.
      problem "$label — $(printf '%s' "$line" | cut -d: -f1,2)"
    done <<< "$out"
  fi
}
scan "개인 키 블록" '-----BEGIN [A-Z ]*PRIVATE KEY-----'
scan "AWS 액세스 키" 'AKIA[0-9A-Z]{16}'
scan "GitHub 토큰" 'gh[pousr]_[A-Za-z0-9]{36,}'
scan "Slack 토큰" 'xox[baprs]-[A-Za-z0-9-]{10,}'
scan "키/비밀번호 대입(값이 20자 이상 리터럴)" '(api[_-]?key|secret|token|passw(or)?d)[A-Za-z0-9_.-]*[[:space:]]*[:=][[:space:]]*["'"'"']?[A-Za-z0-9/+_=-]{20,}'
[ "$pattern_hits" -eq 0 ] && note "  없음"

# ---------------------------------------------------------------------------
note "== 4. 알려진 비밀값 정확 일치 검색 (로컬 .env / application-local.properties) =="
checked=0
check_value() { # 출처 키 값
  local source="$1" key="$2" value="$3"
  value="${value%$'\r'}"
  value="${value%\"}"; value="${value#\"}"; value="${value%\'}"; value="${value#\'}"
  [ "${#value}" -ge 8 ] || return 0
  case "$value" in \$\{*) return 0 ;; esac            # ${ENV} 참조는 값이 아니다
  checked=$((checked + 1))
  local files commits
  files=$(git grep -I -l -F -e "$value" -- . 2>/dev/null || true)
  commits=$(git log --all --format=%h -S"$value" 2>/dev/null | tr '\n' ' ')
  if [ -n "$files" ]; then
    while IFS= read -r f; do problem "$source 의 $key 값이 추적 파일에 있음: $f"; done <<< "$files"
  fi
  if [ -n "${commits// /}" ]; then
    problem "$source 의 $key 값이 git 이력에 있음(커밋: ${commits})"
  fi
}
scan_file() { # 파일
  local f="$1" line key value
  [ -f "$f" ] || return 0
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    case "$line" in ''|'#'*) continue ;; esac
    key="${line%%=*}"; value="${line#*=}"
    key="${key## }"; key="${key%% }"
    printf '%s' "$key" | grep -qiE 'KEY|SECRET|TOKEN|PASSWORD' || continue
    check_value "$f" "$key" "$value"
  done < "$f"
}
scan_file .env
scan_file backend/.env
scan_file backend/src/main/resources/application-local.properties
if [ "$checked" -eq 0 ]; then
  note "  비밀값 파일이 없어 건너뜀(CI 에서는 정상)"
else
  note "  확인한 값 ${checked}개 (이름이 KEY/SECRET/TOKEN/PASSWORD 이고 8자 이상)"
fi

# ---------------------------------------------------------------------------
echo
if [ "$problems" -eq 0 ]; then
  note "결과: 문제 0건 (비밀값·대용량 파일 없음)"
  exit 0
fi
note "결과: 문제 ${problems}건"
exit 1
