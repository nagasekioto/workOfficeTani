package jp.co.housekeeping.person_management.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 認証チェックがInterceptorで集約されていることの確認。
 *
 * 各コントローラの手書きチェックではなく、Interceptor1箇所で守れているかを見る。
 * ここが壊れると「無認証で個人情報が見える」か「誰もログインできない」の
 * どちらかになるため、両方向を検証する。
 *
 * DBを見るBeanが必要なため @WebMvcTest ではなく @SpringBootTest を使う。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    // ============================================================
    // 未認証は弾かれること
    // ============================================================

    @Test
    void 未認証でブラウザから業務画面を開くとログイン画面へ飛ばされる() throws Exception {
        mockMvc.perform(get("/menu").header(HttpHeaders.ACCEPT, "text/html"))
               .andExpect(status().is3xxRedirection())
               .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")));
    }

    @Test
    void 未認証の画面遷移でない要求には401を返す() throws Exception {
        // fetch/XHR などにログイン画面のHTMLを返しても解釈できないため401にしている
        mockMvc.perform(get("/menu").header(HttpHeaders.ACCEPT, "application/json"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void 未認証でウェルカムページを開くとログイン画面へ飛ばされる() throws Exception {
        // index.html は Spring Boot のウェルカムページとして GET / で配信されており、
        // コントローラを経由しないため、手書きの認証チェックが一切効いていなかった。
        // Interceptor方式にしたことで初めてこの経路が守られる。
        mockMvc.perform(get("/").header(HttpHeaders.ACCEPT, "text/html"))
               .andExpect(status().is3xxRedirection())
               .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")));
    }

    @Test
    void 未認証でログイン利用者管理を開くとログイン画面へ飛ばされる() throws Exception {
        // /auth/setup を除外しているせいで /auth/** まで通してしまっていないかの確認。
        // ここが通ると、他人のTOTP登録を無効化したりバックアップコードを再発行できてしまう。
        mockMvc.perform(get("/auth/users").header(HttpHeaders.ACCEPT, "text/html"))
               .andExpect(status().is3xxRedirection())
               .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")));
    }

    // ============================================================
    // 通してよい経路は塞がないこと
    // ============================================================

    @Test
    void 未認証でもログイン画面は開ける() throws Exception {
        // ここが壊れると誰もログインできなくなる（システム全体が使用不能）
        mockMvc.perform(get("/login").header(HttpHeaders.ACCEPT, "text/html"))
               .andExpect(status().isOk());
    }

    @Test
    void 認証済みならログイン画面へ飛ばされない() throws Exception {
        mockMvc.perform(get("/menu")
                    .header(HttpHeaders.ACCEPT, "text/html")
                    .sessionAttr("authenticated", true))
               .andExpect(result -> {
                   String location = result.getResponse().getHeader("Location");
                   if (location != null && location.endsWith("/login")) {
                       throw new AssertionError(
                           "認証済みなのにログイン画面へ飛ばされた: " + location);
                   }
               });
    }

    // ============================================================
    // 監査ログとの共存
    // ============================================================

    @Test
    void 未認証で弾かれても例外にならず監査ログ側の処理が完走する() throws Exception {
        // AuditLogInterceptor は登録順が先なので、認証で弾かれた場合でも
        // afterCompletion が呼ばれて不正アクセスの試行が記録される。
        //
        // 記録されたレコードの中身までは、DBの状態に依存して他テストと
        // 干渉するため、ここでは検証しない。
        // 「弾かれた経路でも監査ログの処理が例外を投げず、レスポンスが
        // 正しく返ること」までを確認する。
        // （AuditLogService 自体の記録内容は AuditLogServiceTest で検証済み）
        mockMvc.perform(get("/person/list").header(HttpHeaders.ACCEPT, "text/html"))
               .andExpect(status().is3xxRedirection());
    }
}
