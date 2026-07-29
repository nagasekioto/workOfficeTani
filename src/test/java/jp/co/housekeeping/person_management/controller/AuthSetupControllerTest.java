package jp.co.housekeeping.person_management.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jp.co.housekeeping.person_management.service.AuditLogService;
import jp.co.housekeeping.person_management.service.AuthUserService;
import jp.co.housekeeping.person_management.service.QrCodeService;
import jp.co.housekeeping.person_management.service.SecretCipher;

/**
 * AuthSetupController（初回セットアップ・利用者管理）のコントローラテスト。
 *
 * 特に重要: 初回セットアップ画面は「利用者が1人も居ない」かつ「このPC自身(loopback)からのアクセス」
 * の両方を満たさないとアクセスできない。GET/POSTの両方で守られていることを確認する
 * （GET側だけ守っても、フォームを介さず直接POSTされたら意味がないため）。
 */
@WebMvcTest(AuthSetupController.class)
class AuthSetupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthUserService authUserService;

    @MockitoBean
    private QrCodeService qrCodeService;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private SecretCipher secretCipher;

    // MockHttpServletRequestの既定のremoteAddrは"127.0.0.1"(loopback)のため、
    // 明示的に上書きしない限りテストはloopbackからのアクセスとして扱われる。

    @Test
    void 利用者が既に居るときGET_auth_setupはloginにリダイレクトされる() throws Exception {
        when(authUserService.hasNoActiveUser()).thenReturn(false);

        mockMvc.perform(get("/auth/setup"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loopback以外のIPからのGET_auth_setupはloginにリダイレクトされる() throws Exception {
        when(authUserService.hasNoActiveUser()).thenReturn(true);

        mockMvc.perform(get("/auth/setup")
                        .with(request -> { request.setRemoteAddr("192.168.1.50"); return request; }))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loopback以外のIPからのPOST_auth_setupも拒否される() throws Exception {
        when(authUserService.hasNoActiveUser()).thenReturn(true);

        mockMvc.perform(post("/auth/setup").header("Origin", "http://localhost")
                        .param("username", "tani")
                        .param("displayName", "谷")
                        .with(request -> { request.setRemoteAddr("192.168.1.50"); return request; }))
                .andExpect(redirectedUrl("/login"));

        // createUserが呼ばれていない = 実際に登録処理が実行されていないこと
        org.mockito.Mockito.verify(authUserService, org.mockito.Mockito.never())
                .createUser(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 未ログイン状態でGET_auth_usersはloginにリダイレクトされる() throws Exception {
        // ブラウザで画面を開く場合を再現するため Accept を明示する
        // （理由は AuthenticationInterceptor のJavadoc参照）
        mockMvc.perform(get("/auth/users").header("Accept", "text/html"))
                .andExpect(redirectedUrl("/login"));
    }

    // ─── 登録完了エンドポイントのアクセス制御（重要） ─────────────
    // セッションに登録手続きが記録されていない相手は、userIdを指定しても
    // 他人の登録を完了させられない（バックアップコードを奪えない）ことを確認する。

    @Test
    void 登録手続きを開始していない相手のPOST_enroll_confirmは拒否される() throws Exception {
        mockMvc.perform(post("/auth/enroll/confirm").header("Origin", "http://localhost").param("code", "123456"))
                .andExpect(redirectedUrl("/login"));

        // completeEnrollmentが一度も呼ばれていない = 登録処理が走っていないこと
        org.mockito.Mockito.verify(authUserService, org.mockito.Mockito.never())
                .completeEnrollment(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void セッションに他人のuserIdをフォームで送っても登録は完了しない() throws Exception {
        // 攻撃者が他人のuserIdをパラメータに付けて直接POSTしても、
        // サーバーはフォームのuserIdを一切見ないため登録は進まない
        mockMvc.perform(post("/auth/enroll/confirm").header("Origin", "http://localhost")
                        .param("code", "123456")
                        .param("userId", "1"))
                .andExpect(redirectedUrl("/login"));

        org.mockito.Mockito.verify(authUserService, org.mockito.Mockito.never())
                .completeEnrollment(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 登録手続き中でもコード入力の試行回数には上限がある() throws Exception {
        AuthUserService.EnrollmentInfo info = new AuthUserService.EnrollmentInfo();
        info.userId = 1L;
        info.username = "tani";
        info.secretBase32 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        info.otpAuthUri = "otpauth://totp/test";

        org.springframework.mock.web.MockHttpSession session =
                new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("pendingEnrollment", info);
        session.setAttribute("pendingEnrollmentAttempts", 5); // 上限に到達済み

        mockMvc.perform(post("/auth/enroll/confirm").header("Origin", "http://localhost").param("code", "123456").session(session))
                .andExpect(redirectedUrl("/login?error"));

        org.mockito.Mockito.verify(authUserService, org.mockito.Mockito.never())
                .completeEnrollment(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString());
    }
}
