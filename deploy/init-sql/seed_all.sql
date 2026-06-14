-- ============================================================
--  智驭能效 — 全部种子数据（幂等，可重复执行）
-- 包含: 管理员账号、6台设备、4条SOP、6个案例、12个备件
-- ============================================================

-- ===== 管理员账号 =====
INSERT INTO sys_user (username, password, role)
VALUES ('2026010001', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 'ADMIN')
ON CONFLICT (username) DO NOTHING;

-- ===== 6台设备 =====
INSERT INTO device (device_code, device_name, device_type, status, location, maintainer, description) VALUES
('EAF-01', '1号电弧炉',   'ARC_FURNACE',       'RUNNING', '炼钢一车间', '', '核心高耗能设备——废钢熔化与初步合金化'),
('PUMP-01','循环水泵',    'PUMP',              'STOPPED', '公辅站',   '', '冷却系统关键泵组——向电弧炉及连铸机提供冷却水'),
('COMP-01','空压机A',    'COMPRESSOR',        'STOPPED', '动力站',   '', '压缩空气主设备——气动阀门与仪表风气源'),
('LF-01',  '钢包精炼炉', 'LADLE_FURNACE',     'STOPPED', '炼钢一车间', '', '钢水二次精炼——合金化、脱硫、成分与温度调整'),
('CC-01',  '1号连铸机',  'CONTINUOUS_CASTER', 'STOPPED', '连铸跨',   '', '钢水连续浇铸成坯——弧形连铸机'),
('DC-01',  '主除尘系统', 'DUST_COLLECTOR',    'STOPPED', '环保站',   '', '电弧炉烟气捕集与布袋除尘——环保合规')
ON CONFLICT (device_code) DO NOTHING;

-- ===== 6名维修工程师（sys_user 账号 + 员工档案 + 排班）=====
-- 密码均为 123456（BCrypt）
-- 账号格式：2026(入职年份) + 03(MAINTENANCE_ENGINEER) + 0001~0006(序号)

-- Step 1: 创建 sys_user 登录账号
INSERT INTO sys_user (username, password, role, nickname, phone, department) VALUES
('2026030001', '$2a$10$QiUD0hIi91K2NzBx8YN/R.4KXD3.0H8A3s1mg2x9Ew.atUPOE6S7q', 'MAINTENANCE_ENGINEER', '张工', '13800000001', '设备运维部'),
('2026030002', '$2a$10$QiUD0hIi91K2NzBx8YN/R.4KXD3.0H8A3s1mg2x9Ew.atUPOE6S7q', 'MAINTENANCE_ENGINEER', '李工', '13800000002', '设备运维部'),
('2026030003', '$2a$10$QiUD0hIi91K2NzBx8YN/R.4KXD3.0H8A3s1mg2x9Ew.atUPOE6S7q', 'MAINTENANCE_ENGINEER', '王工', '13800000003', '设备运维部'),
('2026030004', '$2a$10$QiUD0hIi91K2NzBx8YN/R.4KXD3.0H8A3s1mg2x9Ew.atUPOE6S7q', 'MAINTENANCE_ENGINEER', '赵工', '13800000004', '设备运维部'),
('2026030005', '$2a$10$QiUD0hIi91K2NzBx8YN/R.4KXD3.0H8A3s1mg2x9Ew.atUPOE6S7q', 'MAINTENANCE_ENGINEER', '孙工', '13800000005', '设备运维部'),
('2026030006', '$2a$10$QiUD0hIi91K2NzBx8YN/R.4KXD3.0H8A3s1mg2x9Ew.atUPOE6S7q', 'MAINTENANCE_ENGINEER', '周工', '13800000006', '设备运维部')
ON CONFLICT (username) DO NOTHING;

