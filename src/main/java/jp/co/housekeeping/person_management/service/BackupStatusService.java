package jp.co.housekeeping.person_management.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * バックアップ状態（最終取得日時・保存世代数・OneDriveコピー状況等）を収集するサービス。
 *
 * これまでバックアップが3週間止まっていても誰も気付けなかった。
 * /backup-guide（1-7-5）画面の先頭に現況を表示し、止まっていることに気付ける
 * ようにするためのデータをここで作る。
 *
 * 前提（backup.ps1 が実行のたびに作るもの。このクラスからは変更しない）
 *   - バックアップ本体: {backupDir}\kaseihu_yyyyMMdd_HHmmss.backup
 *   - 状態ファイル    : {backupDir}\last-backup.txt（UTF-8, key=value 形式）
 *
 * 【最重要】このクラスは絶対に例外を外へ投げない。
 * フォルダが存在しない・読めない・状態ファイルが壊れている等の場合でも
 * found=false / null を返すだけにし、画面自体が開けなくなる事態（他のPCで
 * 動かした時に落ちる）を防ぐ。
 */
@Service
public class BackupStatusService {

    private static final String FILE_PREFIX = "kaseihu_";
    private static final String FILE_SUFFIX = ".backup";
    private static final String STATUS_FILE_NAME = "last-backup.txt";

    private static final DateTimeFormatter STATUS_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 5日に1回の運用が前提。この日数までは正常とみなす */
    private static final long OK_THRESHOLD_DAYS = 6;
    /** これを超えると「止まっている可能性」の異常とみなす（7〜10日はWARN） */
    private static final long WARN_THRESHOLD_DAYS = 10;

    private final Path backupDir;

    public BackupStatusService(@Value("${app.backup.dir:C:/workOfficeTani/backup}") String backupDir) {
        this.backupDir = Paths.get(backupDir);
    }

    /** 画面(1-7-5)から呼ばれる公開メソッド。設定済みのバックアップフォルダを見る。 */
    public BackupStatus getStatus() {
        return inspect(backupDir);
    }

    /**
     * 指定フォルダの状態を調べる。テストから一時フォルダを渡せるようにpackage-privateで公開する。
     * 例外は投げない（存在しない・読めないフォルダでも found=false を返す）。
     */
    BackupStatus inspect(Path dir) {
        BackupStatus status = new BackupStatus();

        try {
            List<Path> backups = listBackupFiles(dir);
            status.generationCount = backups.size();

            if (!backups.isEmpty()) {
                Path latest = backups.get(0);
                status.found = true;
                status.lastBackupFileName = latest.getFileName().toString();

                try {
                    LocalDateTime modified = LocalDateTime.ofInstant(
                        Files.getLastModifiedTime(latest).toInstant(), ZoneId.systemDefault());
                    status.lastBackupAt = modified;
                    status.ageDays = Duration.between(modified, LocalDateTime.now()).toDays();
                } catch (IOException | RuntimeException e) {
                    status.lastBackupAt = null;
                    status.ageDays = 0;
                }

                try {
                    status.sizeKb = Files.size(latest) / 1024;
                } catch (IOException | RuntimeException e) {
                    status.sizeKb = 0;
                }
            }
        } catch (Exception e) {
            // フォルダが読めない等でも画面は開けるようにする
            status.found = false;
        }

        try {
            readStatusFile(dir, status);
        } catch (Exception e) {
            // last-backup.txt が壊れていても本体の判定には影響させない
        }

        applyLevel(status);
        return status;
    }

    /** kaseihu_*.backup を最終更新日時の新しい順に返す。フォルダが無ければ空リスト。 */
    private List<Path> listBackupFiles(Path dir) {
        List<Path> result = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return result;
        }
        try (DirectoryStream<Path> stream =
                 Files.newDirectoryStream(dir, FILE_PREFIX + "*" + FILE_SUFFIX)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    result.add(p);
                }
            }
        } catch (IOException e) {
            return new ArrayList<>();
        }
        result.sort((a, b) -> {
            try {
                return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
            } catch (IOException e) {
                return 0;
            }
        });
        return result;
    }

    /** last-backup.txt から last_cloud_copy / result を読む。無い・壊れていても何もしない。 */
    private void readStatusFile(Path dir, BackupStatus status) throws IOException {
        if (dir == null) {
            return;
        }
        Path statusFile = dir.resolve(STATUS_FILE_NAME);
        if (!Files.isRegularFile(statusFile)) {
            return;
        }
        List<String> lines = Files.readAllLines(statusFile, StandardCharsets.UTF_8);
        for (String line : lines) {
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            switch (key) {
                case "last_cloud_copy":
                    status.lastCloudCopyAt = parseDateTime(value);
                    break;
                case "result":
                    status.scriptResult = value.isEmpty() ? null : value;
                    break;
                default:
                    break;
            }
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, STATUS_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** found / ageDays / scriptResult から level・message を組み立てる（5日に1回の運用が前提）。 */
    private void applyLevel(BackupStatus status) {
        String level;
        String message;

        if (!status.found) {
            level = "NG";
            message = "バックアップが1つも見つかりません。至急ご確認ください。";
        } else if (status.ageDays <= OK_THRESHOLD_DAYS) {
            level = "OK";
            message = "正常に取得できています。";
        } else if (status.ageDays <= WARN_THRESHOLD_DAYS) {
            level = "WARN";
            message = "前回から" + status.ageDays + "日経過しています。自動実行が動いているかご確認ください。";
        } else {
            level = "NG";
            message = "前回から" + status.ageDays + "日経過しています。バックアップが止まっている可能性があります。";
        }

        String scriptResult = status.scriptResult;
        if (scriptResult != null && !scriptResult.isEmpty() && !"OK".equals(scriptResult)) {
            if ("OK".equals(level)) {
                level = "WARN";
            }
            message = message + "（前回の実行結果: " + scriptResult + "）";
            if ("WARN_CLOUD_FAILED".equals(scriptResult)) {
                message = message + "（OneDriveへのコピーに失敗しています）";
            }
        }

        status.level = level;
        status.message = message;
    }

    /** Thymeleafから読むための状態オブジェクト。フィールドは本クラスから直接設定する。 */
    public static class BackupStatus {
        private boolean found;
        private LocalDateTime lastBackupAt;
        private String lastBackupFileName;
        private long sizeKb;
        private int generationCount;
        private long ageDays;
        private String level;
        private String message;
        private LocalDateTime lastCloudCopyAt;
        private String scriptResult;

        public boolean isFound() { return found; }
        public LocalDateTime getLastBackupAt() { return lastBackupAt; }
        public String getLastBackupFileName() { return lastBackupFileName; }
        public long getSizeKb() { return sizeKb; }
        public int getGenerationCount() { return generationCount; }
        public long getAgeDays() { return ageDays; }
        public String getLevel() { return level; }
        public String getMessage() { return message; }
        public LocalDateTime getLastCloudCopyAt() { return lastCloudCopyAt; }
        public String getScriptResult() { return scriptResult; }
    }
}
