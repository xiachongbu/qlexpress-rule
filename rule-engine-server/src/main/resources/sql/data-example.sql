-- ============================================================
-- 示例数据初始化脚本（actionData JSON 架构）
-- 为 rule-engine-example 客户端示例创建完整的服务端数据（信贷 / 交易风控演示域）
-- 包含：1个项目 + 30个变量 + 函数管理(SCRIPT/JAVA/BEAN) + 数据对象 + 11条规则（含对象传入、JAVA/BEAN 函数、复杂交叉表 / 复杂评分卡 / QL 脚本等多模型示例）
-- 交叉表 RC_RATE_MATRIX、复杂交叉表 RC_MULTI_DIM_RATE 的 model_json 与构建器界面示例对齐：客商类型×产品总线→风险定价费率（2×4）；业务类型×结算方式 × 客户类型×纳税人资格（8×6，ICT 后付一般纳税人 0.13）
-- compiled_script 与当前 rule-engine-core 编译器一致：多输出时末尾为 `_result = { "varCode": var, ... }` + `_result`（QLExpress JSON 对象字面量）；执行日志 outputResult 为 JSON 对象时可多键展示。
--
-- 执行方式：mysql -u root -p rule_engine < data-example.sql
-- 前提条件：已执行 schema.sql 创建数据库和表结构
-- ============================================================

USE `rule_engine`;

-- ============================================================
-- 1. 创建示例项目
-- ============================================================
INSERT INTO `rule_project` (`id`, `project_code`, `project_name`, `description`, `status`, `access_token`)
VALUES (1, 'RISK_DEMO', '综合风控示例项目', '演示决策表/树/流/交叉表/评分卡及扩展模型在授信准入、定价与交易监控中的典型用法', 1, 'demo-token-change-me')
ON DUPLICATE KEY UPDATE `project_name` = VALUES(`project_name`);

-- ============================================================
-- 2. 创建项目变量
-- ============================================================
INSERT INTO `rule_variable` (`id`, `project_id`, `var_code`, `var_label`, `script_name`, `var_type`, `var_source`, `default_value`, `example_value`, `description`, `sort_order`, `status`) VALUES
(1,  1, 'taxpayerType',       '客商类型',   'taxpayerType',       'STRING',  'INPUT',    NULL,     '一般纳税人',  '示例取值：一般纳税人/小规模纳税人（与设计器「行维度」一致）',               1, 1),
(2,  1, 'goodsCategory',      '产品总线',     'goodsCategory',      'STRING',  'INPUT',    NULL,     '货物',        '示例：货物/服务/不动产/无形资产（与设计器「列维度」一致）',          2, 1),
(3,  1, 'taxRate',             '风险定价费率',     'taxRate',             'NUMBER',  'COMPUTED', NULL,     '0.13',        '规则输出的定价/费率系数',                      3, 1),
(4,  1, 'annualRevenue',      '年营收(万元)',  'annualRevenue',      'NUMBER',  'INPUT',    NULL,     '5000',        '客户或主体年营业收入，单位万元',                 4, 1),
(5,  1, 'taxComplianceScore', '合规内控评分',  'taxComplianceScore', 'NUMBER',  'INPUT',    NULL,     '85',          '0-100 分',                               5, 1),
(6,  1, 'yearsInBusiness',    '合作/经营年限',      'yearsInBusiness',    'NUMBER',  'INPUT',    NULL,     '10',          '经营或与机构合作年限',                            6, 1),
(7,  1, 'hasViolation',       '是否存在严重违规', 'hasViolation',       'BOOLEAN', 'INPUT',    'false',  'false',       'true/false',                             7, 1),
(8,  1, 'creditLevel',        '内部信用等级',      'creditLevel',        'STRING',  'COMPUTED', NULL,     'A',           '决策树输出：A/B/C/D',                     8, 1),
(9,  1, 'totalAmount',        '交易金额(含税口径)',      'totalAmount',        'NUMBER',  'INPUT',    NULL,     '113000',      '用于试算或限额校验的金额示例',                             9, 1),
(10, 1, 'isExempt',           '是否适用减免政策',   'isExempt',           'BOOLEAN', 'INPUT',    'false',  'false',       '是否命中费用减免或优惠策略',                    10, 1),
(11, 1, 'taxBurdenDeviation', '指标偏离度',     'taxBurdenDeviation', 'NUMBER',  'INPUT',    NULL,     '0.15',        '与行业或客群基准的偏离比例，如 0.15 表示 15%',       11, 1),
(12, 1, 'violationCount',     '历史风险事件次数',   'violationCount',     'NUMBER',  'INPUT',    NULL,     '0',           '历史负面事件计数',                           12, 1),
(13, 1, 'totalScore',         '风险总评分',     'totalScore',         'NUMBER',  'COMPUTED', NULL,     '85',          '评分卡计算输出的总分',                    13, 1),
(14, 1, 'riskLevel',          '风险等级',       'riskLevel',          'STRING',  'COMPUTED', NULL,     '低风险',      '评分卡输出：低风险/中风险/高风险',          14, 1)
ON DUPLICATE KEY UPDATE `var_label` = VALUES(`var_label`);

-- 为客商类型添加枚举选项（入参取值与规则脚本一致）
INSERT INTO `rule_variable_option` (`variable_id`, `option_value`, `option_label`, `sort_order`) VALUES
(1, '一般纳税人',   '一般纳税人',   1),
(1, '小规模纳税人', '小规模纳税人', 2)
ON DUPLICATE KEY UPDATE `option_label` = VALUES(`option_label`);

-- 为产品总线添加枚举选项
INSERT INTO `rule_variable_option` (`variable_id`, `option_value`, `option_label`, `sort_order`) VALUES
(2, '货物',     '货物',     1),
(2, '服务',     '服务',     2),
(2, '不动产',   '不动产',   3),
(2, '无形资产', '无形资产', 4)
ON DUPLICATE KEY UPDATE `option_label` = VALUES(`option_label`);

-- ============================================================
-- 2.1 自定义函数定义（函数管理 —— 支持 SCRIPT / JAVA / BEAN 三种类型）
-- ============================================================
INSERT INTO `rule_function` (`id`, `project_id`, `func_code`, `func_name`, `description`, `params_json`, `return_type`, `impl_type`, `impl_script`, `impl_class`, `impl_method`, `impl_bean_name`, `status`) VALUES
-- SCRIPT 类型：QLExpress 脚本实现，执行时自动包装为 function 定义拼接在编译脚本前面
(1, 1, 'roundTax', '金额四舍五入', '将数值保留2位小数（费用/敞口试算）', '[{"name":"amount","type":"NUMBER","label":"金额"}]', 'NUMBER', 'SCRIPT', 'return new java.math.BigDecimal(String.valueOf(amount)).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();', NULL, NULL, NULL, 1),
(2, 1, 'formatAmount', '金额格式化', '格式化为带千分位的字符串', '[{"name":"amount","type":"NUMBER","label":"金额"}]', 'STRING', 'SCRIPT', 'return new java.text.DecimalFormat("#,##0.00").format(amount);', NULL, NULL, NULL, 1),
-- JAVA 类型：通过反射实例化 Java 类，调用指定方法注册为 QL 函数
(3, 1, 'calculateVAT', '费用试算(Java)', '演示 Java 反射函数：按金额与费率计算费用', '[{"name":"amount","type":"NUMBER","label":"金额"},{"name":"rate","type":"NUMBER","label":"费率"}]', 'NUMBER', 'JAVA', NULL, 'com.bjjw.rule.example.functions.TaxFunctions', 'calculateVAT', NULL, 1),
-- BEAN 类型：从 Spring 容器获取 Bean，调用指定方法注册为 QL 函数
(4, 1, 'calcTaxByBean', '费用试算(Bean)', '演示 Spring Bean 函数，复用已有定价服务', '[{"name":"amount","type":"NUMBER","label":"金额"},{"name":"rate","type":"NUMBER","label":"费率"}]', 'NUMBER', 'BEAN', NULL, NULL, 'calculateVAT', 'taxFunctions', 1)
ON DUPLICATE KEY UPDATE `func_name` = VALUES(`func_name`), `impl_type` = VALUES(`impl_type`), `impl_script` = VALUES(`impl_script`), `impl_class` = VALUES(`impl_class`), `impl_method` = VALUES(`impl_method`), `impl_bean_name` = VALUES(`impl_bean_name`);

-- ============================================================
-- 2.2 数据对象定义（对象传入）
-- ============================================================
INSERT INTO `rule_data_object` (`id`, `project_id`, `object_code`, `object_label`, `script_name`, `object_type`, `source_type`, `source_content`, `status`) VALUES
(1, 1, 'TaxContext', '风控上下文对象', 'taxContext', 'INPUT', 'JSON', '{"taxpayerType":"STRING","goodsCategory":"STRING","totalAmount":"NUMBER","isExempt":"BOOLEAN"}', 1)
ON DUPLICATE KEY UPDATE `object_label` = VALUES(`object_label`);

