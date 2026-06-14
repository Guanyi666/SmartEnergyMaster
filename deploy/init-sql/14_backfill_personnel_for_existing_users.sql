-- ====================================================================
-- 14_backfill_personnel_for_existing_users.sql
-- ====================================================================
-- 用途: 修复账号-人员双向不同步 — 回填脚本
--
-- 背景:
--   sys_user 已存在 6 个账号(2026010001 ~ 2026060001),但 syncMaintenanceProfile()
--   只对 MAINTENANCE_ENGINEER 角色同步 personnel 档案,导致其余 5 个角色 (ADMIN/MANAGER/
--   HR_MANAGER/OPERATOR/DEVICE_MANAGER) 的账号在 listUsersWithPersonnel 时档案字段
--   (工号/姓名/电话/邮箱等) 全部为空。
--
--   同时 maintenance_personnel 表里有 5 条 seed 数据 (employee_no E002~E006,
--   user_id=NULL) 是历史孤儿档案,占用 employee_no 空间且无人关联。
--
-- 本脚本:
--   Step 1: 删除 user_id IS NULL 的孤儿档案 + 排班(包括 E002-E006)
--   Step 2: 给每个 sys_user 补一份 workorder_maintenance_personnel (排班)
--   Step 3: 给每个 sys_user 补一份 maintenance_personnel (档案)
--   策略: 非维修角色 max_workload=0 + is_on_duty=false (他们不接工单)
--         维修角色保持原值
-- ====================================================================

-- ────────────────────────────────────────────────────────────────────
-- Step 1: 清理孤儿档案与孤儿排班 (user_id IS NULL)
-- ────────────────────────────────────────────────────────────────────
DELETE FROM workorder_maintenance_personnel WHERE user_id IS NULL;
DELETE FROM maintenance_personnel           WHERE user_id IS NULL;

-- ────────────────────────────────────────────────────────────────────
-- Step 2: 回填排班表 workorder_maintenance_personnel
--   - employee_no 用 sys_user.username (规范化为 2026xxxxxx 格式)
--   - 非维修角色 max_workload=0, is_on_duty=false
--   - 维修角色 max_workload=5, is_on_duty=true
-- ────────────────────────────────────────────────────────────────────
INSERT INTO workorder_maintenance_personnel
    (user_id, avatar_color, current_workload, max_workload, is_on_duty, created_at, updated_at)
SELECT
    u.id,
    CASE
        WHEN u.role = 'MAINTENANCE_ENGINEER' THEN '#52c8ff'
        WHEN u.role = 'MANAGER'              THEN '#ffaa00'
        WHEN u.role = 'ADMIN'                THEN '#ff5d5d'
        WHEN u.role = 'HR_MANAGER'           THEN '#3bff9f'
        WHEN u.role = 'OPERATOR'             THEN '#a78bfa'
        WHEN u.role = 'DEVICE_MANAGER'       THEN '#ffd24a'
        ELSE '#94a3b8'
    END,
    0,                                                                              -- current_workload
    CASE WHEN u.role = 'MAINTENANCE_ENGINEER' THEN 5 ELSE 0 END,                   -- max_workload
    CASE WHEN u.role = 'MAINTENANCE_ENGINEER' THEN TRUE ELSE FALSE END,            -- is_on_duty
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM sys_user u
WHERE NOT EXISTS (
    SELECT 1 FROM workorder_maintenance_personnel p WHERE p.user_id = u.id
);

-- ────────────────────────────────────────────────────────────────────
-- Step 3: 回填档案表 maintenance_personnel
--   - employee_no 同步用 sys_user.username
--   - name 用 sys_user.nickname (没有就用 username)
--   - phone / email 从 sys_user 复制
-- ────────────────────────────────────────────────────────────────────
INSERT INTO maintenance_personnel
    (user_id, name, phone, email, specializations, skill_level, certification, created_at, updated_at)
SELECT
    u.id,
    COALESCE(NULLIF(u.nickname, ''), u.username),
    u.phone,
    u.email,
    CASE WHEN u.role = 'MAINTENANCE_ENGINEER' THEN '["设备维修"]'::jsonb ELSE '[]'::jsonb END,
    CASE WHEN u.role = 'MAINTENANCE_ENGINEER' THEN 'INTERMEDIATE' ELSE 'JUNIOR' END,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM sys_user u
WHERE NOT EXISTS (
    SELECT 1 FROM maintenance_personnel a WHERE a.user_id = u.id
);

-- ────────────────────────────────────────────────────────────────────
-- 验证 (期望: sys_user / archive / schedule 三表行数一致)
-- ────────────────────────────────────────────────────────────────────
-- SELECT
--   (SELECT COUNT(*) FROM sys_user)                     AS sys_user_count,
--   (SELECT COUNT(*) FROM maintenance_personnel)        AS archive_count,
--   (SELECT COUNT(*) FROM workorder_maintenance_personnel) AS schedule_count;
--
-- SELECT u.id, u.username, u.role, a.name, a.phone, s.max_workload, s.is_on_duty
--   FROM sys_user u
--   LEFT JOIN maintenance_personnel a ON a.user_id = u.id
--   LEFT JOIN workorder_maintenance_personnel s ON s.user_id = u.id
--   ORDER BY u.id;
