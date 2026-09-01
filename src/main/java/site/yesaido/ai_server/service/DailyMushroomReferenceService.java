package site.yesaido.ai_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoListResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoResponse;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * 일일 피드백에 사용할 버섯 참조정보를 조회하고 ID별로 제공합니다.
 *
 * <p>Cultivation Server에는 버섯 참조정보 전체를 반환하는 API만
 * 공개되어 있으므로, 일일 피드백 배치 시작 시 전체 정보를 한 번 조회해
 * 해당 배치가 사용하는 참조정보 Snapshot으로 보관합니다. 경작지마다
 * 전체 API를 반복 호출하거나 장기 캐시를 사용하지 않습니다.</p>
 *
 * <p>버섯 ID Map은 경작지 상세 응답의 {@code mushroomId}를
 * 한국어 버섯 이름 및 센서 참조 임계값과 연결하는 데 사용합니다.
 * ID 오름차순의 결정적인 반복 순서는 실행마다 동일한 Context와
 * LLM 입력 순서를 만드는 데 필요합니다.</p>
 *
 * <p>전체 참조정보가 없는 빈 목록은 표현 가능한 정상 응답으로
 * 허용합니다. 다만 실제 대상 경작지의 버섯 ID가 Map에 없다면
 * {@link #requireReference(Map, Long)}에서 즉시 실패합니다.</p>
 *
 * <p>현재 참조 응답에는 하드코딩된 재배 기간이나 수확일 정책이
 * 포함되어 있지 않으므로 이 서비스에서 임의의 기간 정책을 만들거나
 * 수확일을 계산하지 않습니다. 임계값 목록은 빈 목록을 허용하며,
 * 개별 임계값의 세부 계약은 이후 수확 정책 계층에서 검증합니다.</p>
 *
 * <p>참조정보 조회 실패나 외부 응답 계약 위반을 빈 Map 또는
 * 기본 버섯 정보로 숨기지 않고 상위 계층으로 전파합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class DailyMushroomReferenceService {

    private final CultivationClient cultivationClient;

    /**
     * 전체 버섯 참조정보를 한 번 조회하여 ID 오름차순 Map으로 반환합니다.
     *
     * @return 버섯 ID를 키로 갖는 정렬되고 수정 불가능한 참조정보 Map
     * @throws IllegalStateException 최상위 응답이나 필수 응답 계약이 잘못된 경우
     */
    public Map<Long, MushroomReferenceInfoResponse> fetchAllById() {
        MushroomReferenceInfoListResponse response = cultivationClient.getMushroomReference();

        if (response == null) {
            throw new IllegalStateException("버섯 참조정보 최상위 응답이 null입니다.");
        }

        if (response.mushroomReferenceInfoResponses() == null) {
            throw new IllegalStateException("버섯 참조정보 목록이 null입니다.");
        }

        TreeMap<Long, MushroomReferenceInfoResponse> referencesById = new TreeMap<>();

        for (MushroomReferenceInfoResponse reference : response.mushroomReferenceInfoResponses()) {
            if (reference == null) {
                throw new IllegalStateException("버섯 참조정보 목록에 null 요소가 있습니다.");
            }

            if (reference.id() <= 0) {
                throw new IllegalStateException("버섯 참조정보 ID는 0보다 커야 합니다: mushroomId=%s"
                        .formatted(reference.id()));
            }

            if (reference.mushroomNameKo() == null || reference.mushroomNameKo().isBlank()) {
                throw new IllegalStateException("버섯 참조정보의 한국어 이름이 유효하지 않습니다: mushroomId=%s"
                                .formatted(reference.id()));
            }

            if (reference.thresholdInfoResponses() == null) {
                throw new IllegalStateException("버섯 참조 임계값 목록이 null입니다: mushroomId=%s"
                                .formatted(reference.id()));
            }

            MushroomReferenceInfoResponse previous = referencesById.putIfAbsent(reference.id(), reference);

            if (previous != null) {
                throw new IllegalStateException("동일한 버섯 참조정보 ID가 중복되었습니다: mushroomId=%s"
                        .formatted(reference.id()));
            }
        }

        return Collections.unmodifiableMap(referencesById);
    }

    /**
     * 경작지의 버섯 ID에 해당하는 참조정보를 반환합니다.
     *
     * @param referencesById 배치 시작 시 조회한 버섯 참조정보 Map
     * @param mushroomId 찾을 버섯 참조정보 ID
     * @return Map에 저장된 참조정보 객체
     * @throws IllegalArgumentException Map 또는 mushroomId가 유효하지 않은 경우
     * @throws IllegalStateException 해당 mushroomId의 참조정보가 없는 경우
     */
    public MushroomReferenceInfoResponse requireReference(
            Map<Long, MushroomReferenceInfoResponse> referencesById, Long mushroomId) {
        if (referencesById == null) {
            throw new IllegalArgumentException("referencesById는 null일 수 없습니다.");
        }

        if (mushroomId == null || mushroomId <= 0) {
            throw new IllegalArgumentException("mushroomId는 null이 아니며 0보다 커야 합니다.");
        }

        MushroomReferenceInfoResponse reference = referencesById.get(mushroomId);

        if (reference == null) {
            throw new IllegalStateException("대상 버섯의 참조정보가 존재하지 않습니다: mushroomId=%s".formatted(mushroomId));
        }

        return reference;
    }
}