-- ============================================================
-- 3. 创建八条规则定义（已发布状态，含对象传入和函数调用示例）
-- ============================================================
INSERT INTO `rule_definition` (`id`, `project_id`, `rule_code`, `rule_name`, `model_type`, `description`, `current_version`, `published_version`, `status`) VALUES
(1, 1, 'RC_PRICING_TABLE',        '客商×产品总线定价表',     'TABLE', '根据客商类型与产品总线匹配风险定价费率（决策表示例）',                              1, 1, 1),
(2, 1, 'RC_CREDIT_TREE', '客户信用分层', 'TREE',  '结合负面记录、合规评分、营收规模、合作年限输出内部信用等级 A/B/C/D',          1, 1, 1),
(3, 1, 'RC_EXPOSURE_FLOW',        '敞口与费用试算流程',     'FLOW',  '串行试算：定价费率 → 本金/敞口拆分 → 费用 → 减免策略 → 应收费用', 1, 1, 1),
(4, 1, 'RC_RATE_MATRIX',     '风险定价交叉表',       'CROSS', '客商类型 × 产品总线 交叉矩阵输出风险定价费率（与交叉表设计器示例一致）',                         1, 1, 1),
(5, 1, 'RC_RISK_SCORECARD',      '综合风险评分卡',     'SCORE', '基于信用等级、营收、年限、指标偏离、历史事件的加权评分与风险档',            1, 1, 1),
(6, 1, 'RC_PRICING_BY_OBJECT',      '对象传入定价', 'TABLE', '演示对象上下文入参：规则读取 taxpayerType、goodsCategory 等字段', 1, 1, 1),
(7, 1, 'RC_FLOW_JAVA_SAMPLE',  'JAVA 函数试算',     'FLOW',  '演示 JAVA 函数：反射调用 TaxFunctions.calculateVAT 完成费用试算',      1, 1, 1),
(8, 1, 'RC_FLOW_BEAN_SAMPLE',  'Spring Bean 试算',     'FLOW',  '演示 BEAN 函数：调用 Spring 容器内 taxFunctions.calculateVAT', 1, 1, 1)
ON DUPLICATE KEY UPDATE `rule_name` = VALUES(`rule_name`), `description` = VALUES(`description`), `status` = 1, `published_version` = 1;

-- ============================================================
-- 4. 规则内容（设计态 JSON + 编译产物）
-- ============================================================

-- ---------- 4.1 决策表：RC_PRICING_TABLE ----------
INSERT INTO `rule_definition_content` (`id`, `definition_id`, `model_json`, `compiled_script`, `compiled_type`, `compile_status`, `compile_time`) VALUES
(1, 1,
 '{"hitPolicy":"FIRST","conditions":[{"varCode":"taxpayerType","varLabel":"客商类型","varType":"STRING"},{"varCode":"goodsCategory","varLabel":"产品总线","varType":"STRING"}],"actions":[{"varCode":"taxRate","varLabel":"风险定价费率","varType":"NUMBER"}],"rules":[{"conditions":[{"operator":"==","value":"一般纳税人"},{"operator":"==","value":"货物"}],"actions":[{"value":"0.13"}]},{"conditions":[{"operator":"==","value":"一般纳税人"},{"operator":"==","value":"服务"}],"actions":[{"value":"0.06"}]},{"conditions":[{"operator":"==","value":"一般纳税人"},{"operator":"==","value":"不动产"}],"actions":[{"value":"0.09"}]},{"conditions":[{"operator":"==","value":"一般纳税人"},{"operator":"==","value":"无形资产"}],"actions":[{"value":"0.06"}]},{"conditions":[{"operator":"==","value":"小规模纳税人"},{"operator":"==","value":"货物"}],"actions":[{"value":"0.03"}]},{"conditions":[{"operator":"==","value":"小规模纳税人"},{"operator":"==","value":"服务"}],"actions":[{"value":"0.03"}]},{"conditions":[{"operator":"==","value":"小规模纳税人"},{"operator":"==","value":"不动产"}],"actions":[{"value":"0.05"}]},{"conditions":[{"operator":"==","value":"小规模纳税人"},{"operator":"==","value":"无形资产"}],"actions":[{"value":"0.03"}]}]}',
 'taxRate = null\nif (taxpayerType == "一般纳税人" && goodsCategory == "货物") {\n    taxRate = 0.13;\n} else if (taxpayerType == "一般纳税人" && goodsCategory == "服务") {\n    taxRate = 0.06;\n} else if (taxpayerType == "一般纳税人" && goodsCategory == "不动产") {\n    taxRate = 0.09;\n} else if (taxpayerType == "一般纳税人" && goodsCategory == "无形资产") {\n    taxRate = 0.06;\n} else if (taxpayerType == "小规模纳税人" && goodsCategory == "货物") {\n    taxRate = 0.03;\n} else if (taxpayerType == "小规模纳税人" && goodsCategory == "服务") {\n    taxRate = 0.03;\n} else if (taxpayerType == "小规模纳税人" && goodsCategory == "不动产") {\n    taxRate = 0.05;\n} else if (taxpayerType == "小规模纳税人" && goodsCategory == "无形资产") {\n    taxRate = 0.03;\n}\n\n_result = {"taxRate": taxRate}\n_result\n',
 'QLEXPRESS', 1, NOW())
ON DUPLICATE KEY UPDATE `model_json` = VALUES(`model_json`), `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `compile_status` = 1;

-- ---------- 4.2 决策树：RC_CREDIT_TREE ----------
-- 节点使用 actionData JSON 格式存储动作；defaultEdgeLineType 供设计器全局连线样式（折线/直线/弧线）
INSERT INTO `rule_definition_content` (`id`, `definition_id`, `model_json`, `compiled_script`, `compiled_type`, `compile_status`, `compile_time`) VALUES
(2, 2,
 '{"defaultEdgeLineType":"polyline","nodes":[{"id":"n1","type":"start","name":"开始","x":100,"y":200},{"id":"n2","type":"decision","name":"是否有违规记录","x":300,"y":200},{"id":"n3","type":"task","name":"信用等级D","actionData":[{"type":"assign","target":"creditLevel","value":"\\"D\\""}],"x":500,"y":50},{"id":"n4","type":"decision","name":"合规评分判断","x":500,"y":300},{"id":"n5","type":"task","name":"信用等级A","actionData":[{"type":"assign","target":"creditLevel","value":"\\"A\\""}],"x":700,"y":150},{"id":"n6","type":"task","name":"信用等级B","actionData":[{"type":"assign","target":"creditLevel","value":"\\"B\\""}],"x":700,"y":300},{"id":"n7","type":"task","name":"信用等级C","actionData":[{"type":"assign","target":"creditLevel","value":"\\"C\\""}],"x":700,"y":450}],"edges":[{"id":"e1","source":"n1","target":"n2"},{"id":"e2","source":"n2","target":"n3","conditionExpression":"hasViolation == true","name":"有违规"},{"id":"e3","source":"n2","target":"n4","name":"无违规"},{"id":"e4","source":"n4","target":"n5","conditionExpression":"taxComplianceScore >= 80 && annualRevenue >= 3000 && yearsInBusiness >= 5","name":"A级"},{"id":"e5","source":"n4","target":"n6","conditionExpression":"taxComplianceScore >= 60","name":"B级"},{"id":"e6","source":"n4","target":"n7","name":"C级"}]}',
 'creditLevel = null\nif (hasViolation == true) {\n    // 信用等级D\n    creditLevel = "D"\n\n} else {\n    if (taxComplianceScore >= 80 && annualRevenue >= 3000 && yearsInBusiness >= 5) {\n        // 信用等级A\n        creditLevel = "A"\n\n    } else if (taxComplianceScore >= 60) {\n        // 信用等级B\n        creditLevel = "B"\n\n    } else {\n        // 信用等级C\n        creditLevel = "C"\n\n    }\n\n}\n\n_result = {"creditLevel": creditLevel}\n_result\n',
 'QLEXPRESS', 1, NOW())
ON DUPLICATE KEY UPDATE `model_json` = VALUES(`model_json`), `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `compile_status` = 1;

