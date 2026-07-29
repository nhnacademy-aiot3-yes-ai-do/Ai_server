#!/usr/bin/env bash
set -uo pipefail

echo "coverage_percentage=not_available" >> "$GITHUB_OUTPUT"
echo "coverage_threshold=not_available" >> "$GITHUB_OUTPUT"

read_pom_property() {
  ./mvnw -q help:evaluate \
    -Dexpression="$1" \
    -DforceStdout \
    -Dstyle.color=never 2>/dev/null |
    tr -d '\r' |
    tail -n 1
}

SONAR_PROJECT_KEY="$(read_pom_property sonar.projectKey)"
THRESHOLD_RATIO="$(read_pom_property jacoco.minimum.line.coverage)"

if [[ "$THRESHOLD_RATIO" =~ ^(0([.][0-9]+)?|1([.]0+)?)$ ]]; then
  THRESHOLD_PERCENTAGE="$(
    awk -v ratio="$THRESHOLD_RATIO" 'BEGIN { printf "%g", ratio * 100 }'
  )"
  echo "coverage_threshold=$THRESHOLD_PERCENTAGE" >> "$GITHUB_OUTPUT"
else
  echo "::warning::pom.xml에서 커버리지 기준을 읽지 못했습니다: $THRESHOLD_RATIO"
fi

if [ "${SONAR_ANALYSIS_OUTCOME:-skipped}" != "success" ]; then
  echo "::warning::SonarQube 분석이 성공하지 않아 실제 커버리지 조회를 생략합니다."
  exit 0
fi

SONAR_BASE_URL="${SONAR_HOST_URL%/}"

if ! RESPONSE="$(
  curl --fail --show-error --silent \
    --get "$SONAR_BASE_URL/api/measures/component" \
    --header "Authorization: Bearer $SONAR_API_TOKEN" \
    --data-urlencode "component=$SONAR_PROJECT_KEY" \
    --data-urlencode "metricKeys=line_coverage"
)"; then
  echo "::warning::SonarQube 커버리지 조회에 실패했습니다."
  exit 0
fi

COVERAGE="$(
  echo "$RESPONSE" |
    jq -r '.component.measures[]
      | select(.metric == "line_coverage")
      | .value'
)"

if [ -z "$COVERAGE" ] || [ "$COVERAGE" = "null" ]; then
  echo "::warning::SonarQube 응답에 line_coverage 값이 없습니다."
  exit 0
fi

echo "coverage_percentage=$COVERAGE" >> "$GITHUB_OUTPUT"
