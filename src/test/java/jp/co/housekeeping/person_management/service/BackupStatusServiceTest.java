package jp.co.housekeeping.person_management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * BackupStatusService の純粋な単体テスト（DBに接続しない）。
 * バックアップが止まっていても画面に気付けるようにするための判定ロジックを固定する。
 */
class BackupStatusServiceTest {

    private final BackupStatusService service = new BackupStatusService("C:/dummy-not-used");

    private Path createBackupFile(Path dir, String fileName, long daysAgo) throws IOException {
        Path file = dir.resolve(fileName);
        Files.writeString(file, "dummy", StandardCharsets.UTF_8);
        Instant modified = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        Files.setLastModifiedTime(file, FileTime.from(modified));
        return file;
    }

    // 1. バックアップファイルが無い → found=false, level=NG
    @Test
    void バックアップファイルが1つも無い場合はfoundFalseでNGになる(@TempDir Path dir) {
        BackupStatusService.BackupStatus status = service.inspect(dir);
        assertFalse(status.isFound());
        assertEquals("NG", status.getLevel());
        assertEquals(0, status.getGenerationCount());
    }

    // 2. 今日の日付のファイルがある → level=OK、generationCountが正しい
    @Test
    void 今日の日付のバックアップがある場合はOKになり世代数が数えられる(@TempDir Path dir) throws IOException {
        createBackupFile(dir, "kaseihu_20260801_100000.backup", 0);
        createBackupFile(dir, "kaseihu_20260731_100000.backup", 1);

        BackupStatusService.BackupStatus status = service.inspect(dir);
        assertTrue(status.isFound());
        assertEquals("OK", status.getLevel());
        assertEquals(2, status.getGenerationCount());
        assertEquals("kaseihu_20260801_100000.backup", status.getLastBackupFileName());
    }

    // 3. 8日前のファイル → level=WARN（7〜10日）
    @Test
    void バックアップが8日前しかない場合はWARNになる(@TempDir Path dir) throws IOException {
        createBackupFile(dir, "kaseihu_20260101_000000.backup", 8);

        BackupStatusService.BackupStatus status = service.inspect(dir);
        assertTrue(status.isFound());
        assertEquals("WARN", status.getLevel());
        assertEquals(8, status.getAgeDays());
    }

    // 4. 15日前のファイル → level=NG（11日以上）
    @Test
    void バックアップが15日前しかない場合はNGになる(@TempDir Path dir) throws IOException {
        createBackupFile(dir, "kaseihu_20260101_000000.backup", 15);

        BackupStatusService.BackupStatus status = service.inspect(dir);
        assertTrue(status.isFound());
        assertEquals("NG", status.getLevel());
        assertEquals(15, status.getAgeDays());
    }

    // 5. last-backup.txt を置いた場合、last_cloud_copy と result が読めること
    @Test
    void 状態ファイルからlastCloudCopyAtとscriptResultが読める(@TempDir Path dir) throws IOException {
        createBackupFile(dir, "kaseihu_20260801_100000.backup", 0);
        Files.writeString(dir.resolve("last-backup.txt"),
            "last_backup=2026-08-01 10:00:00\n"
                + "last_backup_file=C:\\workOfficeTani\\backup\\kaseihu_20260801_100000.backup\n"
                + "last_cloud_copy=2026-08-01 10:00:05\n"
                + "result=OK\n",
            StandardCharsets.UTF_8);

        BackupStatusService.BackupStatus status = service.inspect(dir);
        assertEquals("OK", status.getScriptResult());
        assertEquals(2026, status.getLastCloudCopyAt().getYear());
        assertEquals(8, status.getLastCloudCopyAt().getMonthValue());
        assertEquals(1, status.getLastCloudCopyAt().getDayOfMonth());
    }

    // 6. result=WARN_CLOUD_FAILED のとき、日付が新しくてもlevelがWARNに上がること
    @Test
    void スクリプト結果がWARN_CLOUD_FAILEDなら日付が新しくてもWARNに上がる(@TempDir Path dir) throws IOException {
        createBackupFile(dir, "kaseihu_20260801_100000.backup", 0);
        Files.writeString(dir.resolve("last-backup.txt"),
            "last_backup=2026-08-01 10:00:00\n"
                + "last_cloud_copy=\n"
                + "result=WARN_CLOUD_FAILED\n",
            StandardCharsets.UTF_8);

        BackupStatusService.BackupStatus status = service.inspect(dir);
        assertEquals("WARN", status.getLevel());
        assertNull(status.getLastCloudCopyAt());
        assertTrue(status.getMessage().contains("OneDriveへのコピーに失敗しています"));
    }

    // 7. 存在しないフォルダを渡しても例外を投げずfound=falseになること
    @Test
    void 存在しないフォルダを渡しても例外を投げずfoundがfalseになる(@TempDir Path dir) {
        Path notExist = dir.resolve("does-not-exist-subdir");
        BackupStatusService.BackupStatus status = service.inspect(notExist);
        assertFalse(status.isFound());
        assertEquals("NG", status.getLevel());
        assertEquals(0, status.getAgeDays());
    }
}
