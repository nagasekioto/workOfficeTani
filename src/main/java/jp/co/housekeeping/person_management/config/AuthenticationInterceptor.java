package jp.co.housekeeping.person_management.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 認証チェックを1箇所に集約するInterceptor。
 *
 * これまで認証は各コントローラが
 * {@code session.getAttribute("authenticated")} を手書きで確認する方式で、
 * 16ファイル・52箇所に散らばっていた。
 * この方式は「新しい画面を足したときに書き漏らすと、その画面だけ無認証で
 * 個人情報が見えてしまう」という弱点があり、実際に認証チェック漏れの回帰が
 * 起きたことがある（コミット e349c58）。
 *
 * さらに調査の結果、{@code templates/index.html} が Spring Boot の
 * ウェルカムページとして {@code GET /} で配信されており、
 * **コントローラを経由しないため認証チェックが一切効いていない**ことが分かった。
 * 手書き方式では、そもそもコントローラが無い経路を守れない。
 *
 * そのため「既定で全部を守り、通してよい経路だけを明示的に除外する」方式にする。
 * 除外リストは {@link WebMvcConfig} 側に置く。
 *
 * 【未認証時の返し方について】
 * ブラウザで画面を開いた場合（Acceptに text/html を含む）はログイン画面へ誘導し、
 * それ以外（fetch/XHR など）は401を返す。画面遷移でない相手にログイン画面のHTMLを
 * 返しても解釈できないため。
 *
 * PDF出力などをリンクから開いた場合は Accept に text/html が入るため、
 * 401ではなくログイン画面へ遷移する。従来この種のエンドポイントは個別に401を
 * 返していたが、画面から使う限りはログイン画面が出るほうが利用者に分かりやすいため、
 * 意図的にこの挙動にしている。
 */
public class AuthenticationInterceptor implements HandlerInterceptor {

    /** ログイン成功時に {@code LoginController} がセッションに立てる印。 */
    static final String SESSION_AUTHENTICATED = "authenticated";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                              Object handler) throws Exception {

        // セッションを新規作成しないよう getSession(false) を使う。
        // 未認証リクエストのたびにセッションが増えるのを防ぐため。
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_AUTHENTICATED) != null) {
            return true;
        }

        if (expectsHtml(request)) {
            response.sendRedirect(request.getContextPath() + "/login");
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
        return false;
    }

    /**
     * ブラウザでの画面遷移かどうか。
     * Acceptヘッダが無い相手（プログラムからの呼び出し等）は画面遷移ではないと判断する。
     */
    private boolean expectsHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }
}
