package site.yesaido.ai_server.dto.common;
/*
제네릭 <T>를 사용해 데이터에 String, List 등 무엇이든 담을 수 있게 구현
ApiResponse<T>를 작성하여 구조 통일
 */
public record ApiResponse<T>( // 공통 응답 DTO
        boolean success, // 성공/실패 여부
        String message, // 메시지
        T data // 데이터
) {
    public static <T> ApiResponse<T> success(T data){ // 성공했을 때 호출할 메서드(데이터 포함)
        return new ApiResponse<>(true, "요청이 성공적으로 처리되었습니다.", data);
    }
}