-- ---------- 4.3 决策流：RC_EXPOSURE_FLOW ----------
-- 决策流编译为 QLEXPRESS 脚本，节点使用 actionData 格式
-- "计算税额"节点调用了自定义函数 roundTax 对税额四舍五入，演示函数管理与决策流的联动
INSERT INTO `rule_definition_content` (`id`, `definition_id`, `model_json`, `compiled_script`, `compiled_type`, `compile_status`, `compile_time`) VALUES
(3, 3,
 '{"defaultEdgeLineType":"polyline","nodes":[{"id":"n1","type":"start","name":"开始","x":80,"y":200},{"id":"n2","type":"decision","name":"判断纳税人类型","x":250,"y":200},{"id":"n3","type":"task","name":"一般纳税人税率","actionData":[{"type":"if-block","branches":[{"type":"if","condVar":"goodsCategory","condOp":"==","condValue":"货物","actions":[{"type":"assign","target":"taxRate","value":"0.13"}]},{"type":"elseif","condVar":"goodsCategory","condOp":"==","condValue":"服务","actions":[{"type":"assign","target":"taxRate","value":"0.06"}]},{"type":"elseif","condVar":"goodsCategory","condOp":"==","condValue":"不动产","actions":[{"type":"assign","target":"taxRate","value":"0.09"}]},{"type":"else","actions":[{"type":"assign","target":"taxRate","value":"0.06"}]}]}],"x":450,"y":100},{"id":"n4","type":"task","name":"小规模纳税人税率","actionData":[{"type":"if-block","branches":[{"type":"if","condVar":"goodsCategory","condOp":"==","condValue":"不动产","actions":[{"type":"assign","target":"taxRate","value":"0.05"}]},{"type":"else","actions":[{"type":"assign","target":"taxRate","value":"0.03"}]}]}],"x":450,"y":300},{"id":"n5","type":"join","name":"汇合","x":650,"y":200},{"id":"n6","type":"task","name":"计算税额","actionData":[{"type":"assign","target":"excludingTaxAmount","value":"totalAmount / (1 + taxRate)"},{"type":"assign","target":"taxAmount","value":"excludingTaxAmount * taxRate"},{"type":"func-call","target":"taxAmount","funcName":"roundTax","args":["taxAmount"]}],"x":800,"y":200},{"id":"n7","type":"task","name":"减免计算","actionData":[{"type":"if-block","branches":[{"type":"if","condVar":"isExempt","condOp":"==","condValue":"true","actions":[{"type":"assign","target":"exemptAmount","value":"taxAmount * 0.5"}]},{"type":"else","actions":[{"type":"assign","target":"exemptAmount","value":"0"}]}]},{"type":"assign","target":"finalTaxAmount","value":"taxAmount - exemptAmount"}],"x":1000,"y":200},{"id":"n8","type":"end","name":"结束","x":1200,"y":200}],"edges":[{"id":"e1","source":"n1","target":"n2"},{"id":"e2","source":"n2","target":"n3","conditionExpression":"taxpayerType == \\"一般纳税人\\"","name":"一般纳税人"},{"id":"e3","source":"n2","target":"n4","name":"小规模纳税人"},{"id":"e4","source":"n3","target":"n5"},{"id":"e5","source":"n4","target":"n5"},{"id":"e6","source":"n5","target":"n6"},{"id":"e7","source":"n6","target":"n7"},{"id":"e8","source":"n7","target":"n8"}]}',
 'taxRate = null\nexcludingTaxAmount = null\ntaxAmount = null\nexemptAmount = null\nfinalTaxAmount = null\nif (taxpayerType == \"一般纳税人\") {\n    // 一般纳税人税率\n    if (goodsCategory == \"货物\") {\n        taxRate = 0.13\n    } else if (goodsCategory == \"服务\") {\n        taxRate = 0.06\n    } else if (goodsCategory == \"不动产\") {\n        taxRate = 0.09\n    } else {\n        taxRate = 0.06\n    }\n\n} else {\n    // 小规模纳税人税率\n    if (goodsCategory == \"不动产\") {\n        taxRate = 0.05\n    } else {\n        taxRate = 0.03\n    }\n\n}\n\n// 计算税额\nexcludingTaxAmount = totalAmount / (1 + taxRate)\ntaxAmount = excludingTaxAmount * taxRate\ntaxAmount = roundTax(taxAmount)\n\n// 减免计算\nif (isExempt == true) {\n    exemptAmount = taxAmount * 0.5\n} else {\n    exemptAmount = 0\n}\nfinalTaxAmount = taxAmount - exemptAmount\n\n_result = {"taxRate": taxRate, "excludingTaxAmount": excludingTaxAmount, "taxAmount": taxAmount, "exemptAmount": exemptAmount, "finalTaxAmount": finalTaxAmount}\n_result',
 'QLEXPRESS', 1, NOW())
ON DUPLICATE KEY UPDATE `model_json` = VALUES(`model_json`), `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `compile_status` = 1;

-- ---------- 4.4 交叉表：RC_RATE_MATRIX（与交叉表设计器：行=客商类型，列=产品总线，结果=风险定价费率）----------
INSERT INTO `rule_definition_content` (`id`, `definition_id`, `model_json`, `compiled_script`, `compiled_type`, `compile_status`, `compile_time`) VALUES
(4, 4,
 '{"rowVar":{"varCode":"taxpayerType","varLabel":"客商类型","varType":"STRING"},"colVar":{"varCode":"goodsCategory","varLabel":"产品总线","varType":"STRING"},"resultVar":{"varCode":"taxRate","varLabel":"风险定价费率","varType":"NUMBER"},"rowHeaders":["一般纳税人","小规模纳税人"],"colHeaders":["货物","服务","不动产","无形资产"],"cells":[["0.13","0.06","0.09","0.06"],["0.03","0.03","0.05","0.03"]]}',
 'if (taxpayerType == "一般纳税人" && goodsCategory == "货物") {\n    taxRate = 0.13\n} else if (taxpayerType == "一般纳税人" && goodsCategory == "服务") {\n    taxRate = 0.06\n} else if (taxpayerType == "一般纳税人" && goodsCategory == "不动产") {\n    taxRate = 0.09\n} else if (taxpayerType == "一般纳税人" && goodsCategory == "无形资产") {\n    taxRate = 0.06\n} else if (taxpayerType == "小规模纳税人" && goodsCategory == "货物") {\n    taxRate = 0.03\n} else if (taxpayerType == "小规模纳税人" && goodsCategory == "服务") {\n    taxRate = 0.03\n} else if (taxpayerType == "小规模纳税人" && goodsCategory == "不动产") {\n    taxRate = 0.05\n} else if (taxpayerType == "小规模纳税人" && goodsCategory == "无形资产") {\n    taxRate = 0.03\n}\n',
 'QLEXPRESS', 1, NOW())
ON DUPLICATE KEY UPDATE `model_json` = VALUES(`model_json`), `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `compile_status` = 1;

-- ---------- 4.5 评分卡：RC_RISK_SCORECARD ----------
INSERT INTO `rule_definition_content` (`id`, `definition_id`, `model_json`, `compiled_script`, `compiled_type`, `compile_status`, `compile_time`) VALUES
(5, 5,
 '{"initialScore":100,"resultVar":{"varCode":"totalScore","varLabel":"风险总评分","varType":"NUMBER"},"scoreItems":[{"conditionLabel":"信用等级A","condition":"creditLevel == \\"A\\"","score":30,"weight":1.0},{"conditionLabel":"信用等级B","condition":"creditLevel == \\"B\\"","score":20,"weight":1.0},{"conditionLabel":"信用等级C","condition":"creditLevel == \\"C\\"","score":10,"weight":1.0},{"conditionLabel":"信用等级D","condition":"creditLevel == \\"D\\"","score":-20,"weight":1.0},{"conditionLabel":"年营收>=5000万","condition":"annualRevenue >= 5000","score":15,"weight":1.0},{"conditionLabel":"年营收>=1000万","condition":"annualRevenue >= 1000 && annualRevenue < 5000","score":10,"weight":1.0},{"conditionLabel":"经营>=10年","condition":"yearsInBusiness >= 10","score":10,"weight":1.0},{"conditionLabel":"经营>=5年","condition":"yearsInBusiness >= 5 && yearsInBusiness < 10","score":5,"weight":1.0},{"conditionLabel":"税负偏离<=10%","condition":"taxBurdenDeviation <= 0.10","score":15,"weight":1.0},{"conditionLabel":"税负偏离>20%","condition":"taxBurdenDeviation > 0.20","score":-15,"weight":1.0},{"conditionLabel":"无违规记录","condition":"violationCount == 0","score":10,"weight":1.0},{"conditionLabel":"有违规>=3次","condition":"violationCount >= 3","score":-20,"weight":1.0}],"thresholds":[{"min":80,"max":999,"result":"低风险"},{"min":60,"max":80,"result":"中风险"},{"min":0,"max":60,"result":"高风险"}]}',
 'totalScore = 100.0\n\nif (creditLevel == "A") {\n    totalScore = totalScore + 30.0\n}\n\nif (creditLevel == "B") {\n    totalScore = totalScore + 20.0\n}\n\nif (creditLevel == "C") {\n    totalScore = totalScore + 10.0\n}\n\nif (creditLevel == "D") {\n    totalScore = totalScore + -20.0\n}\n\nif (annualRevenue >= 5000) {\n    totalScore = totalScore + 15.0\n}\n\nif (annualRevenue >= 1000 && annualRevenue < 5000) {\n    totalScore = totalScore + 10.0\n}\n\nif (yearsInBusiness >= 10) {\n    totalScore = totalScore + 10.0\n}\n\nif (yearsInBusiness >= 5 && yearsInBusiness < 10) {\n    totalScore = totalScore + 5.0\n}\n\nif (taxBurdenDeviation <= 0.10) {\n    totalScore = totalScore + 15.0\n}\n\nif (taxBurdenDeviation > 0.20) {\n    totalScore = totalScore + -15.0\n}\n\nif (violationCount == 0) {\n    totalScore = totalScore + 10.0\n}\n\nif (violationCount >= 3) {\n    totalScore = totalScore + -20.0\n}\n\nriskLevel = "未知"\nif (totalScore >= 80.0 && totalScore < 999.0) {\n    riskLevel = "低风险"\n} else if (totalScore >= 60.0 && totalScore < 80.0) {\n    riskLevel = "中风险"\n} else if (totalScore >= 0.0 && totalScore < 60.0) {\n    riskLevel = "高风险"\n}\n\n_result = {"totalScore": totalScore, "riskLevel": riskLevel}\n_result\n',
 'QLEXPRESS', 1, NOW())
