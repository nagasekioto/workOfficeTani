package jp.co.housekeeping.person_management.config;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;

/**
 * CSRF（他サイトからの意図しない操作）対策。
 *
 * 【何を防ぐのか】
 * ログイン中の利用者に細工した外部ページを踏ませると、そのページから
 * このシステムへPOSTを飛ばせてしまう。ブラウザはCookieを自動で付けるため、
 * サーバー側からは本人の操作と区別がつかない。
 * 放置すると {@code /permanent-delete/person/{id}} のような
 * 取り返しのつかない操作を外部から発火させられる。
 *
 * 【なぜトークン方式ではなくOrigin検証なのか】
 * 教科書的な対策は、フォームに使い捨てトークンを埋めて突き合わせる方式。
 * しかし本システムのThymeleafテンプレートには**共通フラグメントが1つも無く**、
 * 40個のフォームが28ファイルに散在している。全部に手で埋め込む必要があり、
 * **1つ埋め忘れるとその画面が使えなくなる**（業務が止まる）。
 * 埋め忘れは画面を開くまで気付けないため、リスクに見合わないと判断した。
 *
 * 代わりに、状態を変えるリクエストの送信元をヘッダで検証する。
 * ブラウザは Origin ヘッダを偽装できない（JavaScriptから書き換えられない）ため、
 * 「このシステム自身の画面から送られたか」を確実に判定できる。
 * テンプレートには一切手を入れないので、埋め忘れによる画面破壊が起こりえない。
 *
 * さらに Cookie に SameSite=Strict を設定済み（application.yml）で、
 * そもそも他サイトからのリクエストにはCookieが付かない。二重の防御になっている。
 *
 * 【この方式の限界】
 * Origin も Referer も送らない古いブラウザからは操作できなくなる。
 * 本システムは1台のPCで最新のブラウザから使う運用のため許容している。
 * 社内の他PCや古い環境へ広げる場合は、トークン方式の追加を検討すること。
 */
public class CsrfInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                              Object handler) throws Exception {

        // GET/HEAD/OPTIONS はデータを変えないので検証しない。
        // (変更を伴うGETを作らないことが前提。作ってはいけない)
        if (isSafeMethod(request.getMethod())) {
            return true;
        }

        String expected = buildExpectedOrigin(request);
        String actual = originOf(request.getHeader("Origin"));

        // Origin が無い場合は Referer から送信元を取り出す。
        // 一部の状況(リダイレクト直後など)で Origin が付かないことがあるため。
        if (actual == null) {
            actual = originOf(request.getHeader("Referer"));
        }

        if (actual != null && actual.equalsIgnoreCase(expected)) {
            return true;
        }

        // どちらのヘッダも無い、または一致しない場合は拒否する。
        // 「判定できないものは通す」にすると、ヘッダを送らない経路が
        // そのまま抜け道になるため、通さない側に倒す。
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
            "リクエストの送信元を確認できませんでした。画面を開き直してからやり直してください。");
        return false;
    }

    private boolean isSafeMethod(String method) {
        return "GET".equalsIgnoreCase(method)
            || "HEAD".equalsIgnoreCase(method)
            || "OPTIONS".equalsIgnoreCase(method);
    }

    /**
     * このシステム自身の origin（scheme://host[:port]）を組み立てる。
     * 設定に書いた固定値ではなくリクエストから作るので、
     * ポート変更やHTTPS化をしても直す必要がない。
     */
    private String buildExpectedOrigin(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();

        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(host);
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                           || ("https".equalsIgnoreCase(scheme) && port == 443);
        if (!defaultPort) {
            sb.append(':').append(port);
        }
        return sb.toString();
    }

    /** ヘッダの値から scheme://host[:port] だけを取り出す。取り出せなければ null。 */
    private String originOf(String headerValue) {
        if (headerValue == null || headerValue.isBlank() || "null".equals(headerValue)) {
            return null;
        }
        try {
            URI uri = new URI(headerValue);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(uri.getScheme()).append("://").append(uri.getHost());
            if (uri.getPort() != -1) {
                sb.append(':').append(uri.getPort());
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
