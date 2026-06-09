-- ============================================================================
-- 清理脚本：清除残留的不一致状态
-- 场景：工单被拖回 PENDING 时，8080 的 work_order.assignee 字段还残留旧指派人
--       而 8081 的 workorder_assignment 已全部 released。两端状态不一致。
-- 运行：docker exec smart_energy_db psql -U energy_user -d smart_energy
--       -f /path/to/cleanup-stale-assignments.sql
-- ============================================================================

BEGIN;

-- 1. 防御性兜底：把任何还"活跃"（released_at IS NULL）的指派标记为释放
UPDATE workorder_assignment
   SET released_at = COALESCE(released_at, CURRENT_TIMESTAMP)
 WHERE released_at IS NULL;

-- 2. 把所有 PENDING 状态工单的 8080 老字段 assignee 清空
UPDATE work_order
   SET assignee = NULL,
       updated_at = CURRENT_TIMESTAMP
 WHERE status = 'PENDING'
   AND assignee IS NOT NULL;

-- 3. 重置所有人员的当前工作负载（按 released 后的实际指派重新计算）
UPDATE workorder_maintenance_personnel p
   SET current_workload = COALESCE((
           SELECT COUNT(*)::int
             FROM workorder_assignment wa
            WHERE wa.personnel_id = p.id
              AND wa.released_at IS NULL
       ), 0),
       updated_at = CURRENT_TIMESTAMP;

-- 4. 输出清理结果
SELECT '--- 清理后工单状态 ---' AS info;
SELECT id, order_no, status, assignee
  FROM work_order
 ORDER BY id;

SELECT '--- 清理后人员负载 ---' AS info;
SELECT id, name, current_workload, max_workload
  FROM workorder_maintenance_personnel
 ORDER BY id;

SELECT '--- 清理后指派记录（最近 10 条）---' AS info;
SELECT work_order_id, personnel_id, role,
       assigned_at, released_at
  FROM workorder_assignment
 ORDER BY id DESC
 LIMIT 10;

COMMIT;
