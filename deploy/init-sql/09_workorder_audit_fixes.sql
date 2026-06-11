-- Epic 07 (Iteration 1) 审计修复：
--   1) work_order.source 列（AUTO / MANUAL），用于前端视觉区分
--   2) partial unique index 防 hasActiveFault 竞态产生重复活跃工单
--   3) 复合索引 (device_id, fault_type, status) 加速 hasActiveFault 查询
--   4) workorder_assignment.personnel_id 加外键，ON DELETE SET NULL 配合应用层 null 检查
--   5) workorder_assignment 加复合唯一约束防同一技师在同一工单上出现重复活跃指派
--
-- 全部使用 IF NOT EXISTS / DO $$ 块，幂等可重跑。

-- ============================================================
-- 1) work_order.source 列
-- ============================================================
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'AUTO';
CREATE INDEX IF NOT EXISTS ix_work_order_source ON work_order (source);

-- ============================================================
-- 2) hasActiveFault 防重复活跃工单 partial unique index
-- ============================================================
-- 注：PostgreSQL 唯一索引条件必须 IMMUTABLE，使用 NOW()/CURRENT_TIMESTAMP 不行，故用 IN (...) 显式列举
CREATE UNIQUE INDEX IF NOT EXISTS ix_work_order_unique_active_fault
    ON work_order (device_id, fault_type)
    WHERE status IN ('PENDING', 'IN_PROGRESS');

-- ============================================================
-- 3) 复合索引（覆盖一般性按设备+故障查询）
-- ============================================================
CREATE INDEX IF NOT EXISTS ix_work_order_device_fault_status
    ON work_order (device_id, fault_type, status);

-- ============================================================
-- 4) workorder_assignment.personnel_id 外键
-- ============================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_assignment_personnel'
          AND conrelid = 'workorder_assignment'::regclass
    ) THEN
        ALTER TABLE workorder_assignment
            ADD CONSTRAINT fk_assignment_personnel
            FOREIGN KEY (personnel_id) REFERENCES workorder_maintenance_personnel(id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- ============================================================
-- 5) workorder_assignment 防止同技师重复活跃指派（unique partial index）
-- ============================================================
CREATE UNIQUE INDEX IF NOT EXISTS ix_assignment_unique_active
    ON workorder_assignment (work_order_id, personnel_id)
    WHERE released_at IS NULL;