-- Step 2: 创建 maintenance_personnel 员工档案
INSERT INTO maintenance_personnel (user_id, name, phone, email, specializations, skill_level, certification)
SELECT u.id, u.nickname, u.phone, u.email,
       CASE u.username
           WHEN '2026030001' THEN '["电气","自动化"]'::jsonb
           WHEN '2026030002' THEN '["机械","液压"]'::jsonb
           WHEN '2026030003' THEN '["电气","机械","液压"]'::jsonb
           WHEN '2026030004' THEN '["仪表","自动化"]'::jsonb
           WHEN '2026030005' THEN '["机械","焊接"]'::jsonb
           WHEN '2026030006' THEN '["电气","仪表","自动化"]'::jsonb
       END,
       CASE u.username
           WHEN '2026030001' THEN 'EXPERT'
           WHEN '2026030002' THEN 'SENIOR'
           WHEN '2026030003' THEN 'SENIOR'
           WHEN '2026030004' THEN 'INTERMEDIATE'
           WHEN '2026030005' THEN 'INTERMEDIATE'
           WHEN '2026030006' THEN 'JUNIOR'
       END,
       CASE u.username
           WHEN '2026030001' THEN '高级工程师 / 15年'
           WHEN '2026030002' THEN '机械工程师 / 10年'
           WHEN '2026030003' THEN '复合技师 / 8年'
           WHEN '2026030004' THEN '仪表技师 / 5年'
           WHEN '2026030005' THEN '机修工 / 3年'
           WHEN '2026030006' THEN '助理工程师 / 1年'
       END
FROM sys_user u
WHERE u.role = 'MAINTENANCE_ENGINEER'
  AND NOT EXISTS (SELECT 1 FROM maintenance_personnel mp WHERE mp.user_id = u.id);

-- Step 3: 创建 workorder_maintenance_personnel 排班记录
INSERT INTO workorder_maintenance_personnel (user_id, avatar_color, current_workload, max_workload, is_on_duty)
SELECT u.id,
       CASE u.username
           WHEN '2026030001' THEN '#52c8ff'
           WHEN '2026030002' THEN '#ff9f43'
           WHEN '2026030003' THEN '#a78bfa'
           WHEN '2026030004' THEN '#3bff9f'
           WHEN '2026030005' THEN '#ffd24a'
           WHEN '2026030006' THEN '#ff6b9d'
       END,
       0,
       CASE u.username
           WHEN '2026030001' THEN 5
           WHEN '2026030002' THEN 4
           WHEN '2026030003' THEN 4
           WHEN '2026030004' THEN 3
           WHEN '2026030005' THEN 3
           WHEN '2026030006' THEN 2
       END,
       TRUE
FROM sys_user u
WHERE u.role = 'MAINTENANCE_ENGINEER'
  AND NOT EXISTS (SELECT 1 FROM workorder_maintenance_personnel wp WHERE wp.user_id = u.id);

-- ===== 4条SOP标准操作规程 =====
INSERT INTO maintenance_sop (sop_code, device_type, fault_type, title, summary, content, steps, required_skills, required_tools, required_parts, estimated_minutes, created_by) VALUES
('SOP-ARC-001', 'ARC_FURNACE', 'MECHANICAL_JAM', '电弧炉机械卡涩标准处理流程',
 '安全第一：必须先切断电源并确认炉体冷却至安全温度后方可进入检修区域。全程需佩戴高温手套和防护面罩。',
 '电弧炉机械卡涩通常由轴承磨损、润滑不足或联轴器偏移引起。关键检查点包括：电机电流异常检测、轴承温度红外测量、联轴器对中检查、润滑脂状态评估。',
 '["切断电弧炉主电源并挂锁挂牌","使用红外测温枪检测轴承座温度","检查电机三相电流是否平衡","拆卸联轴器护罩，检查弹性元件是否老化","使用百分表检测轴对中度","如轴承损坏，使用拉马拆卸旧轴承并更换","加注高温润滑脂","手动盘车确认转动灵活无卡涩","恢复护罩，解除锁定，试车空转5分钟","记录维修过程和更换部件"]',
 '["机械","电气"]', '["红外测温枪","万用表","百分表","拉马","扭矩扳手","高温润滑脂"]', '["BRG-EAF-001","CPL-EAF-003","LUB-HT-001"]', 120, '2026010001'),

