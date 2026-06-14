-- 统一账号格式：入职年份(4位) + 身份标识(2位) + 顺序号(4位)
-- 身份标识：01 ADMIN、02 MANAGER、03 MAINTENANCE_ENGINEER、
--           04 HR_MANAGER、05 OPERATOR、06 DEVICE_MANAGER
BEGIN;

SAVEPOINT before_migration;

ALTER TABLE sys_user DROP CONSTRAINT IF EXISTS ck_sys_user_username_format;

CREATE TEMP TABLE account_username_migration ON COMMIT DROP AS
WITH normalized_users AS (
    SELECT id,
           username AS old_username,
           CASE
               WHEN username IN ('admin', '2026010001') THEN 2026
               ELSE COALESCE(EXTRACT(YEAR FROM created_at)::INTEGER, 2026)
           END AS entry_year,
           CASE role
               WHEN 'ADMIN' THEN '01'
               WHEN 'MANAGER' THEN '02'
               WHEN 'MAINTENANCE_ENGINEER' THEN '03'
               WHEN 'HR_MANAGER' THEN '04'
               WHEN 'OPERATOR' THEN '05'
               WHEN 'DEVICE_MANAGER' THEN '06'
           END AS role_marker,
           role
    FROM sys_user
),
ranked_users AS (
    SELECT id,
           old_username,
           entry_year,
           role_marker,
           ROW_NUMBER() OVER (
               PARTITION BY entry_year, role
               ORDER BY CASE WHEN old_username = 'admin' THEN 0 WHEN old_username = '2026010001' THEN 1 ELSE 2 END, id
           ) AS sequence_no
    FROM normalized_users
)
SELECT id,
       old_username,
       CASE
           WHEN old_username = 'admin' THEN '2026010001'
           ELSE entry_year::TEXT || role_marker || LPAD(sequence_no::TEXT, 4, '0')
       END AS new_username
FROM ranked_users;

UPDATE sys_user u
SET username = '__account_' || u.id
FROM account_username_migration m
WHERE u.id = m.id AND u.username <> m.new_username;

UPDATE sys_user u
SET username = m.new_username,
    updated_at = CURRENT_TIMESTAMP
FROM account_username_migration m
WHERE u.id = m.id AND u.username <> m.new_username;

-- V2 兼容：maintenance_personnel 可能无 employee_no 列
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'maintenance_personnel' AND column_name = 'employee_no') THEN
        UPDATE maintenance_personnel p
        SET employee_no = m.new_username,
            updated_at = CURRENT_TIMESTAMP
        FROM account_username_migration m
        WHERE p.employee_no = m.old_username AND m.old_username <> m.new_username;
    END IF;
END $$;

-- V2 兼容：workorder_maintenance_personnel 可能无 employee_no 列
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'workorder_maintenance_personnel' AND column_name = 'employee_no') THEN
        UPDATE workorder_maintenance_personnel p
        SET employee_no = m.new_username,
            updated_at = CURRENT_TIMESTAMP
        FROM account_username_migration m
        WHERE p.employee_no = m.old_username AND m.old_username <> m.new_username;
    END IF;
END $$;

UPDATE spare_part_usage s
SET user_name = m.new_username
FROM account_username_migration m
WHERE s.user_name = m.old_username AND m.old_username <> m.new_username;

UPDATE maintenance_sop s
SET created_by = m.new_username
FROM account_username_migration m
WHERE s.created_by = m.old_username AND m.old_username <> m.new_username;

UPDATE audit_log a
SET actor_username = m.new_username
FROM account_username_migration m
WHERE a.actor_username = m.old_username AND m.old_username <> m.new_username;

UPDATE work_order_transfer_request r
SET reviewer_username = m.new_username
FROM account_username_migration m
WHERE r.reviewer_username = m.old_username AND m.old_username <> m.new_username;

ALTER TABLE sys_user ADD CONSTRAINT ck_sys_user_username_format CHECK (
    username ~ '^[12][0-9]{9}$'
    AND RIGHT(username, 4) <> '0000'
    AND SUBSTRING(username FROM 5 FOR 2) = CASE role
        WHEN 'ADMIN' THEN '01'
        WHEN 'MANAGER' THEN '02'
        WHEN 'MAINTENANCE_ENGINEER' THEN '03'
        WHEN 'HR_MANAGER' THEN '04'
        WHEN 'OPERATOR' THEN '05'
        WHEN 'DEVICE_MANAGER' THEN '06'
    END
);

COMMIT;
