package jp.co.housekeeping.person_management.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jp.co.housekeeping.person_management.model.AuthUser;
import jp.co.housekeeping.person_management.service.AuditLogService;
import jp.co.housekeeping.person_management.service.AuthUserService;

/**
 * ログイン処理のテスト。
 *
 * 認証は Google Authenticator（TOTP）の一本のみで、
 * 共通パスワード方式・メール認証方式は削除済み。
 * 「削除した経路が本当に消えているか」の確認もここで行う
 * （設定1つで二要素認証を迂回できる抜け道が残っていないことの回帰テスト）。
 */
@WebMvcTest(LoginController.class)
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthUserService authUserService;

    @MockitoBean
    private AuditLogService auditLogService;

    private AuthUser activeUser() {
        AuthUser user = new AuthUser();
        user.setId(1L);
        user.setUsername("tani");
        user.setEnrolledAt(java.time.LocalDateTime.now());
        return user;
    }

    // ─── 削除した認証経路が残っていないこと ─────────────────────

    @Test
    void 共通パスワード方式のPOST_loginは存在しない() throws Exception {
        // /login は表示(GET)のみ。POSTの受け口を削除したため405が返る
        // （認証されず、/menu へも進まないことが要点）
        mockMvc.perform(post("/login").header("Origin", "http://localhost").param("password", "tani"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void メール認証のエンドポイントは存在しない() throws Exception {
        mockMvc.perform(post("/login/email/request").header("Origin", "http://localhost").param("email", "a@example.com"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/login/email/verify").header("Origin", "http://localhost").param("code", "123456"))
                .andExpect(status().isNotFound());
    }

    // ─── ログイン画面 ────────────────────────────────────────

    @Test
    void 利用者が居ればログイン画面が表示される() throws Exception {
        when(authUserService.hasNoActiveUser()).thenReturn(false);

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void 利用者が0人かつこのPCからなら初回セットアップへ誘導される() throws Exception {
        when(authUserService.hasNoActiveUser()).thenReturn(true);

        mockMvc.perform(get("/login"))
                .andExpect(redirectedUrl("/auth/setup"));
    }

    @Test
    void 利用者が0人でも別のPCからならセットアップへ誘導しない() throws Exception {
        when(authUserService.hasNoActiveUser()).thenReturn(true);

        mockMvc.perform(get("/login")
                        .with(request -> { request.setRemoteAddr("192.168.1.50"); return request; }))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    // ─── TOTPログイン ───────────────────────────────────────

    @Test
    void 正しいコードならメニューへ進む() throws Exception {
        AuthUser user = activeUser();
        when(authUserService.findByUsername("tani")).thenReturn(Optional.of(user));
        when(authUserService.verifyTotp(any(), anyString())).thenReturn(true);

        mockMvc.perform(post("/login/totp").header("Origin", "http://localhost").param("username", "tani").param("code", "123456"))
                .andExpect(redirectedUrl("/menu"));
    }

    @Test
    void 誤ったコードならエラー付きでログイン画面に戻る() throws Exception {
        AuthUser user = activeUser();
        when(authUserService.findByUsername("tani")).thenReturn(Optional.of(user));
        when(authUserService.verifyTotp(any(), anyString())).thenReturn(false);
        when(authUserService.verifyBackupCode(any(), anyString())).thenReturn(false);

        mockMvc.perform(post("/login/totp").header("Origin", "http://localhost").param("username", "tani").param("code", "000000"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void 存在しない利用者名でも誤コードと同じ応答になる() throws Exception {
        when(authUserService.findByUsername("nobody")).thenReturn(Optional.empty());

        mockMvc.perform(post("/login/totp").header("Origin", "http://localhost").param("username", "nobody").param("code", "123456"))
                .andExpect(redirectedUrl("/login?error"));

        // 存在しない利用者に対して照合処理を走らせない（応答時間の差から存在を推測されないため）
        verify(authUserService, never()).verifyTotp(any(), anyString());
    }

    @Test
    void バックアップコードでもログインできる() throws Exception {
        AuthUser user = activeUser();
        when(authUserService.findByUsername("tani")).thenReturn(Optional.of(user));
        when(authUserService.verifyTotp(any(), anyString())).thenReturn(false);
        when(authUserService.verifyBackupCode(any(), anyString())).thenReturn(true);
        when(authUserService.countUnusedBackupCodes(1L)).thenReturn(9);

        mockMvc.perform(post("/login/totp").header("Origin", "http://localhost").param("username", "tani").param("code", "ABCD-EFGH"))
                .andExpect(redirectedUrl("/menu"));
    }

    // ─── メニュー・ログアウト ─────────────────────────────────

    @Test
    void 未ログインでメニューを開くとログイン画面に戻される() throws Exception {
        // ブラウザで画面を開く場合を再現するため Accept を明示する。
        // MockMvcは既定でAcceptを送らないが、実際のブラウザは text/html を送る。
        // AuthenticationInterceptor は画面遷移かどうかでログイン画面へのリダイレクトと
        // 401を出し分けるため、ヘッダが無いと401になりリダイレクトを検証できない。
        mockMvc.perform(get("/menu").header("Accept", "text/html"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void ログアウトするとログイン画面に戻る() throws Exception {
        mockMvc.perform(get("/logout"))
                .andExpect(redirectedUrl("/login"));
    }
}