('SOP-ARC-002', 'ARC_FURNACE', 'COOLING_INTERRUPT', '电弧炉冷却中断应急处理流程',
 '紧急：冷却中断可能导致炉体耐火材料损坏甚至漏钢。必须在发现后5分钟内启动应急响应。',
 '冷却中断是最危险的故障模式之一。温度超过1000度且冷却水压力低于50kPa时触发CRITICAL告警。本SOP规定了紧急降温、管路检查和恢复冷却的操作流程。',
 '["立即将电弧炉功率降至60%以下","检查冷却水泵运行状态和出口压力表","检查冷却水管路阀门是否完全开启","使用红外测温枪扫描炉壁寻找热点","检查冷却水进出水温差","如泵组故障，切换至备用泵并启动","如管路泄漏，关闭该段阀门并启动临时旁通","逐步恢复冷却水压力至正常范围","缓慢提升电弧炉功率回到正常工况","记录故障原因和处理过程"]',
 '["液压","机械"]', '["红外测温枪","压力表","管路压力测试仪","备用泵组启动钥匙"]', '["PUMP-EAF-CL01","PIPE-HP-002","VLV-BP-001"]', 45, '2026010001'),

('SOP-PUMP-001', 'PUMP', 'MECHANICAL_JAM', '循环水泵卡涩排查与维修流程',
 '注意：停泵前必须先关闭进出口阀门，防止水锤效应损坏管路。',
 '循环水泵是向电弧炉和连铸机供冷却水的关键设备。泵组卡涩可能由轴承磨损、叶轮堵塞或联轴器故障引起。',
 '["关闭循环水泵电源并锁定","关闭进出口阀门","排空泵体内存水","拆卸泵盖，检查叶轮是否有异物堵塞","使用百分表测量轴跳动","检查机械密封是否泄漏","如轴承损坏，更换同型号轴承","清理叶轮流道并做动平衡","重新组装并加注润滑脂","开启进出口阀门，启动水泵，监测振动和压力"]',
 '["机械","液压"]', '["百分表","扭矩扳手","轴承拉马","动平衡机","机械密封安装工具"]', '["BRG-PMP-001","SEAL-MECH-12","GASKET-EPDM-6"]', 90, '2026010001'),

('SOP-COMP-001', 'COMPRESSOR', 'MECHANICAL_JAM', '空压机卡涩处理流程',
 '注意：压缩空气系统内有余压，拆卸前必须完全泄压。',
 '空压机为全厂气动设备提供压缩空气。卡涩通常由润滑油变质、进气滤芯堵塞或卸载阀故障引起。',
 '["切断空压机电源并锁定","打开泄压阀释放系统余压","检查润滑油油位和油质","拆卸进气滤芯，检查是否堵塞","检查卸载阀动作是否灵活","手动盘车检查转子转动阻力","更换润滑油和滤芯","检查皮带张紧度并调整","关闭泄压阀，启动空压机空载运行3分钟","逐步加载至额定压力，监测温度和振动"]',
 '["机械","电气"]', '["万用表","油品检测仪","滤芯扳手","皮带张紧计"]', '["OIL-COMP-SYN46","FILT-COMP-AIR01","BELT-V-1200"]', 60, '2026010001')
ON CONFLICT (sop_code) DO NOTHING;

