package jp.co.housekeeping.person_management.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 監査ログ閲覧画面（1-7-7）のコントローラテスト。
 */
@WebMvcTest(AuditLogController.class)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @Test
    void 未認証でアクセスするとログイン画面にリダイレクトされる() throws Exception {
        // ブラウザで画面を開く場合を再現するため Accept を明示する
        // （理由は AuthenticationInterceptor のJavadoc参照）
        mockMvc.perform(get("/audit-log").header("Accept", "text/html"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 認証済みでアクセスすると200が返りビュー名がaudit_logである() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticated", true);

        mockMvc.perform(get("/audit-log").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("audit-log"));
    }

    @Test
    void JdbcTemplateが例外を投げても200が返り画面が落ちない() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticated", true);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenThrow(new RuntimeException("access_logs テーブルが存在しません"));

        mockMvc.perform(get("/audit-log").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("audit-log"));
    }
}
