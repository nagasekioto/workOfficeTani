package jp.co.housekeeping.person_management.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.RegisterRecordRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;

/**
 * GET /register/fee-ledger/pdf の month パラメータ検証に関する回帰テスト。
 * ヘッダーインジェクション対策として月のフォーマット検証が効いていることを確認する。
 */
@WebMvcTest(RegisterController.class)
class RegisterControllerFeeLedgerPdfTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonRepository personRepository;
    @MockitoBean
    private RegisterRecordRepository registerRecordRepository;
    @MockitoBean
    private SalesRepository salesRepository;
    @MockitoBean
    private SalesDetailRepository salesDetailRepository;
    @MockitoBean
    private CustomerRepository customerRepository;

    @Test
    void 未認証でアクセスすると401になる() throws Exception {
        mockMvc.perform(get("/register/fee-ledger/pdf").param("month", "2026-07"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 認証済みかつ正常な月ならPDFが返りファイル名にも月が反映される() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticated", true);

        mockMvc.perform(get("/register/fee-ledger/pdf")
                        .session(session)
                        .param("month", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("fee-ledger-2026-07.pdf")));
    }

    @Test
    void 認証済みでも不正な形式の月なら400になる() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticated", true);

        mockMvc.perform(get("/register/fee-ledger/pdf")
                        .session(session)
                        .param("month", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void CRLFを含む月を渡すとヘッダーインジェクションされず400になる() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticated", true);

        mockMvc.perform(get("/register/fee-ledger/pdf")
                        .session(session)
                        .param("month", "2026-07\r\nX-Injected: yes"))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("X-Injected"));
    }
}