-- ===== 6个维修案例 =====
INSERT INTO repair_case (case_code, title, device_type, fault_type, fault_symptom, root_cause, repair_process, repair_result, duration_minutes, technician, keywords, occurred_at) VALUES
('CASE-001', '电弧炉轴承卡涩导致振动超标', 'ARC_FURNACE', 'MECHANICAL_JAM',
 '空转工况下振动值突升至22mm/s，电机电流波动，联轴器处有金属摩擦异响',
 '主轴轴承SKF 23052滚道疲劳剥落，润滑脂高温碳化失效，导致滚动体卡滞',
 '停机后红外测温85度；拆卸联轴器护罩发现弹性体磨损；百分表测轴对中度超差；拉马拆卸旧轴承，滚道有麻点状剥落；更换同型号轴承；更换联轴器弹性体；重新对中至径向偏差合格；加注高温润滑脂；空载试车振动降至2.8mm/s正常',
 '振动恢复至正常范围，电机电流三相平衡，运行3天后复查无异常',
   180, NULL, '电弧炉,轴承卡涩,振动超标,润滑失效,联轴器', '2026-03-15 14:30:00'),

('CASE-002', '电弧炉冷却水压力骤降应急处置', 'ARC_FURNACE', 'COOLING_INTERRUPT',
 '高负荷运行中冷却水压力从120kPa骤降至42kPa，炉膛温度10分钟内升至1080度',
 '冷却水泵机械密封损坏导致泄漏，同时Y型过滤器堵塞造成流量不足',
 '立即降功率至60%减缓温升；切换至备用泵恢复供水压力；关闭故障泵拆卸泵盖；更换机械密封；清洗Y型过滤器滤网；更换老化高压软管；逐步恢复功率，监测稳定',
 '冷却系统恢复正常，压力稳定在118-122kPa，回水温度降至正常范围',
   120, NULL,
 '电弧炉,冷却中断,压力骤降,机械密封损坏,过滤器堵塞', '2026-04-02 09:15:00'),

('CASE-003', '循环水泵异常振动导致冷却水波动', 'PUMP', 'MECHANICAL_JAM',
 '水泵振动值持续上升至4.8mm/s，出口压力波动，泵体发出周期性撞击声',
 '叶轮入口吸入异物（焊渣碎屑）导致动平衡破坏，同时轴承因长期轴向力偏大出现早期疲劳',
 '停泵关闭阀门排空；拆卸泵盖发现叶轮入口卡有焊渣碎屑；取出异物检查叶轮轻微磨损未影响；百分表测轴跳动超差；更换轴承；清理叶轮流道；动平衡校正；更换机械密封；回装试车振动降至1.1mm/s',
 '振动降至正常1.1mm/s，压力稳定，后期在冷却水管路加装Y型过滤器防止异物进入',
   150, NULL,
 '水泵,振动异常,叶轮异物,动平衡破坏,轴承疲劳', '2026-04-18 11:00:00'),

('CASE-004', '空压机高温停机故障', 'COMPRESSOR', 'MECHANICAL_JAM',
 '空压机排气温度持续升高至105度触发高温保护停机，加载时伴有异常噪音',
 '润滑油在高温下氧化变质，粘度下降导致润滑不足。进气滤芯堵塞造成压缩比增大进一步恶化工况',
 '冷却后泄压拆卸进气滤芯已基本堵死；排空旧油油色深黑且有焦糊味；更换合成压缩机油；更换进气滤芯；更换油气分离器滤芯；清洗冷却器翅片改善散热；检查卸载阀正常；空载试车3分钟后加载，排气温度稳定在82度',
 '排气温度恢复正常82度，运行2周后油品复检合格。将润滑油更换周期从6个月调整为4个月',
   120, NULL,
 '空压机,高温停机,润滑油变质,滤芯堵塞', '2026-05-05 16:45:00'),

('CASE-005', '电弧炉联轴器弹性体老化导致频繁卡涩', 'ARC_FURNACE', 'MECHANICAL_JAM',
 '连续3炉次出现短时振动峰值，每次持续约30秒后自行恢复，频率约每40分钟一次',
 '联轴器弹性体在高温环境下老化变硬，失去缓冲能力。弹性体表面龟裂深度达3mm',
 '拆卸联轴器护罩检查弹性体表面龟裂严重；拆卸旧弹性体清理轮毂；更换新弹性体；检查电机和负载端对中合格；螺栓按规定扭矩重新紧固；试车无异常振动',
 '故障消除，后续每2个月定期检查弹性体状态作为预防措施纳入点检表',
   60, NULL,
 '电弧炉,联轴器,弹性体老化,间歇振动,扭矩波动', '2026-05-22 08:00:00'),