ON DUPLICATE KEY UPDATE `model_json` = VALUES(`model_json`), `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `compile_status` = 1;

-- ---------- 4.6 对象传入示例：RC_PRICING_BY_OBJECT ----------
-- 参数为 ctx 对象，规则访问 taxpayerType、goodsCategory
INSERT INTO `rule_definition_content` (`id`, `definition_id`, `model_json`, `compiled_script`, `compiled_type`, `compile_status`, `compile_time`) VALUES
(6, 6,
 '{"hitPolicy":"FIRST","conditions":[{"varCode":"taxpayerType","varLabel":"客商类型","varType":"STRING"},{"varCode":"goodsCategory","varLabel":"产品总线","varType":"STRING"}],"actions":[{"varCode":"taxRate","varLabel":"风险定价费率","varType":"NUMBER"}],"rules":[{"conditions":[{"operator":"==","value":"一般纳税人"},{"operator":"==","value":"货物"}],"actions":[{"value":"0.13"}]},{"conditions":[{"operator":"==","value":"一般纳税人"},{"operator":"==","value":"服务"}],"actions":[{"value":"0.06"}]},{"conditions":[{"operator":"==","value":"一般纳税人"},{"operator":"==","value":"不动产"}],"actions":[{"value":"0.09"}]},{"conditions":[{"operator":"==","value":"一般纳税人"},{"operator":"==","value":"无形资产"}],"actions":[{"value":"0.06"}]},{"conditions":[{"operator":"==","value":"小规模纳税人"},{"operator":"==","value":"货物"}],"actions":[{"value":"0.03"}]},{"conditions":[{"operator":"==","value":"小规模纳税人"},{"operator":"==","value":"服务"}],"actions":[{"value":"0.03"}]},{"conditions":[{"operator":"==","value":"小规模纳税人"},{"operator":"==","value":"不动产"}],"actions":[{"value":"0.05"}]},{"conditions":[{"operator":"==","value":"小规模纳税人"},{"operator":"==","value":"无形资产"}],"actions":[{"value":"0.03"}]}]}',
 'taxRate = null\nif (taxpayerType == "一般纳税人" && goodsCategory == "货物") {\n    taxRate = 0.13;\n} else if (taxpayerType == "一般纳税人" && goodsCategory == "服务") {\n    taxRate = 0.06;\n} else if (taxpayerType == "一般纳税人" && goodsCategory == "不动产") {\n    taxRate = 0.09;\n} else if (taxpayerType == "一般纳税人" && goodsCategory == "无形资产") {\n    taxRate = 0.06;\n} else if (taxpayerType == "小规模纳税人" && goodsCategory == "货物") {\n    taxRate = 0.03;\n} else if (taxpayerType == "小规模纳税人" && goodsCategory == "服务") {\n    taxRate = 0.03;\n} else if (taxpayerType == "小规模纳税人" && goodsCategory == "不动产") {\n    taxRate = 0.05;\n} else if (taxpayerType == "小规模纳税人" && goodsCategory == "无形资产") {\n    taxRate = 0.03;\n}\n\n_result = {"taxRate": taxRate}\n_result\n',
 'QLEXPRESS', 1, NOW())
ON DUPLICATE KEY UPDATE `model_json` = VALUES(`model_json`), `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `compile_status` = 1;

-- ---------- 4.7 JAVA 函数示例：RC_FLOW_JAVA_SAMPLE ----------
-- 演示 JAVA 类型函数 calculateVAT（反射调用 TaxFunctions.calculateVAT）
INSERT INTO `rule_definition_content` (`id`, `definition_id`, `model_json`, `compiled_script`, `compiled_type`, `compile_status`, `compile_time`) VALUES
(7, 7,
 '{"defaultEdgeLineType":"polyline","nodes":[{"id":"n1","type":"start","name":"开始","x":80,"y":200},{"id":"n2","type":"task","name":"确定税率","actionData":[{"type":"assign","target":"taxRate","value":"0.13"}],"x":250,"y":200},{"id":"n3","type":"task","name":"JAVA函数计税","actionData":[{"type":"func-call","target":"taxAmount","funcName":"calculateVAT","args":["totalAmount","taxRate"]},{"type":"func-call","target":"formatted","funcName":"formatAmount","args":["taxAmount"]}],"x":500,"y":200},{"id":"n4","type":"end","name":"结束","x":700,"y":200}],"edges":[{"id":"e1","source":"n1","target":"n2"},{"id":"e2","source":"n2","target":"n3"},{"id":"e3","source":"n3","target":"n4"}]}',
 'taxRate = null\ntaxAmount = null\nformatted = null\n// 确定税率\ntaxRate = 0.13\n\n// JAVA函数计税\ntaxAmount = calculateVAT(totalAmount, taxRate)\nformatted = formatAmount(taxAmount)\n\n_result = {"taxRate": taxRate, "taxAmount": taxAmount, "formatted": formatted}\n_result',
 'QLEXPRESS', 1, NOW())
ON DUPLICATE KEY UPDATE `model_json` = VALUES(`model_json`), `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `compile_status` = 1;

-- ---------- 4.8 BEAN 函数示例：RC_FLOW_BEAN_SAMPLE ----------
-- 演示 BEAN 类型函数 calcTaxByBean（Spring 容器 taxFunctions Bean）
INSERT INTO `rule_definition_content` (`id`, `definition_id`, `model_json`, `compiled_script`, `compiled_type`, `compile_status`, `compile_time`) VALUES
(8, 8,
 '{"defaultEdgeLineType":"polyline","nodes":[{"id":"n1","type":"start","name":"开始","x":80,"y":200},{"id":"n2","type":"task","name":"确定税率","actionData":[{"type":"assign","target":"taxRate","value":"0.13"}],"x":250,"y":200},{"id":"n3","type":"task","name":"Bean函数计税","actionData":[{"type":"func-call","target":"taxAmount","funcName":"calcTaxByBean","args":["totalAmount","taxRate"]},{"type":"func-call","target":"formatted","funcName":"formatAmount","args":["taxAmount"]}],"x":500,"y":200},{"id":"n4","type":"end","name":"结束","x":700,"y":200}],"edges":[{"id":"e1","source":"n1","target":"n2"},{"id":"e2","source":"n2","target":"n3"},{"id":"e3","source":"n3","target":"n4"}]}',
 'taxRate = null\ntaxAmount = null\nformatted = null\n// 确定税率\ntaxRate = 0.13\n\n// Bean函数计税\ntaxAmount = calcTaxByBean(totalAmount, taxRate)\nformatted = formatAmount(taxAmount)\n\n_result = {"taxRate": taxRate, "taxAmount": taxAmount, "formatted": formatted}\n_result',
 'QLEXPRESS', 1, NOW())
ON DUPLICATE KEY UPDATE `model_json` = VALUES(`model_json`), `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `compile_status` = 1;
-- ============================================================
INSERT INTO `rule_published` (`id`, `rule_code`, `definition_id`, `project_code`, `version`, `model_type`, `compiled_script`, `compiled_type`, `model_json`, `status`)
SELECT 1, 'RC_PRICING_TABLE',        1, 'RISK_DEMO', 1, 'TABLE', c.compiled_script, c.compiled_type, c.model_json, 1 FROM `rule_definition_content` c WHERE c.definition_id = 1
ON DUPLICATE KEY UPDATE `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `model_json` = VALUES(`model_json`), `project_code` = VALUES(`project_code`), `version` = 1, `status` = 1;

INSERT INTO `rule_published` (`id`, `rule_code`, `definition_id`, `project_code`, `version`, `model_type`, `compiled_script`, `compiled_type`, `model_json`, `status`)
SELECT 2, 'RC_CREDIT_TREE', 2, 'RISK_DEMO', 1, 'TREE',  c.compiled_script, c.compiled_type, c.model_json, 1 FROM `rule_definition_content` c WHERE c.definition_id = 2
ON DUPLICATE KEY UPDATE `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `model_json` = VALUES(`model_json`), `project_code` = VALUES(`project_code`), `version` = 1, `status` = 1;

