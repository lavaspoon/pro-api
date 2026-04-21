package devlava.youproapi.service;

import devlava.youproapi.dto.TargetMemberUploadResponse;
import devlava.youproapi.repository.TbLmsMemberRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TargetMemberUploadService {

    private static final String INSERT_SQL = """
            INSERT INTO "TB_YOU_TARGET"
            ("회사","센터","상담사ID","문서보안ID","성명","그룹","실","스킬","직책","평가대상여부")
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TbLmsMemberRepository memberRepository;

    @Transactional
    public TargetMemberUploadResponse uploadExcel(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 비어 있습니다.");
        }

        List<TargetRow> parsedRows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int skipped = 0;

        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            boolean hasHeader = detectHeader(sheet.getRow(0), fmt);
            int startRow = hasHeader ? 1 : 0;

            for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                try {
                    TargetRow parsed = parseRow(row, fmt);
                    if (parsed == null) {
                        continue;
                    }
                    parsedRows.add(parsed);
                } catch (Exception ex) {
                    skipped++;
                    warnings.add("행 " + (i + 1) + ": " + ex.getMessage());
                }
            }
        }

        try {
            ensureTargetTableExists();
            jdbcTemplate.execute("TRUNCATE TABLE \"TB_YOU_TARGET\"");
            if (!parsedRows.isEmpty()) {
                jdbcTemplate.batchUpdate(
                        INSERT_SQL,
                        parsedRows,
                        500,
                        (ps, row) -> {
                            ps.setString(1, row.company);
                            ps.setString(2, row.center);
                            ps.setString(3, row.skid);
                            ps.setString(4, row.docSecurityId);
                            ps.setString(5, row.name);
                            ps.setString(6, row.groupName);
                            ps.setString(7, row.roomName);
                            ps.setString(8, row.skill);
                            ps.setString(9, row.position);
                            ps.setString(10, row.evalTargetText);
                        }
                );
            }
        } catch (DataAccessException e) {
            String cause = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
            throw new IllegalArgumentException("평가대상자 업로드 DB 처리 실패: " + cause, e);
        }

        Map<String, TargetMemberSync> syncBySkid = new LinkedHashMap<>();
        for (TargetRow row : parsedRows) {
            if (row.skid == null || row.skid.isBlank()) {
                continue;
            }
            String normalizedYouYn = normalizeYouYn(row.evalTargetText);
            syncBySkid.put(row.skid, new TargetMemberSync(row.skid, row.skill, normalizedYouYn));
        }

        int updatedMembers = 0;
        for (TargetMemberSync sync : syncBySkid.values()) {
            updatedMembers += memberRepository.updateYouSkillAndYouYnBySkid(
                    sync.skid(),
                    sync.skill(),
                    sync.youYn()
            );
        }

        return TargetMemberUploadResponse.builder()
                .inserted(parsedRows.size())
                .updatedMembers(updatedMembers)
                .skipped(skipped)
                .warnings(warnings.size() > 50 ? warnings.subList(0, 50) : warnings)
                .build();
    }

    private void ensureTargetTableExists() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS "TB_YOU_TARGET" (
                    "회사" VARCHAR,
                    "센터" VARCHAR,
                    "상담사ID" VARCHAR,
                    "문서보안ID" VARCHAR,
                    "성명" VARCHAR,
                    "그룹" VARCHAR,
                    "실" VARCHAR,
                    "스킬" VARCHAR,
                    "직책" VARCHAR,
                    "평가대상여부" VARCHAR
                )
                """);
    }

    private static String normalizeYouYn(String raw) {
        if (raw == null) {
            return "N";
        }
        return "대상".equals(raw.trim()) ? "Y" : "N";
    }

    private static boolean detectHeader(Row row, DataFormatter fmt) {
        if (row == null) {
            return false;
        }
        String c0 = fmt.formatCellValue(row.getCell(0)).trim();
        String c2 = fmt.formatCellValue(row.getCell(2)).trim();
        String c9 = fmt.formatCellValue(row.getCell(9)).trim();
        return c0.contains("회사")
                || c2.contains("상담사")
                || c9.contains("평가대상");
    }

    private static TargetRow parseRow(Row row, DataFormatter fmt) {
        String company = value(row, 0, fmt);
        String center = value(row, 1, fmt);
        String skid = value(row, 2, fmt);
        String docSecurityId = value(row, 3, fmt);
        String name = value(row, 4, fmt);
        String group = value(row, 5, fmt);
        String room = value(row, 6, fmt);
        String skill = value(row, 7, fmt);
        String position = value(row, 8, fmt);
        String evalTarget = value(row, 9, fmt);

        if (company.isEmpty()
                && center.isEmpty()
                && skid.isEmpty()
                && docSecurityId.isEmpty()
                && name.isEmpty()
                && group.isEmpty()
                && room.isEmpty()
                && skill.isEmpty()
                && position.isEmpty()
                && evalTarget.isEmpty()) {
            return null;
        }
        if (skid.isEmpty()) {
            throw new IllegalArgumentException("상담사ID가 비어 있습니다.");
        }

        return new TargetRow(
                emptyToNull(company),
                emptyToNull(center),
                skid.trim(),
                emptyToNull(docSecurityId),
                emptyToNull(name),
                emptyToNull(group),
                emptyToNull(room),
                emptyToNull(skill),
                emptyToNull(position),
                emptyToNull(evalTarget)
        );
    }

    private static String value(Row row, int idx, DataFormatter fmt) {
        if (row == null) {
            return "";
        }
        return fmt.formatCellValue(row.getCell(idx)).trim();
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private record TargetMemberSync(String skid, String skill, String youYn) {
    }

    private static final class TargetRow {
        private final String company;
        private final String center;
        private final String skid;
        private final String docSecurityId;
        private final String name;
        private final String groupName;
        private final String roomName;
        private final String skill;
        private final String position;
        private final String evalTargetText;

        private TargetRow(
                String company,
                String center,
                String skid,
                String docSecurityId,
                String name,
                String groupName,
                String roomName,
                String skill,
                String position,
                String evalTargetText) {
            this.company = company;
            this.center = center;
            this.skid = skid;
            this.docSecurityId = docSecurityId;
            this.name = name;
            this.groupName = groupName;
            this.roomName = roomName;
            this.skill = skill;
            this.position = position;
            this.evalTargetText = evalTargetText;
        }
    }
}
