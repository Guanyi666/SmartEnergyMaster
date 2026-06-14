-- ====================================================================
-- 15_merge_account_fields.sql
-- ====================================================================
-- 需求：账号字段合并 — 删除"工号"字段，统一使用"账号"（username）
--       移除账号状态列，移除设备维修工人列
-- ====================================================================

-- Step 1: maintenance_personnel 表 — 删除 employee_no 唯一约束和字段
-- 注意：必须先删约束再删列，同时清理 user_id=NULL 的孤儿记录（不存在关联账号）
DELETE FROM maintenance_personnel WHERE user_id IS NULL;
ALTER TABLE maintenance_personnel DROP CONSTRAINT IF EXISTS maintenance_personnel_employee_no_key;
ALTER TABLE maintenance_personnel DROP COLUMN IF EXISTS employee_no;

-- Step 2: workorder_maintenance_personnel 表 — 删除 employee_no 唯一约束和字段
DELETE FROM workorder_maintenance_personnel WHERE user_id IS NULL;
ALTER TABLE workorder_maintenance_personnel DROP CONSTRAINT IF EXISTS workorder_maintenance_personnel_employee_no_key;
ALTER TABLE workorder_maintenance_personnel DROP COLUMN IF EXISTS employee_no;

-- Step 3: sys_user 表 — 删除 status 字段及索引
DROP INDEX IF EXISTS ix_sys_user_status;
ALTER TABLE sys_user DROP COLUMN IF EXISTS status;

-- Step 4: device 表 — 删除 maintainer 字段
ALTER TABLE device DROP COLUMN IF EXISTS maintainer;

SELECT 'merge_account_fields.sql 执行完毕' AS status;