INSERT INTO `rule_published` (`id`, `rule_code`, `definition_id`, `project_code`, `version`, `model_type`, `compiled_script`, `compiled_type`, `model_json`, `status`)
SELECT 3, 'RC_EXPOSURE_FLOW',        3, 'RISK_DEMO', 1, 'FLOW',  c.compiled_script, c.compiled_type, c.model_json, 1 FROM `rule_definition_content` c WHERE c.definition_id = 3
ON DUPLICATE KEY UPDATE `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `model_json` = VALUES(`model_json`), `project_code` = VALUES(`project_code`), `version` = 1, `status` = 1;

INSERT INTO `rule_published` (`id`, `rule_code`, `definition_id`, `project_code`, `version`, `model_type`, `compiled_script`, `compiled_type`, `model_json`, `status`)
SELECT 4, 'RC_RATE_MATRIX',     4, 'RISK_DEMO', 1, 'CROSS', c.compiled_script, c.compiled_type, c.model_json, 1 FROM `rule_definition_content` c WHERE c.definition_id = 4
ON DUPLICATE KEY UPDATE `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `model_json` = VALUES(`model_json`), `project_code` = VALUES(`project_code`), `version` = 1, `status` = 1;

INSERT INTO `rule_published` (`id`, `rule_code`, `definition_id`, `project_code`, `version`, `model_type`, `compiled_script`, `compiled_type`, `model_json`, `status`)
SELECT 5, 'RC_RISK_SCORECARD',      5, 'RISK_DEMO', 1, 'SCORE', c.compiled_script, c.compiled_type, c.model_json, 1 FROM `rule_definition_content` c WHERE c.definition_id = 5
ON DUPLICATE KEY UPDATE `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `model_json` = VALUES(`model_json`), `project_code` = VALUES(`project_code`), `version` = 1, `status` = 1;

INSERT INTO `rule_published` (`id`, `rule_code`, `definition_id`, `project_code`, `version`, `model_type`, `compiled_script`, `compiled_type`, `model_json`, `status`)
SELECT 6, 'RC_PRICING_BY_OBJECT',     6, 'RISK_DEMO', 1, 'TABLE', c.compiled_script, c.compiled_type, c.model_json, 1 FROM `rule_definition_content` c WHERE c.definition_id = 6
ON DUPLICATE KEY UPDATE `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `model_json` = VALUES(`model_json`), `project_code` = VALUES(`project_code`), `version` = 1, `status` = 1;

INSERT INTO `rule_published` (`id`, `rule_code`, `definition_id`, `project_code`, `version`, `model_type`, `compiled_script`, `compiled_type`, `model_json`, `status`)
SELECT 7, 'RC_FLOW_JAVA_SAMPLE', 7, 'RISK_DEMO', 1, 'FLOW', c.compiled_script, c.compiled_type, c.model_json, 1 FROM `rule_definition_content` c WHERE c.definition_id = 7
ON DUPLICATE KEY UPDATE `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `model_json` = VALUES(`model_json`), `project_code` = VALUES(`project_code`), `version` = 1, `status` = 1;

INSERT INTO `rule_published` (`id`, `rule_code`, `definition_id`, `project_code`, `version`, `model_type`, `compiled_script`, `compiled_type`, `model_json`, `status`)
SELECT 8, 'RC_FLOW_BEAN_SAMPLE', 8, 'RISK_DEMO', 1, 'FLOW', c.compiled_script, c.compiled_type, c.model_json, 1 FROM `rule_definition_content` c WHERE c.definition_id = 8
ON DUPLICATE KEY UPDATE `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `model_json` = VALUES(`model_json`), `project_code` = VALUES(`project_code`), `version` = 1, `status` = 1;

-- ============================================================
-- 6. 版本历史（记录第一次发布）
-- ============================================================
INSERT INTO `rule_definition_version` (`definition_id`, `version`, `model_json`, `compiled_script`, `compiled_type`, `change_log`, `publish_by`) VALUES
(1, 1, (SELECT `model_json` FROM `rule_definition_content` WHERE `definition_id` = 1), (SELECT `compiled_script` FROM `rule_definition_content` WHERE `definition_id` = 1), 'QLEXPRESS', '初始发布 - 客商×产品总线定价决策表', 'system'),
(2, 1, (SELECT `model_json` FROM `rule_definition_content` WHERE `definition_id` = 2), (SELECT `compiled_script` FROM `rule_definition_content` WHERE `definition_id` = 2), 'QLEXPRESS', '初始发布 - 客户信用分层决策树', 'system'),
(3, 1, (SELECT `model_json` FROM `rule_definition_content` WHERE `definition_id` = 3), (SELECT `compiled_script` FROM `rule_definition_content` WHERE `definition_id` = 3), 'QLEXPRESS', '初始发布 - 敞口与费用试算流程', 'system'),
(4, 1, (SELECT `model_json` FROM `rule_definition_content` WHERE `definition_id` = 4), (SELECT `compiled_script` FROM `rule_definition_content` WHERE `definition_id` = 4), 'QLEXPRESS', '初始发布 - 风险定价交叉表（客商类型×产品总线）', 'system'),
(5, 1, (SELECT `model_json` FROM `rule_definition_content` WHERE `definition_id` = 5), (SELECT `compiled_script` FROM `rule_definition_content` WHERE `definition_id` = 5), 'QLEXPRESS', '初始发布 - 综合风险评分卡', 'system'),
(6, 1, (SELECT `model_json` FROM `rule_definition_content` WHERE `definition_id` = 6), (SELECT `compiled_script` FROM `rule_definition_content` WHERE `definition_id` = 6), 'QLEXPRESS', '初始发布 - 对象上下文定价示例', 'system'),
(7, 1, (SELECT `model_json` FROM `rule_definition_content` WHERE `definition_id` = 7), (SELECT `compiled_script` FROM `rule_definition_content` WHERE `definition_id` = 7), 'QLEXPRESS', '初始发布 - Java 函数费用试算', 'system'),
(8, 1, (SELECT `model_json` FROM `rule_definition_content` WHERE `definition_id` = 8), (SELECT `compiled_script` FROM `rule_definition_content` WHERE `definition_id` = 8), 'QLEXPRESS', '初始发布 - Spring Bean 费用试算', 'system')
ON DUPLICATE KEY UPDATE `change_log` = VALUES(`change_log`);

