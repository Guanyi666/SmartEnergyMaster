package com.smartenergy.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.backend.service.AuditLogService;
import com.smartenergy.backend.vo.AuditLogVO;
import com.smartenergy.backend.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void record(String action, String module, String targetType, String targetId, Map<String, Object> detail) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth == null ? "system" : auth.getName();
        try {
            jdbcTemplate.update("""
                    INSERT INTO audit_log(actor_username, action, module, target_type, target_id, detail)
                    VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
                    """, username, action, module, targetType, targetId, objectMapper.writeValueAsString(detail));
        } catch (Exception ignored) {
            // 审计记录失败不能阻断主业务。
        }
    }

    @Override
    public PageVO<AuditLogVO> list(int page, int size, String action, String module,
                                   OffsetDateTime startAt, OffsetDateTime endAt) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        List<Object> args = new ArrayList<>();
        String where = buildWhere(action, module, startAt, endAt, args);

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log " + where, Long.class, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(safeSize);
        queryArgs.add((safePage - 1) * safeSize);
        List<AuditLogVO> records = jdbcTemplate.query("""
                        SELECT id, actor_username, action, module, target_type, target_id,
                               detail::text AS detail, created_at
                        FROM audit_log
                        """ + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    AuditLogVO vo = new AuditLogVO();
                    vo.setId(rs.getLong("id"));
                    vo.setActorUsername(rs.getString("actor_username"));
                    vo.setAction(rs.getString("action"));
                    vo.setModule(rs.getString("module"));
                    vo.setTargetType(rs.getString("target_type"));
                    vo.setTargetId(rs.getString("target_id"));
                    vo.setDetail(rs.getString("detail"));
                    vo.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
                    return vo;
                }, queryArgs.toArray());

        PageVO<AuditLogVO> response = new PageVO<>();
        response.setPage(safePage);
        response.setSize(safeSize);
        response.setTotal(total == null ? 0 : total);
        response.setRecords(records);
        return response;
    }

    private String buildWhere(String action, String module, OffsetDateTime startAt,
                              OffsetDateTime endAt, List<Object> args) {
        List<String> clauses = new ArrayList<>();
        if (StringUtils.hasText(action)) {
            clauses.add("action = ?");
            args.add(action);
        }
        if (StringUtils.hasText(module)) {
            clauses.add("module = ?");
            args.add(module);
        }
        if (startAt != null) {
            clauses.add("created_at >= ?");
            args.add(startAt);
        }
        if (endAt != null) {
            clauses.add("created_at <= ?");
            args.add(endAt);
        }
        return clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses);
    }
}
