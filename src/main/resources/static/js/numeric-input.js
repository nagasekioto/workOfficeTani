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
 *
 * 【data-numeric（数字以外を弾く）】
 * data-hankaku はあくまで「全角→半角の変換」であり、かな・漢字・英字はそのまま
 * 通してしまう。郵便番号や電話番号のように「半角数字（電話番号はハイフンも）以外は
 * そもそも入力させたくない」欄には、これに加えて data-numeric を付ける。
 *   data-numeric="digits" 半角数字 [0-9] 以外の文字をすべて取り除く（郵便番号など）
 *   data-numeric="tel"    半角数字とハイフン `-` 以外の文字をすべて取り除く（電話番号）
 * 処理順序は必ず「全角→半角変換 → data-numeric による除去」。
 * 全角数字は消さずに半角化してから残す（「０９０」と打たれたら "090" が残る）。
 * data-hankaku を付けていない欄（例: 検索窓）に data-numeric だけを付けた場合でも、
 * 全角数字は半角化してから絞り込む（消えてしまわないようにするため）。
 */
(function () {
    'use strict';

    // 全角数字 → 半角数字（1文字）
    function baseHankakuChar(c) {
        var code = c.charCodeAt(0);
        if (code >= 0xFF10 && code <= 0xFF19) {
            return String.fromCharCode(code - 0xFEE0);
        }
        return c;
    }

    // 全角数字の半角化に加えて、ハイフンとして打たれ得る記号を半角ハイフンへ寄せ、
    // 全角スペースは取り除く（1文字）。
    // テンキーの「-」はIME経由だと長音「ー」やダッシュ類になることがある。
    function telHankakuChar(c) {
        c = baseHankakuChar(c);
        if (/[ー－―‐‑–—−]/.test(c)) return '-';
        if (c === '　') return '';
        return c;
    }

    // data-hankaku の値に応じた1文字変換。
    // data-hankaku が付いていない欄で data-numeric だけを使う場合も、
    // 全角数字だけは半角化してから絞り込みたいので既定値は digits 相当にする
    // （data-numeric="tel" なら長音・ダッシュ類も半角ハイフンへ寄せる。
    //   寄せずに絞り込むと、IME経由で打たれた「ー」が消えてしまうため）。
    function hankakuChar(c, mode) {
        return (mode === 'tel') ? telHankakuChar(c) : baseHankakuChar(c);
    }

    // data-numeric による1文字フィルタ。既に半角化された後の文字に対して適用する。
    function numericFilterChar(c, mode) {
        if (c === '') return '';
        if (mode === 'digits') return /^[0-9]$/.test(c) ? c : '';
        if (mode === 'tel') return /^[0-9-]$/.test(c) ? c : '';
        return c;
    }

    // 変換結果に加えて、カーソル位置の付け直しに使う「入力i文字目までを変換したら
    // 出力が何文字になるか」の対応表(prefixLen)を一緒に返す。
    // 1つの元の文字は変換で消えるか(0文字)残るか(1文字)のどちらかにしかならないため、
    // この対応表さえあれば「元のカーソル位置より前にあった文字のうち、
    // 除去されずに残った文字数」がそのまま新しいカーソル位置になる。
    function transform(el) {
        var hankakuMode = el.getAttribute('data-hankaku');
        var numMode = el.getAttribute('data-numeric');
        if (!hankakuMode && !numMode) return null;

        var mapMode = hankakuMode || (numMode === 'tel' ? 'tel' : 'digits');
        var before = el.value;
        var after = '';
        var prefixLen = new Array(before.length + 1);
        prefixLen[0] = 0;

        for (var i = 0; i < before.length; i++) {
            var c = hankakuChar(before[i], mapMode);
            if (numMode) c = numericFilterChar(c, numMode);
            after += c;
            prefixLen[i + 1] = after.length;
        }

        if (after === before) return null;
        return { after: after, prefixLen: prefixLen };
    }

    // 自分が発火させた input で再入しないようにするための番人。
    // dispatchEvent は同期実行なので、この単純なフラグで足りる。
    var busy = false;

    function normalize(el) {
        if (busy) return;
        if (!el || !el.getAttribute) return;
        if (!el.getAttribute('data-hankaku') && !el.getAttribute('data-numeric')) return;

        var result = transform(el);
        if (result === null) return;

        // カーソル位置を保つ。
        // data-hankaku 単独（全角→半角）の場合は文字数が変わらないため単純に戻せるが、
        // data-numeric で文字が削除されると文字数が変わるため、prefixLen を使って
        // 「元の位置より前で、除去されずに残った文字数」を新しい位置とする。
        var start = null, end = null;
        try { start = el.selectionStart; end = el.selectionEnd; } catch (e) { /* 対応外のtypeは無視 */ }

        el.value = result.after;

        if (start !== null) {
            var prefixLen = result.prefixLen;
            var maxIndex = prefixLen.length - 1;
            var newStart = prefixLen[Math.min(start, maxIndex)];
            var newEnd = prefixLen[Math.min(end, maxIndex)];
            try { el.setSelectionRange(newStart, newEnd); } catch (e) { /* 同上 */ }
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