-- ============================================================
-- 7. 扩展风控示例 —— 新增变量（复杂交叉表/复杂评分卡/QL脚本三种新模型类型）
-- ============================================================
INSERT INTO `rule_variable` (`id`, `project_id`, `var_code`, `var_label`, `script_name`, `var_type`, `var_source`, `default_value`, `example_value`, `description`, `sort_order`, `status`) VALUES
(15, 1, 'serviceType',             '业务类型',       'serviceType',             'ENUM',   'INPUT',    NULL,    '基础通信', '基础通信/增值业务/宽带接入/ICT服务',         15, 1),
(16, 1, 'paymentMode',             '结算方式',       'paymentMode',             'ENUM',   'INPUT',    NULL,    '后付费',   '预付费/后付费',                           16, 1),
(17, 1, 'customerType',            '客户类型',       'customerType',            'ENUM',   'INPUT',    NULL,    '企业客户', '企业客户/个人客户/政企客户',                17, 1),
(18, 1, 'taxpayerQualification',   '纳税人资格',     'taxpayerQualification',   'ENUM',   'INPUT',    NULL,    '一般纳税人', '一般纳税人/小规模纳税人',                18, 1),
(19, 1, 'multiDimRate',           '多维组合定价系数',   'multiDimRate',           'NUMBER', 'COMPUTED', NULL,    '0.09',     '复杂交叉表/QL脚本输出的组合定价系数',          19, 1),
(20, 1, 'customerLevel',           '客户等级',       'customerLevel',           'ENUM',   'INPUT',    NULL,    '金',       '钻石/金/银/铜',                          20, 1),
(21, 1, 'monthlyConsumption',      '月消费金额(元)', 'monthlyConsumption',      'NUMBER', 'INPUT',    NULL,    '5000',     '月均消费额（元）',                        21, 1),
(22, 1, 'invoiceDeviationRate',    '开票偏差率',     'invoiceDeviationRate',    'NUMBER', 'INPUT',    NULL,    '0.05',     '月开票金额与消费金额的偏差百分比',          22, 1),
(23, 1, 'redInvoiceRatio',         '红冲发票比例',   'redInvoiceRatio',         'NUMBER', 'INPUT',    NULL,    '0.02',     '红冲发票占总开票量的百分比',               23, 1),
(24, 1, 'zeroRateInvoiceRatio',    '零税率发票占比', 'zeroRateInvoiceRatio',    'NUMBER', 'INPUT',    NULL,    '0.01',     '零税率发票金额占比',                      24, 1),
(25, 1, 'crossRegionInvoiceRatio', '跨地区开票比例', 'crossRegionInvoiceRatio', 'NUMBER', 'INPUT',    NULL,    '0.08',     '跨地区开票金额占比',                      25, 1),
(26, 1, 'invoiceRiskScore',        '发票风险评分',   'invoiceRiskScore',        'NUMBER', 'COMPUTED', NULL,    '135',      '复杂评分卡输出的风险总评分',               26, 1),
(27, 1, 'invoiceRiskLevel',        '发票风险等级',   'invoiceRiskLevel',        'STRING', 'COMPUTED', NULL,    '低风险',   '评分卡输出：低风险/中风险/高风险',          27, 1),
(28, 1, 'billingAmount',           '含税账单金额(元)', 'billingAmount',         'NUMBER', 'INPUT',    NULL,    '100000',   'QL脚本输入：含税账单总金额',               28, 1),
(29, 1, 'basicServiceRatio',       '基础通信占比',   'basicServiceRatio',       'NUMBER', 'INPUT',    NULL,    '0.6',      '混合经营中基础通信收入占比',               29, 1),
(30, 1, 'vasServiceRatio',         '增值业务占比',   'vasServiceRatio',         'NUMBER', 'INPUT',    NULL,    '0.4',      '混合经营中增值业务收入占比',               30, 1)
ON DUPLICATE KEY UPDATE `var_label` = VALUES(`var_label`);

-- 业务类型枚举选项
INSERT INTO `rule_variable_option` (`variable_id`, `option_value`, `option_label`, `sort_order`) VALUES
(15, '基础通信', '基础通信', 1), (15, '增值业务', '增值业务', 2), (15, '宽带接入', '宽带接入', 3), (15, 'ICT服务', 'ICT服务', 4)
ON DUPLICATE KEY UPDATE `option_label` = VALUES(`option_label`);

-- 结算方式枚举选项
INSERT INTO `rule_variable_option` (`variable_id`, `option_value`, `option_label`, `sort_order`) VALUES
(16, '预付费', '预付费', 1), (16, '后付费', '后付费', 2)
ON DUPLICATE KEY UPDATE `option_label` = VALUES(`option_label`);

-- 客户类型枚举选项
INSERT INTO `rule_variable_option` (`variable_id`, `option_value`, `option_label`, `sort_order`) VALUES
(17, '企业客户', '企业客户', 1), (17, '个人客户', '个人客户', 2), (17, '政企客户', '政企客户', 3)
ON DUPLICATE KEY UPDATE `option_label` = VALUES(`option_label`);

-- 纳税人资格枚举选项
INSERT INTO `rule_variable_option` (`variable_id`, `option_value`, `option_label`, `sort_order`) VALUES
(18, '一般纳税人', '一般纳税人', 1), (18, '小规模纳税人', '小规模纳税人', 2)
ON DUPLICATE KEY UPDATE `option_label` = VALUES(`option_label`);

-- 客户等级枚举选项
INSERT INTO `rule_variable_option` (`variable_id`, `option_value`, `option_label`, `sort_order`) VALUES
(20, '钻石', '钻石', 1), (20, '金', '金', 2), (20, '银', '银', 3), (20, '铜', '铜', 4)
ON DUPLICATE KEY UPDATE `option_label` = VALUES(`option_label`);

-- ============================================================
-- 8. 扩展风控示例 —— 规则定义（3条新规则：复杂交叉表/复杂评分卡/QL脚本）
-- ============================================================
INSERT INTO `rule_definition` (`id`, `project_id`, `rule_code`, `rule_name`, `model_type`, `description`, `current_version`, `published_version`, `status`) VALUES
(9,  1, 'RC_MULTI_DIM_RATE',    '交叉矩阵多维定价（8×6）',       'CROSS_ADV', '行维度：业务类型×结算方式（8）；列维度：客户类型×纳税人资格（6）。与复杂交叉矩阵设计器示例一致（含 ICT 后付一般纳税人 0.13）', 1, 1, 1),
(10, 1, 'RC_INVOICE_FRAUD_SCORE', '交易票据异常评分',       'SCORE_ADV', '基于客户等级、交易规模、票据偏离与地域异常等指标的分组加权评分（复杂评分卡示例）', 1, 1, 1),
(11, 1, 'RC_BLEND_CALC_SCRIPT',    '混业组合计费脚本', 'SCRIPT',    '多费率产品线按比例分拆计费，并支持简易计税口径（QL脚本示例）', 1, 1, 1)
ON DUPLICATE KEY UPDATE `rule_name` = VALUES(`rule_name`), `description` = VALUES(`description`), `status` = 1, `published_version` = 1;

-- ============================================================
-- 9. 扩展风控示例 —— 规则内容
-- ============================================================

