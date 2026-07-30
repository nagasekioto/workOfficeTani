/*
 * 数字入力（テンキー）の共通処理
 *
 * 【なぜ必要か】
 * この画面群は氏名カナ・住所など日本語入力が続くため、日本語IMEをONにしたまま
 * 電話番号や郵便番号の欄へ移動することになる。その状態でテンキーを打つと
 * IMEが「０９０」のような全角数字を作ってしまい、
 *   - formatPhone() の /[^\d]/ は全角を数字と見なさないので中身が消える
 *   - DBにも全角のまま入り、検索・並び替えが合わなくなる
 * という形で「テンキーで入力できない」状態になる。
 *
 * 【対策は2段構え】
 * 1) 電話番号・郵便番号の欄は type="tel" にする。
 *    Chromium系(Chrome/Edge)のWindowsでは tel はIMEが無効化されるため、
 *    IMEがONのままでもテンキーがそのまま半角で入る（これが本命の修正）。
 * 2) それでも全角が入り得る経路（貼り付け、他ブラウザ、番地欄など）に備え、
 *    data-hankaku を付けた欄で全角→半角へ自動変換する（この保険）。
 *
 * 【使い方】
 *   data-hankaku="tel"    数字・ハイフン類を半角化（電話番号・郵便番号・番地）
 *   data-hankaku="digits" 数字だけを半角化（「１０１号室」→「101号室」のように
 *                         日本語が混ざる欄。長音記号をハイフンに変えてはいけない）
 */
(function () {
    'use strict';

    // 全角数字 → 半角数字
    function digitsToHankaku(s) {
        return s.replace(/[０-９]/g, function (c) {
            return String.fromCharCode(c.charCodeAt(0) - 0xFEE0);
        });
    }

    // 全角数字に加えて、ハイフンとして打たれ得る記号をすべて半角ハイフンへ寄せる。
    // テンキーの「-」はIME経由だと長音「ー」やダッシュ類になることがある。
    function telToHankaku(s) {
        return digitsToHankaku(s)
            .replace(/[ー－―‐‑–—−]/g, '-')
            .replace(/　/g, '');
    }

    function convert(el) {
        var mode = el.getAttribute('data-hankaku');
        if (!mode) return null;
        var before = el.value;
        var after = (mode === 'digits') ? digitsToHankaku(before) : telToHankaku(before);
        return (after === before) ? null : after;
    }

    // 自分が発火させた input で再入しないようにするための番人。
    // dispatchEvent は同期実行なので、この単純なフラグで足りる。
    var busy = false;

    function normalize(el) {
        if (busy) return;
        if (!el || !el.getAttribute || !el.getAttribute('data-hankaku')) return;

        var after = convert(el);
        if (after === null) return;

        // カーソル位置を保つ（全角→半角で文字数は変わらないため、そのまま戻せる）
        var start = null, end = null;
        try { start = el.selectionStart; end = el.selectionEnd; } catch (e) { /* 対応外のtypeは無視 */ }

        el.value = after;

        if (start !== null) {
            try { el.setSelectionRange(start, end); } catch (e) { /* 同上 */ }
        }

        // 画面側の oninput（住所の自動結合など）へ変換後の値を届ける
        busy = true;
        try {
            el.dispatchEvent(new Event('input', { bubbles: true }));
        } finally {
            busy = false;
        }
    }

    // IME変換中に値を書き換えると変換自体が壊れるので、
    // 確定後（compositionend）・通常入力・欄を離れたとき の3点で整える。
    document.addEventListener('compositionend', function (e) {
        normalize(e.target);
    });

    document.addEventListener('input', function (e) {
        if (e.isComposing) return;
        normalize(e.target);
    });

    document.addEventListener('focusout', function (e) {
        normalize(e.target);
    });
})();
