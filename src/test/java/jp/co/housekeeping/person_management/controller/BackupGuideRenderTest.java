package jp.co.housekeeping.person_management.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * /backup-guide（1-7-5）に、最終バックアップ状態の表示ブロックが追加されたことを固定する。
 *
 * 【なぜこのテストが必要か】
 * バックアップが3週間止まっていても誰も気付けなかった問題への対策として、画面の先頭に
 * 現況（BackupStatusService の結果）を表示するようにした。この表示自体が将来の改修で
 * 失われないよう、レンダリング結果に見出しが含まれることだけを固定する。
 *
 * 実行環境によってバックアップの有無・件数は異なる（OKかNG/WARNかは環境依存）ため、
 * 色分けや具体的な日時・件数など環境依存の内容は検証しない。画面が200で開き、
 * 状態ブロックの見出しが出ていることだけを確認する。
 */
@SpringBootTest
@AutoConfigureMockMvc
class BackupGuideRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void backupGuide画面に現在のバックアップ状態の見出しが表示される() throws Exception {
        String html = mockMvc.perform(get("/backup-guide").sessionAttr("authenticated", true))
                              .andExpect(status().isOk())
                              .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("現在のバックアップ状態"),
            "/backup-guide に「現在のバックアップ状態」の見出しが表示されていない");
    }
}
