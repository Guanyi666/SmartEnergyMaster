-- ====================================================================
-- 13_data_integrity_fixes.sql
-- ====================================================================
-- 用途: 审计修复批次 1 — 账号-员工 1:1 完整性 + 索引优化
--
-- 修复编号:
--   C7 (CRITICAL): maintenance_personnel.user_id 缺 UNIQUE 约束
--                  → DB 层允许 N 个员工档案绑同一账号
--   Bug-NEW-1 (MEDIUM): maintenance_personnel.user_id 无索引
--                       → 按 user_id JOIN/WHERE 触发全表扫描
--
-- 一次迁移同时解决以上两条 — UNIQUE INDEX 自带 B-tree 索引能力,
-- 既保证唯一性又加速查询。
--
-- 安全提示: 执行前务必 pg_dump 备份! dedup 步骤会将重复的 user_id
--           置为 NULL(失去关联,无法回滚)。
-- ====================================================================

-- ────────────────────────────────────────────────────────────────────
-- Step 1: 清理重复的 user_id
--   按 user_id 分组,保留最早(created_at, id)的一条,其它置 NULL
--   若当前无重复,此 UPDATE 影响 0 行,安全无副作用
-- ────────────────────────────────────────────────────────────────────
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id
               ORDER BY created_at NULLS LAST, id
           ) AS rn
    FROM maintenance_personnel
    WHERE user_id IS NOT NULL
)
UPDATE maintenance_personnel
   SET user_id = NULL,
       updated_at = CURRENT_TIMESTAMP
 WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

-- ────────────────────────────────────────────────────────────────────
-- Step 2: 创建部分唯一索引 (允许多行 user_id = NULL,但非 NULL 必须唯一)
--   同时承担 B-tree 索引职能,加速按 user_id 的查询
-- ────────────────────────────────────────────────────────────────────
CREATE UNIQUE INDEX IF NOT EXISTS ux_maintenance_personnel_user_id
    ON maintenance_personnel(user_id)
 WHERE user_id IS NOT NULL;

-- 列注释更新
COMMENT ON COLUMN maintenance_personnel.user_id IS
    '关联 sys_user.id,逻辑 1:1。允许 NULL 表示尚未关联账号 (DB UNIQUE 部分索引保证非 NULL 时唯一)';

-- ────────────────────────────────────────────────────────────────────
-- 验证查询(可选,运维手动跑)
-- ────────────────────────────────────────────────────────────────────
-- 1. 查看是否还有重复 user_id (应为 0 行)
--   SELECT user_id, COUNT(*) FROM maintenance_personnel
--    WHERE user_id IS NOT NULL GROUP BY user_id HAVING COUNT(*) > 1;
--
-- 2. 验证 UNIQUE INDEX 存在
--   SELECT indexname, indexdef FROM pg_indexes
--    WHERE tablename = 'maintenance_personnel' AND indexname = 'ux_maintenance_personnel_user_id';
--
-- 3. 模拟违反唯一性 (期望: ERROR: duplicate key value violates unique constraint)
--   INSERT INTO maintenance_personnel (user_id, employee_no, name) VALUES (5, 'TEST-X1', 'A');
--   INSERT INTO maintenance_personnel (user_id, employee_no, name) VALUES (5, 'TEST-X2', 'B');
-- ────────────────────────────────────────────────────────────────────
