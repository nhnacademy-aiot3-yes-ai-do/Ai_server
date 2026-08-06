package site.yesaido.ai_server.dto;
/*
제네릭 <T>를 사용해 데이터에 String, List 등 무엇이든 담을 수 있게 구현
공통 응답 포맷 및 handler 적용
변경사항
1. ApiResponse<T>를 작성하여 구조 통일
2. handler 생성하여 모든 예외 정해진 규격(ApiResponse.error)에 따라 401 등 HTTP 상태 코드와 함께 응답하도록 수정
 */

public record ApiResponse<T>(
        boolean success, // 성공/실패 여부
        String message, // 메시지
        T data // 데이터
) {
    public static <T> ApiResponse<T> success(T data){ // 성공했을 때 호출할 메서드(데이터 포함)
        return new ApiResponse<>(true, "요청이 성공적으로 처리되었습니다.", data);
    }
}