-- ---------- 9.1 复杂交叉表：RC_MULTI_DIM_RATE（与复杂交叉矩阵 8×6 设计器示例一致；列顺序=企业/个人/政企 × 一般/小规模）----------
INSERT INTO `rule_definition_content` (`id`, `definition_id`, `model_json`, `compiled_script`, `compiled_type`, `compile_status`, `compile_time`) VALUES
(9, 9,
'{"rowDimensions":[{"varCode":"serviceType","varLabel":"业务类型","varType":"ENUM","segments":[{"label":"基础通信","operator":"==","value":"基础通信"},{"label":"增值业务","operator":"==","value":"增值业务"},{"label":"宽带接入","operator":"==","value":"宽带接入"},{"label":"ICT服务","operator":"==","value":"ICT服务"}]},{"varCode":"paymentMode","varLabel":"结算方式","varType":"ENUM","segments":[{"label":"预付费","operator":"==","value":"预付费"},{"label":"后付费","operator":"==","value":"后付费"}]}],"colDimensions":[{"varCode":"customerType","varLabel":"客户类型","varType":"ENUM","segments":[{"label":"企业客户","operator":"==","value":"企业客户"},{"label":"个人客户","operator":"==","value":"个人客户"},{"label":"政企客户","operator":"==","value":"政企客户"}]},{"varCode":"taxpayerQualification","varLabel":"纳税人资格","varType":"ENUM","segments":[{"label":"一般纳税人","operator":"==","value":"一般纳税人"},{"label":"小规模纳税人","operator":"==","value":"小规模纳税人"}]}],"resultVar":{"varCode":"multiDimRate","varLabel":"多维组合定价系数","varType":"NUMBER"},"cells":[["0.09","0.03","0.09","0.03","0.09","0.03"],["0.09","0.03","0.09","0.03","0.09","0.03"],["0.06","0.03","0.06","0.03","0.06","0.03"],["0.06","0.03","0.06","0.03","0.06","0.03"],["0.09","0.03","0.09","0.03","0.09","0.03"],["0.09","0.03","0.09","0.03","0.09","0.03"],["0.06","0.03","0.06","0.03","0.06","0.03"],["0.13","0.03","0.13","0.03","0.13","0.03"]]}',
 'if (serviceType == "基础通信" && paymentMode == "预付费" && customerType == "企业客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.09\n} else if (serviceType == "基础通信" && paymentMode == "预付费" && customerType == "企业客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "基础通信" && paymentMode == "预付费" && customerType == "个人客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.09\n} else if (serviceType == "基础通信" && paymentMode == "预付费" && customerType == "个人客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "基础通信" && paymentMode == "预付费" && customerType == "政企客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.09\n} else if (serviceType == "基础通信" && paymentMode == "预付费" && customerType == "政企客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "基础通信" && paymentMode == "后付费" && customerType == "企业客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.09\n} else if (serviceType == "基础通信" && paymentMode == "后付费" && customerType == "企业客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "基础通信" && paymentMode == "后付费" && customerType == "个人客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.09\n} else if (serviceType == "基础通信" && paymentMode == "后付费" && customerType == "个人客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "基础通信" && paymentMode == "后付费" && customerType == "政企客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.09\n} else if (serviceType == "基础通信" && paymentMode == "后付费" && customerType == "政企客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "增值业务" && paymentMode == "预付费" && customerType == "企业客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.06\n} else if (serviceType == "增值业务" && paymentMode == "预付费" && customerType == "企业客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "增值业务" && paymentMode == "预付费" && customerType == "个人客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.06\n} else if (serviceType == "增值业务" && paymentMode == "预付费" && customerType == "个人客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "增值业务" && paymentMode == "预付费" && customerType == "政企客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.06\n} else if (serviceType == "增值业务" && paymentMode == "预付费" && customerType == "政企客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "增值业务" && paymentMode == "后付费" && customerType == "企业客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.06\n} else if (serviceType == "增值业务" && paymentMode == "后付费" && customerType == "企业客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "增值业务" && paymentMode == "后付费" && customerType == "个人客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.06\n} else if (serviceType == "增值业务" && paymentMode == "后付费" && customerType == "个人客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "增值业务" && paymentMode == "后付费" && customerType == "政企客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.06\n} else if (serviceType == "增值业务" && paymentMode == "后付费" && customerType == "政企客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "宽带接入" && paymentMode == "预付费" && customerType == "企业客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.09\n} else if (serviceType == "宽带接入" && paymentMode == "预付费" && customerType == "企业客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "宽带接入" && paymentMode == "预付费" && customerType == "个人客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.09\n} else if (serviceType == "宽带接入" && paymentMode == "预付费" && customerType == "个人客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "宽带接入" && paymentMode == "预付费" && customerType == "政企客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.09\n} else if (serviceType == "宽带接入" && paymentMode == "预付费" && customerType == "政企客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "宽带接入" && paymentMode == "后付费" && customerType == "企业客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.09\n} else if (serviceType == "宽带接入" && paymentMode == "后付费" && customerType == "企业客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "宽带接入" && paymentMode == "后付费" && customerType == "个人客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.09\n} else if (serviceType == "宽带接入" && paymentMode == "后付费" && customerType == "个人客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "宽带接入" && paymentMode == "后付费" && customerType == "政企客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.09\n} else if (serviceType == "宽带接入" && paymentMode == "后付费" && customerType == "政企客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "ICT服务" && paymentMode == "预付费" && customerType == "企业客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.06\n} else if (serviceType == "ICT服务" && paymentMode == "预付费" && customerType == "企业客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "ICT服务" && paymentMode == "预付费" && customerType == "个人客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.06\n} else if (serviceType == "ICT服务" && paymentMode == "预付费" && customerType == "个人客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "ICT服务" && paymentMode == "预付费" && customerType == "政企客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.06\n} else if (serviceType == "ICT服务" && paymentMode == "预付费" && customerType == "政企客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "ICT服务" && paymentMode == "后付费" && customerType == "企业客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.13\n} else if (serviceType == "ICT服务" && paymentMode == "后付费" && customerType == "企业客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "ICT服务" && paymentMode == "后付费" && customerType == "个人客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.13\n} else if (serviceType == "ICT服务" && paymentMode == "后付费" && customerType == "个人客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n} else if (serviceType == "ICT服务" && paymentMode == "后付费" && customerType == "政企客户" && taxpayerQualification == "一般纳税人") {\n    multiDimRate = 0.13\n} else if (serviceType == "ICT服务" && paymentMode == "后付费" && customerType == "政企客户" && taxpayerQualification == "小规模纳税人") {\n    multiDimRate = 0.03\n}\n',
 'QLEXPRESS', 1, NOW())
ON DUPLICATE KEY UPDATE `model_json` = VALUES(`model_json`), `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `compile_status` = 1;

-- ---------- 9.2 复杂评分卡：RC_INVOICE_FRAUD_SCORE ----------
INSERT INTO `rule_definition_content` (`id`, `definition_id`, `model_json`, `compiled_script`, `compiled_type`, `compile_status`, `compile_time`) VALUES
(10, 10,
 '{"initialScore":100,"resultVar":{"varCode":"invoiceRiskScore","varLabel":"发票风险评分"},"dimensionGroups":[{"groupLabel":"客户基础信息","dimensions":[{"varCode":"customerLevel","varLabel":"客户等级","rules":[{"conditions":[{"varCode":"customerLevel","operator":"==","value":"钻石"}],"score":20},{"conditions":[{"varCode":"customerLevel","operator":"==","value":"金"}],"score":15},{"conditions":[{"varCode":"customerLevel","operator":"==","value":"银"}],"score":10},{"conditions":[{"varCode":"customerLevel","operator":"==","value":"铜"}],"score":5}]},{"varCode":"monthlyConsumption","varLabel":"月消费金额","rules":[{"conditions":[{"varCode":"monthlyConsumption","operator":">=","value":"10000"}],"score":15},{"conditions":[{"varCode":"monthlyConsumption","operator":">=","value":"3000"},{"varCode":"monthlyConsumption","operator":"<","value":"10000"}],"score":10},{"conditions":[{"varCode":"monthlyConsumption","operator":"<","value":"3000"}],"score":5}]}]},{"groupLabel":"发票合规指标","dimensions":[{"varCode":"invoiceDeviationRate","varLabel":"开票偏差率","rules":[{"conditions":[{"varCode":"invoiceDeviationRate","operator":"<=","value":"0.05"}],"score":15},{"conditions":[{"varCode":"invoiceDeviationRate","operator":">","value":"0.05"},{"varCode":"invoiceDeviationRate","operator":"<=","value":"0.15"}],"score":5},{"conditions":[{"varCode":"invoiceDeviationRate","operator":">","value":"0.15"}],"score":-10}]},{"varCode":"redInvoiceRatio","varLabel":"红冲发票比例","rules":[{"conditions":[{"varCode":"redInvoiceRatio","operator":"<=","value":"0.03"}],"score":10},{"conditions":[{"varCode":"redInvoiceRatio","operator":">","value":"0.03"},{"varCode":"redInvoiceRatio","operator":"<=","value":"0.10"}],"score":0},{"conditions":[{"varCode":"redInvoiceRatio","operator":">","value":"0.10"}],"score":-15}]}]},{"groupLabel":"税务风险指标","dimensions":[{"varCode":"zeroRateInvoiceRatio","varLabel":"零税率发票占比","rules":[{"conditions":[{"varCode":"zeroRateInvoiceRatio","operator":"<=","value":"0.05"}],"score":10},{"conditions":[{"varCode":"zeroRateInvoiceRatio","operator":">","value":"0.05"}],"score":-10}]},{"varCode":"crossRegionInvoiceRatio","varLabel":"跨地区开票比例","rules":[{"conditions":[{"varCode":"crossRegionInvoiceRatio","operator":"<=","value":"0.10"}],"score":10},{"conditions":[{"varCode":"crossRegionInvoiceRatio","operator":">","value":"0.10"},{"varCode":"crossRegionInvoiceRatio","operator":"<=","value":"0.30"}],"score":0},{"conditions":[{"varCode":"crossRegionInvoiceRatio","operator":">","value":"0.30"}],"score":-15}]}]}],"thresholds":[{"min":80,"max":999,"result":"低风险"},{"min":60,"max":80,"result":"中风险"},{"min":0,"max":60,"result":"高风险"}]}',
 'invoiceRiskScore = 100.0\n\n// ---- 客户基础信息 ----\n_dim_0_0 = 0\nif (customerLevel == \"钻石\") {\n    _dim_0_0 = 20.0\n} else if (customerLevel == \"金\") {\n    _dim_0_0 = 15.0\n} else if (customerLevel == \"银\") {\n    _dim_0_0 = 10.0\n} else if (customerLevel == \"铜\") {\n    _dim_0_0 = 5.0\n}\n// 客户等级 得分累加\ninvoiceRiskScore = invoiceRiskScore + _dim_0_0\n_dim_0_1 = 0\nif (monthlyConsumption >= 10000) {\n    _dim_0_1 = 15.0\n} else if (monthlyConsumption >= 3000 && monthlyConsumption < 10000) {\n    _dim_0_1 = 10.0\n} else if (monthlyConsumption < 3000) {\n    _dim_0_1 = 5.0\n}\n// 月消费金额 得分累加\ninvoiceRiskScore = invoiceRiskScore + _dim_0_1\n\n// ---- 发票合规指标 ----\n_dim_1_0 = 0\nif (invoiceDeviationRate <= 0.05) {\n    _dim_1_0 = 15.0\n} else if (invoiceDeviationRate > 0.05 && invoiceDeviationRate <= 0.15) {\n    _dim_1_0 = 5.0\n} else if (invoiceDeviationRate > 0.15) {\n    _dim_1_0 = -10.0\n}\n// 开票偏差率 得分累加\ninvoiceRiskScore = invoiceRiskScore + _dim_1_0\n_dim_1_1 = 0\nif (redInvoiceRatio <= 0.03) {\n    _dim_1_1 = 10.0\n} else if (redInvoiceRatio > 0.03 && redInvoiceRatio <= 0.10) {\n    _dim_1_1 = 0.0\n} else if (redInvoiceRatio > 0.10) {\n    _dim_1_1 = -15.0\n}\n// 红冲发票比例 得分累加\ninvoiceRiskScore = invoiceRiskScore + _dim_1_1\n\n// ---- 税务风险指标 ----\n_dim_2_0 = 0\nif (zeroRateInvoiceRatio <= 0.05) {\n    _dim_2_0 = 10.0\n} else if (zeroRateInvoiceRatio > 0.05) {\n    _dim_2_0 = -10.0\n}\n// 零税率发票占比 得分累加\ninvoiceRiskScore = invoiceRiskScore + _dim_2_0\n_dim_2_1 = 0\nif (crossRegionInvoiceRatio <= 0.10) {\n    _dim_2_1 = 10.0\n} else if (crossRegionInvoiceRatio > 0.10 && crossRegionInvoiceRatio <= 0.30) {\n    _dim_2_1 = 0.0\n} else if (crossRegionInvoiceRatio > 0.30) {\n    _dim_2_1 = -15.0\n}\n// 跨地区开票比例 得分累加\ninvoiceRiskScore = invoiceRiskScore + _dim_2_1\n\n// ---- 等级判定 ----\nriskLevel = \"未知\"\nif (invoiceRiskScore >= 80.0 && invoiceRiskScore < 999.0) {\n    riskLevel = \"低风险\"\n} else if (invoiceRiskScore >= 60.0 && invoiceRiskScore < 80.0) {\n    riskLevel = \"中风险\"\n} else if (invoiceRiskScore >= 0.0 && invoiceRiskScore < 60.0) {\n    riskLevel = \"高风险\"\n}\n\n_result = {"invoiceRiskScore": invoiceRiskScore, "riskLevel": riskLevel}\n_result\n',
 'QLEXPRESS', 1, NOW())
ON DUPLICATE KEY UPDATE `model_json` = VALUES(`model_json`), `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `compile_status` = 1;

