package jp.co.housekeeping.person_management.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import jp.co.housekeeping.person_management.model.Person;
import jp.co.housekeeping.person_management.repository.CustomerRepository;
import jp.co.housekeeping.person_management.repository.PersonRepository;
import jp.co.housekeeping.person_management.repository.SalesDetailRepository;
import jp.co.housekeeping.person_management.repository.SalesRepository;

/**
 * 高②(脆弱性報告)：POST /person/sales/print に認証チェックがなく、
 * 未ログインでも他人の売上PDFを生成できてしまっていたバグの回帰テスト。
 * あわせて、存在しないpersonIdを指定した場合に404になることも確認する。
 */
@WebMvcTest(SalesController.class)
class SalesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PersonRepository personRepository;
    @MockBean
    private CustomerRepository customerRepository;
    @MockBean
    private SalesRepository salesRepository;
    @MockBean
    private SalesDetailRepository salesDetailRepository;

    @Test
    void 未ログインで売上印刷すると401になる() throws Exception {
        mockMvc.perform(post("/person/sales/print").param("personId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ログイン済みでも存在しないpersonIdなら404になる() throws Exception {
        when(personRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/person/sales/print")
                        .sessionAttr("authenticated", true)
                        .param("personId", "999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ログイン済みかつ存在するpersonIdならPDFが生成される() throws Exception {
        Person person = new Person();
        person.setId(1L);
        when(personRepository.findById(1L)).thenReturn(Optional.of(person));

        mockMvc.perform(post("/person/sales/print")
                        .sessionAttr("authenticated", true)
                        .param("personId", "1"))
                .andExpect(status().isOk());
    }
}
