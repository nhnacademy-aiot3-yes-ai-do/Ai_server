package site.yesaido.ai_server.context;

/**
 * 인증된 실제 사용자 ID를 현재 쓰레드에 안전하게 보관하는 컨텍스트 홀더
 * (LLM이 userId를 조작하거나 위조할 수 없도록 서버에서 직접 통제하는 보안 장치)
 */
public class UserContextHolder {
    private static final ThreadLocal<Long> CONTEXT = new ThreadLocal<>();

    private UserContextHolder() {}

    public static void setUserId(Long userId) {
        CONTEXT.set(userId);
    }

    public static Long getUserId() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
