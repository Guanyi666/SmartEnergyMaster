package com.smartenergy.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.backend.service.AuditLogService;
import com.smartenergy.backend.vo.AuditLogVO;
import com.smartenergy.backend.vo.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
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
        } catch (Exception e) {
            // ★ NewC4 修复 (2026-06-13): 旧版 catch(Exception ignored){} 完全静默,
            //   导致审计 DB 故障/约束冲突时主业务无感知,事后追责无凭据。
            //   修复后: 仍然不阻断主业务(审计降级), 但必须 log.warn 让运维感知。
            log.warn("[Audit] insert failed, action={}, module={}, target={}:{}, actor={}, error={}",
                    action, module, targetType, targetId, username, e.getMessage());
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