('CASE-006', '水泵机械密封泄漏处理', 'PUMP', 'COOLING_INTERRUPT',
 '循环水泵机械密封处有持续滴水（每分钟约15滴），密封处温度偏高52度，但尚未影响供水压力',
 '机械密封动环端面因冷却水硬度偏高产生水垢沉积，导致密封端面无法完全贴合',
 '计划性停泵利用换班间隙；拆卸机械密封组件动环端面有明显水垢；清洗密封腔去除水垢；更换整套机械密封；更换静环O型圈；回装试压无泄漏；建议水处理增加软化设备',
 '密封恢复正常无渗漏，水软化改造纳入长期计划',
   90, NULL,
 '水泵,机械密封泄漏,水垢,冷却水硬度,O型圈老化', '2026-06-01 13:30:00')
ON CONFLICT (case_code) DO NOTHING;

-- ===== 12个备件 =====
INSERT INTO spare_part (part_code, name, spec, unit, quantity, safety_stock, unit_price, supplier, location) VALUES
('BRG-EAF-001', '电弧炉主轴轴承', 'SKF 23052 CC/W33', '套', 4, 2, 8500.00, 'SKF授权经销商', 'A区-轴承库-3号架'),
('CPL-EAF-003', '电弧炉联轴器弹性体', 'ROTEX GS90 聚氨酯', '件', 6, 3, 1200.00, 'KTR中国', 'A区-传动件库-1号架'),
('LUB-HT-001', '高温润滑脂', 'Shell Gadus S5 V100 2, 18kg/桶', '桶', 3, 2, 2800.00, '壳牌润滑油', 'C区-油品库-2号柜'),
('PUMP-EAF-CL01', '冷却水泵机械密封', 'EagleBurgmann MG1/45', '套', 4, 2, 3200.00, '博格曼密封', 'B区-密封件库-4号架'),
('PIPE-HP-002', '高压软管总成', 'DN50x3000mm 316L不锈钢编织', '根', 8, 4, 650.00, '派克汉尼汾', 'B区-管件库-6号架'),
('VLV-BP-001', '旁通球阀', 'DN80 PN16 304不锈钢', '台', 3, 2, 1800.00, '沃茨水工业', 'B区-阀门库-2号架'),
('BRG-PMP-001', '水泵轴承', 'SKF 6312/C3', '套', 6, 3, 450.00, 'SKF授权经销商', 'A区-轴承库-5号架'),
('SEAL-MECH-12', '机械密封组件', 'Crane Type 2, 45mm', '套', 5, 2, 2800.00, '福斯密封', 'B区-密封件库-2号架'),
('GASKET-EPDM-6', 'EPDM密封垫片', 'DN150 3mm厚', '片', 20, 10, 35.00, '本地供应商', 'B区-密封件库-7号架'),
('OIL-COMP-SYN46', '合成压缩机油', 'Shell Corena S4 R 46, 20L/桶', '桶', 2, 2, 2200.00, '壳牌润滑油', 'C区-油品库-1号柜'),
('FILT-COMP-AIR01', '空压机进气滤芯', 'MANN+HUMMEL C 25 125', '件', 8, 4, 380.00, '曼胡默尔', 'C区-滤材库-3号架'),
('BELT-V-1200', '三角皮带', 'SPB 1200 联组带', '条', 6, 3, 120.00, '盖茨皮带', 'A区-传动件库-3号架')
ON CONFLICT (part_code) DO NOTHING;

SELECT 'seed_all.sql 执行完毕 — 全部种子数据已就绪' AS status;
