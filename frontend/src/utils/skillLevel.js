// frontend/src/utils/skillLevel.js
// 技能等级 ENUM → 中文标签的映射
// 数据库存英文（JUNIOR/INTERMEDIATE/SENIOR/EXPERT），UI 展示中文
// 好处：DB schema 稳定、扩展 i18n 容易、新增等级只改这里

export const SKILL_LEVEL_META = {
  JUNIOR:       { label: '初级',   tone: 'junior' },
  INTERMEDIATE: { label: '中级',   tone: 'intermediate' },
  SENIOR:       { label: '高级',   tone: 'senior' },
  EXPERT:       { label: '专家',   tone: 'expert' }
}

/**
 * 获取等级的中文标签
 * @param {string} level  ENUM 字符串
 * @returns {string}      中文标签，未知值原样返回
 */
export const skillLevelLabel = (level) =>
  (SKILL_LEVEL_META[level]?.label) || level || '—'

/**
 * 获取等级的 CSS tone class（用于 badge 配色）
 */
export const skillLevelTone = (level) =>
  (SKILL_LEVEL_META[level]?.tone) || (level || 'unknown').toLowerCase()

/**
 * el-select 选项数组（label 中文，value 英文原值，方便后端交互）
 */
export const SKILL_LEVEL_OPTIONS = Object.entries(SKILL_LEVEL_META).map(
  ([value, { label }]) => ({ value, label })
)