-- ---------- 9.3 QL脚本：RC_BLEND_CALC_SCRIPT ----------
INSERT INTO `rule_definition_content` (`id`, `definition_id`, `model_json`, `compiled_script`, `compiled_type`, `compile_status`, `compile_time`) VALUES
(11, 11,
 '{"script":"// 混业场景组合计费脚本\\n// 场景：低费率产品线(9%) + 高费率产品线(6%) 按比例分拆\\n\\nif (taxpayerQualification == \\"小规模纳税人\\") {\\n    multiDimRate = 0.03\\n    excludingTaxAmount = billingAmount / (1 + multiDimRate)\\n    taxAmount = roundTax(excludingTaxAmount * multiDimRate)\\n} else {\\n    basicAmount = billingAmount * basicServiceRatio\\n    basicExcludingTax = basicAmount / (1 + 0.09)\\n    basicTax = basicExcludingTax * 0.09\\n\\n    vasAmount = billingAmount * vasServiceRatio\\n    vasExcludingTax = vasAmount / (1 + 0.06)\\n    vasTax = vasExcludingTax * 0.06\\n\\n    excludingTaxAmount = roundTax(basicExcludingTax + vasExcludingTax)\\n    taxAmount = roundTax(basicTax + vasTax)\\n\\n    if (excludingTaxAmount > 0) {\\n        multiDimRate = roundTax(taxAmount / excludingTaxAmount)\\n    } else {\\n        multiDimRate = 0\\n    }\\n}\\n\\nfinalAmount = billingAmount\\nnetAmount = excludingTaxAmount\\nvatAmount = taxAmount"}',
 '// 混业场景组合计费脚本\n// 场景：低费率产品线(9%) + 高费率产品线(6%) 按比例分拆\n\nif (taxpayerQualification == \"小规模纳税人\") {\n    multiDimRate = 0.03\n    excludingTaxAmount = billingAmount / (1 + multiDimRate)\n    taxAmount = roundTax(excludingTaxAmount * multiDimRate)\n} else {\n    basicAmount = billingAmount * basicServiceRatio\n    basicExcludingTax = basicAmount / (1 + 0.09)\n    basicTax = basicExcludingTax * 0.09\n\n    vasAmount = billingAmount * vasServiceRatio\n    vasExcludingTax = vasAmount / (1 + 0.06)\n    vasTax = vasExcludingTax * 0.06\n\n    excludingTaxAmount = roundTax(basicExcludingTax + vasExcludingTax)\n    taxAmount = roundTax(basicTax + vasTax)\n\n    if (excludingTaxAmount > 0) {\n        multiDimRate = roundTax(taxAmount / excludingTaxAmount)\n    } else {\n        multiDimRate = 0\n    }\n}\n\nfinalAmount = billingAmount\nnetAmount = excludingTaxAmount\nvatAmount = taxAmount',
 'QLEXPRESS', 1, NOW())
ON DUPLICATE KEY UPDATE `model_json` = VALUES(`model_json`), `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `compile_status` = 1;

-- ============================================================
-- 10. 扩展风控示例 —— 发布记录
-- ============================================================
INSERT INTO `rule_published` (`id`, `rule_code`, `definition_id`, `project_code`, `version`, `model_type`, `compiled_script`, `compiled_type`, `model_json`, `status`)
SELECT 9,  'RC_MULTI_DIM_RATE',    9,  'RISK_DEMO', 1, 'CROSS_ADV', c.compiled_script, c.compiled_type, c.model_json, 1 FROM `rule_definition_content` c WHERE c.definition_id = 9
ON DUPLICATE KEY UPDATE `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `model_json` = VALUES(`model_json`), `project_code` = VALUES(`project_code`), `version` = 1, `status` = 1;

INSERT INTO `rule_published` (`id`, `rule_code`, `definition_id`, `project_code`, `version`, `model_type`, `compiled_script`, `compiled_type`, `model_json`, `status`)
SELECT 10, 'RC_INVOICE_FRAUD_SCORE', 10, 'RISK_DEMO', 1, 'SCORE_ADV', c.compiled_script, c.compiled_type, c.model_json, 1 FROM `rule_definition_content` c WHERE c.definition_id = 10
ON DUPLICATE KEY UPDATE `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `model_json` = VALUES(`model_json`), `project_code` = VALUES(`project_code`), `version` = 1, `status` = 1;

INSERT INTO `rule_published` (`id`, `rule_code`, `definition_id`, `project_code`, `version`, `model_type`, `compiled_script`, `compiled_type`, `model_json`, `status`)
SELECT 11, 'RC_BLEND_CALC_SCRIPT',    11, 'RISK_DEMO', 1, 'SCRIPT',    c.compiled_script, c.compiled_type, c.model_json, 1 FROM `rule_definition_content` c WHERE c.definition_id = 11
ON DUPLICATE KEY UPDATE `compiled_script` = VALUES(`compiled_script`), `compiled_type` = VALUES(`compiled_type`), `model_json` = VALUES(`model_json`), `project_code` = VALUES(`project_code`), `version` = 1, `status` = 1;

-- ============================================================
-- 11. 扩展风控示例 —— 版本历史
-- ============================================================
INSERT INTO `rule_definition_version` (`definition_id`, `version`, `model_json`, `compiled_script`, `compiled_type`, `change_log`, `publish_by`) VALUES
(9,  1, (SELECT `model_json` FROM `rule_definition_content` WHERE `definition_id` = 9),  (SELECT `compiled_script` FROM `rule_definition_content` WHERE `definition_id` = 9),  'QLEXPRESS', '初始发布 - 交叉矩阵多维定价 8×6（复杂交叉表）', 'system'),
(10, 1, (SELECT `model_json` FROM `rule_definition_content` WHERE `definition_id` = 10), (SELECT `compiled_script` FROM `rule_definition_content` WHERE `definition_id` = 10), 'QLEXPRESS', '初始发布 - 交易票据异常评分（复杂评分卡）', 'system'),
(11, 1, (SELECT `model_json` FROM `rule_definition_content` WHERE `definition_id` = 11), (SELECT `compiled_script` FROM `rule_definition_content` WHERE `definition_id` = 11), 'QLEXPRESS', '初始发布 - 混业组合计费脚本（QL脚本）', 'system')
ON DUPLICATE KEY UPDATE `change_log` = VALUES(`change_log`);
