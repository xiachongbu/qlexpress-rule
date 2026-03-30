CREATE DATABASE IF NOT EXISTS `rule_engine` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `rule_engine`;

-- ============================================================
-- 1. rule_project - 规则项目表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_project` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_code` VARCHAR(64)  NOT NULL                COMMENT '项目编码',
  `project_name` VARCHAR(128) NOT NULL                COMMENT '项目名称（中文）',
  `description`  VARCHAR(512) DEFAULT NULL             COMMENT '项目描述',
  `status`       TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `access_token` VARCHAR(64)  DEFAULT NULL             COMMENT '访问Token',
  `create_by`    VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    VARCHAR(64)  DEFAULT NULL             COMMENT '更新人',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_code` (`project_code`),
  UNIQUE KEY `uk_access_token` (`access_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则项目表';

-- ============================================================
-- 2. rule_definition - 规则定义表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_definition` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`        BIGINT       NOT NULL                COMMENT '所属项目ID',
  `rule_code`         VARCHAR(128) NOT NULL                COMMENT '规则编码（Client SDK调用标识）',
  `rule_name`         VARCHAR(256) NOT NULL                COMMENT '规则名称（中文）',
  `model_type`        VARCHAR(16)  NOT NULL                COMMENT '决策模型类型：TABLE/TREE/FLOW/CROSS/SCORE',
  `description`       VARCHAR(512) DEFAULT NULL             COMMENT '规则描述',
  `current_version`   INT          NOT NULL DEFAULT 0       COMMENT '当前设计版本号',
  `published_version` INT          DEFAULT NULL             COMMENT '已发布版本号',
  `status`            TINYINT      NOT NULL DEFAULT 0       COMMENT '状态：0-草稿，1-已发布，2-已下线',
  `create_by`         VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`         VARCHAR(64)  DEFAULT NULL             COMMENT '更新人',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_code` (`rule_code`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_model_type` (`model_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则定义表';

-- ============================================================
-- 3. rule_definition_content - 规则内容表（设计态）
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_definition_content` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `definition_id`   BIGINT   NOT NULL                COMMENT '规则定义ID',
  `model_json`      LONGTEXT NOT NULL                COMMENT '模型设计数据（JSON）',
  `compiled_script` TEXT     DEFAULT NULL             COMMENT '编译后脚本',
  `compiled_type`   VARCHAR(16) DEFAULT NULL          COMMENT '编译产物类型：QLEXPRESS',
  `compile_status`  TINYINT  NOT NULL DEFAULT 0       COMMENT '编译状态：0-未编译，1-成功，2-失败',
  `compile_message` VARCHAR(1024) DEFAULT NULL        COMMENT '编译信息',
  `compile_time`    DATETIME DEFAULT NULL             COMMENT '最近编译时间',
  `script_mode`     VARCHAR(16) NOT NULL DEFAULT 'visual' COMMENT '编辑模式：visual-可视化，script-脚本模式',
  `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_definition_id` (`definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则内容表（设计态数据和编译产物）';

-- ============================================================
-- 4. rule_definition_version - 规则版本历史表（HASH分区）
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_definition_version` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `definition_id`   BIGINT       NOT NULL                COMMENT '规则定义ID',
  `version`         INT          NOT NULL                COMMENT '版本号',
  `model_json`      LONGTEXT     NOT NULL                COMMENT '版本快照 - 模型JSON',
  `compiled_script` TEXT         DEFAULT NULL             COMMENT '版本快照 - 编译后脚本',
  `compiled_type`   VARCHAR(16)  DEFAULT NULL             COMMENT '编译产物类型',
  `change_log`      VARCHAR(512) DEFAULT NULL             COMMENT '变更说明（中文）',
  `publish_by`      VARCHAR(64)  DEFAULT NULL             COMMENT '发布人',
  `publish_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (`id`, `definition_id`),
  UNIQUE KEY `uk_def_version` (`definition_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则版本历史表'
PARTITION BY HASH(`definition_id`) PARTITIONS 8;

-- ============================================================
-- 5. rule_published - 已发布规则表（Client SDK同步数据源）
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_published` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_code`       VARCHAR(128) NOT NULL                COMMENT '规则编码',
  `definition_id`   BIGINT       NOT NULL                COMMENT '规则定义ID',
  `project_code`    VARCHAR(64)  DEFAULT NULL             COMMENT '所属项目编码',
  `version`         INT          NOT NULL                COMMENT '发布版本号',
  `model_type`      VARCHAR(16)  NOT NULL                COMMENT '决策模型类型',
  `compiled_script` TEXT         NOT NULL                COMMENT '编译后脚本',
  `compiled_type`   VARCHAR(16)  DEFAULT NULL             COMMENT '编译产物类型',
  `model_json`      LONGTEXT     DEFAULT NULL             COMMENT '模型JSON（设计器回显用）',
  `status`          TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-已下线，1-已上线',
  `publish_by`      VARCHAR(64)  DEFAULT NULL             COMMENT '发布人',
  `publish_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `offline_time`    DATETIME     DEFAULT NULL             COMMENT '下线时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_code` (`rule_code`),
  KEY `idx_status` (`status`),
  KEY `idx_definition_id` (`definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已发布规则表（Client SDK同步数据源）';

-- ============================================================
-- 6. rule_data_object - 数据对象定义表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_data_object` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`       BIGINT       NOT NULL                COMMENT '所属项目ID',
  `object_code`      VARCHAR(128) NOT NULL                COMMENT '对象编码（Java类名/JSON键名）',
  `object_label`     VARCHAR(128) DEFAULT NULL             COMMENT '对象中文名称',
  `script_name`      VARCHAR(128) DEFAULT NULL             COMMENT '脚本中的对象引用名（默认驼峰，如 taxRequest）',
  `object_type`      VARCHAR(16)  NOT NULL DEFAULT 'INPUT' COMMENT '对象类型：INPUT-输入/OUTPUT-输出/INOUT-输入输出',
  `source_type`      VARCHAR(16)  DEFAULT NULL             COMMENT '来源类型：JAVA/JSON',
  `source_content`   LONGTEXT     DEFAULT NULL             COMMENT '原始文件内容',
  `parent_object_id` BIGINT       DEFAULT NULL             COMMENT '父对象ID（嵌套对象）',
  `status`           TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_object` (`project_id`, `object_code`),
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据对象定义表（Java实体类/JSON对象）';

-- ============================================================
-- 7. rule_data_object_field - 数据对象字段表（与 rule_variable 解耦）
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_data_object_field` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`       BIGINT       NOT NULL                COMMENT '所属项目ID',
  `object_id`        BIGINT       NOT NULL                COMMENT '所属数据对象ID',
  `var_code`         VARCHAR(128) NOT NULL                COMMENT '字段编码',
  `var_label`        VARCHAR(128) NOT NULL                COMMENT '字段中文名称',
  `script_name`      VARCHAR(128) DEFAULT NULL             COMMENT '脚本中的字段名（驼峰）',
  `var_type`         VARCHAR(32)  NOT NULL                COMMENT '数据类型：STRING/NUMBER/BOOLEAN/DATE/ENUM/OBJECT/LIST/MAP',
  `ref_object_code`  VARCHAR(128) DEFAULT NULL             COMMENT 'OBJECT 时引用的对象编码',
  `parent_field_id`  BIGINT       DEFAULT NULL             COMMENT '父字段ID（嵌套预留）',
  `sort_order`       INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  `status`           TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_object_var_code` (`object_id`, `var_code`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_object_id` (`object_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据对象字段表';

-- ============================================================
-- 8. rule_data_object_field_option - 对象字段枚举选项
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_data_object_field_option` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `field_id`     BIGINT       NOT NULL                COMMENT '所属对象字段ID',
  `option_value` VARCHAR(256) NOT NULL                COMMENT '选项值',
  `option_label` VARCHAR(256) NOT NULL                COMMENT '选项中文标签',
  `sort_order`   INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  PRIMARY KEY (`id`),
  KEY `idx_field_id` (`field_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据对象字段枚举选项';

-- ============================================================
-- 9. rule_variable - 规则变量表（普通变量与常量，var_source=CONSTANT 时须配置 default_value）
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_variable` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`        BIGINT       NOT NULL                COMMENT '所属项目ID',
  `var_code`          VARCHAR(128) NOT NULL                COMMENT '变量编码',
  `var_label`         VARCHAR(128) NOT NULL                COMMENT '变量中文名称',
  `script_name`       VARCHAR(128) DEFAULT NULL             COMMENT '脚本中的变量名（默认驼峰）',
  `var_type`          VARCHAR(32)  NOT NULL                COMMENT '数据类型：STRING/NUMBER/BOOLEAN/DATE/ENUM/OBJECT/LIST/MAP',
  `var_source`        VARCHAR(32)  NOT NULL DEFAULT 'INPUT' COMMENT '来源：INPUT/COMPUTED/CONSTANT/DB/API',
  `default_value`     TEXT         DEFAULT NULL             COMMENT '默认值（常量必填，可为较长 JSON）',
  `value_range`       VARCHAR(512) DEFAULT NULL             COMMENT '取值范围描述',
  `example_value`     VARCHAR(256) DEFAULT NULL             COMMENT '示例值',
  `description`       VARCHAR(512) DEFAULT NULL             COMMENT '变量说明',
  `sort_order`        INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  `status`            TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_var` (`project_id`, `var_code`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_var_source` (`var_source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则变量表（普通变量与常量）';

-- ============================================================
-- 10. rule_variable_option - 规则变量选项表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_variable_option` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `variable_id`  BIGINT       NOT NULL                COMMENT '所属变量ID',
  `option_value` VARCHAR(256) NOT NULL                COMMENT '选项值',
  `option_label` VARCHAR(256) NOT NULL                COMMENT '选项中文标签',
  `sort_order`   INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  PRIMARY KEY (`id`),
  KEY `idx_variable_id` (`variable_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则变量选项表（枚举变量的可选值）';

-- ============================================================
-- 11. rule_function - 自定义函数定义表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_function` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`    BIGINT       NOT NULL                COMMENT '所属项目ID',
  `func_code`     VARCHAR(128) NOT NULL                COMMENT '函数编码（QLExpress 中的函数名）',
  `func_name`     VARCHAR(128) NOT NULL                COMMENT '函数中文名称',
  `description`   VARCHAR(512) DEFAULT NULL             COMMENT '函数说明',
  `params_json`   TEXT         DEFAULT NULL             COMMENT '参数定义JSON [{"name":"a","type":"NUMBER","label":"金额"}]',
  `return_type`   VARCHAR(32)  DEFAULT 'STRING'         COMMENT '返回值类型：STRING/NUMBER/BOOLEAN/OBJECT',
  `impl_type`     VARCHAR(16)  NOT NULL DEFAULT 'SCRIPT' COMMENT '实现方式：SCRIPT-QLExpress脚本/JAVA-Java类/BEAN-Spring Bean',
  `impl_script`   TEXT         DEFAULT NULL             COMMENT 'QLExpress 实现脚本（SCRIPT 类型）',
  `impl_class`    VARCHAR(256) DEFAULT NULL             COMMENT 'Java 实现类全限定名（JAVA 类型）',
  `impl_method`   VARCHAR(128) DEFAULT NULL             COMMENT '方法名（JAVA/BEAN 类型时指定）',
  `impl_bean_name` VARCHAR(128) DEFAULT NULL            COMMENT 'Spring Bean 名称（BEAN 类型时指定）',
  `status`        TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_func` (`project_id`, `func_code`),
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自定义函数定义表';

-- ============================================================
-- 12. rule_execution_log - 规则执行日志表（按月RANGE分区）
-- ============================================================
-- 先删除原有分区（如果是修改现有表）
-- ALTER TABLE rule_execution_log REMOVE PARTITIONING;
CREATE TABLE IF NOT EXISTS `rule_execution_log` (
   `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
   `rule_code`       VARCHAR(128)  NOT NULL                COMMENT '规则编码',
   `project_code`    VARCHAR(128)  DEFAULT NULL             COMMENT '项目编码',
   `rule_version`    INT           DEFAULT NULL             COMMENT '规则版本号',
   `model_type`      VARCHAR(16)   DEFAULT NULL             COMMENT '决策模型类型',
   `source`          VARCHAR(32)   NOT NULL DEFAULT 'SERVER' COMMENT '来源：SERVER-服务端测试，CLIENT-客户端执行',
   `client_app_name` VARCHAR(128)  DEFAULT NULL             COMMENT '客户端应用名称',
   `client_ip`       VARCHAR(64)   DEFAULT NULL             COMMENT '客户端IP',
   `input_params`    TEXT          DEFAULT NULL             COMMENT '输入参数（JSON）',
   `output_result`   TEXT          DEFAULT NULL             COMMENT '输出结果（JSON）',
   `trace_info`      LONGTEXT      DEFAULT NULL             COMMENT '表达式追踪树（JSON）',
   `success`         TINYINT       NOT NULL DEFAULT 1       COMMENT '执行结果：0-失败，1-成功',
   `error_message`   VARCHAR(1024) DEFAULT NULL             COMMENT '错误信息',
   `execute_time_ms` BIGINT        DEFAULT NULL             COMMENT '执行耗时（毫秒）',
   `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
   PRIMARY KEY (`id`, `create_time`),
   KEY `idx_rule_code` (`rule_code`, `create_time`),
   KEY `idx_project_code` (`project_code`, `create_time`),
   KEY `idx_source` (`source`, `create_time`),
   KEY `idx_client_app` (`client_app_name`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    COMMENT='规则执行日志表（按月分区）'
    PARTITION BY RANGE (TO_DAYS(`create_time`)) (
        -- 2026年
        PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')) COMMENT '2026年01月',
        PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')) COMMENT '2026年02月',
        PARTITION p202603 VALUES LESS THAN (TO_DAYS('2026-04-01')) COMMENT '2026年03月',
        PARTITION p202604 VALUES LESS THAN (TO_DAYS('2026-05-01')) COMMENT '2026年04月',
        PARTITION p202605 VALUES LESS THAN (TO_DAYS('2026-06-01')) COMMENT '2026年05月',
        PARTITION p202606 VALUES LESS THAN (TO_DAYS('2026-07-01')) COMMENT '2026年06月',
        PARTITION p202607 VALUES LESS THAN (TO_DAYS('2026-08-01')) COMMENT '2026年07月',
        PARTITION p202608 VALUES LESS THAN (TO_DAYS('2026-09-01')) COMMENT '2026年08月',
        PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01')) COMMENT '2026年09月',
        PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')) COMMENT '2026年10月',
        PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')) COMMENT '2026年11月',
        PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01')) COMMENT '2026年12月',
        -- 2027年
        PARTITION p202701 VALUES LESS THAN (TO_DAYS('2027-02-01')) COMMENT '2027年01月',
        PARTITION p202702 VALUES LESS THAN (TO_DAYS('2027-03-01')) COMMENT '2027年02月',
        PARTITION p202703 VALUES LESS THAN (TO_DAYS('2027-04-01')) COMMENT '2027年03月',
        PARTITION p202704 VALUES LESS THAN (TO_DAYS('2027-05-01')) COMMENT '2027年04月',
        PARTITION p202705 VALUES LESS THAN (TO_DAYS('2027-06-01')) COMMENT '2027年05月',
        PARTITION p202706 VALUES LESS THAN (TO_DAYS('2027-07-01')) COMMENT '2027年06月',
        PARTITION p202707 VALUES LESS THAN (TO_DAYS('2027-08-01')) COMMENT '2027年07月',
        PARTITION p202708 VALUES LESS THAN (TO_DAYS('2027-09-01')) COMMENT '2027年08月',
        PARTITION p202709 VALUES LESS THAN (TO_DAYS('2027-10-01')) COMMENT '2027年09月',
        PARTITION p202710 VALUES LESS THAN (TO_DAYS('2027-11-01')) COMMENT '2027年10月',
        PARTITION p202711 VALUES LESS THAN (TO_DAYS('2027-12-01')) COMMENT '2027年11月',
        PARTITION p202712 VALUES LESS THAN (TO_DAYS('2028-01-01')) COMMENT '2027年12月',
        -- 2028年
        PARTITION p202801 VALUES LESS THAN (TO_DAYS('2028-02-01')) COMMENT '2028年01月',
        PARTITION p202802 VALUES LESS THAN (TO_DAYS('2028-03-01')) COMMENT '2028年02月',
        PARTITION p202803 VALUES LESS THAN (TO_DAYS('2028-04-01')) COMMENT '2028年03月',
        PARTITION p202804 VALUES LESS THAN (TO_DAYS('2028-05-01')) COMMENT '2028年04月',
        PARTITION p202805 VALUES LESS THAN (TO_DAYS('2028-06-01')) COMMENT '2028年05月',
        PARTITION p202806 VALUES LESS THAN (TO_DAYS('2028-07-01')) COMMENT '2028年06月',
        PARTITION p202807 VALUES LESS THAN (TO_DAYS('2028-08-01')) COMMENT '2028年07月',
        PARTITION p202808 VALUES LESS THAN (TO_DAYS('2028-09-01')) COMMENT '2028年08月',
        PARTITION p202809 VALUES LESS THAN (TO_DAYS('2028-10-01')) COMMENT '2028年09月',
        PARTITION p202810 VALUES LESS THAN (TO_DAYS('2028-11-01')) COMMENT '2028年10月',
        PARTITION p202811 VALUES LESS THAN (TO_DAYS('2028-12-01')) COMMENT '2028年11月',
        PARTITION p202812 VALUES LESS THAN (TO_DAYS('2029-01-01')) COMMENT '2028年12月',
        -- 2029年
        PARTITION p202901 VALUES LESS THAN (TO_DAYS('2029-02-01')) COMMENT '2029年01月',
        PARTITION p202902 VALUES LESS THAN (TO_DAYS('2029-03-01')) COMMENT '2029年02月',
        PARTITION p202903 VALUES LESS THAN (TO_DAYS('2029-04-01')) COMMENT '2029年03月',
        PARTITION p202904 VALUES LESS THAN (TO_DAYS('2029-05-01')) COMMENT '2029年04月',
        PARTITION p202905 VALUES LESS THAN (TO_DAYS('2029-06-01')) COMMENT '2029年05月',
        PARTITION p202906 VALUES LESS THAN (TO_DAYS('2029-07-01')) COMMENT '2029年06月',
        PARTITION p202907 VALUES LESS THAN (TO_DAYS('2029-08-01')) COMMENT '2029年07月',
        PARTITION p202908 VALUES LESS THAN (TO_DAYS('2029-09-01')) COMMENT '2029年08月',
        PARTITION p202909 VALUES LESS THAN (TO_DAYS('2029-10-01')) COMMENT '2029年09月',
        PARTITION p202910 VALUES LESS THAN (TO_DAYS('2029-11-01')) COMMENT '2029年10月',
        PARTITION p202911 VALUES LESS THAN (TO_DAYS('2029-12-01')) COMMENT '2029年11月',
        PARTITION p202912 VALUES LESS THAN (TO_DAYS('2030-01-01')) COMMENT '2029年12月',
        -- 2030年
        PARTITION p203001 VALUES LESS THAN (TO_DAYS('2030-02-01')) COMMENT '2030年01月',
        PARTITION p203002 VALUES LESS THAN (TO_DAYS('2030-03-01')) COMMENT '2030年02月',
        PARTITION p203003 VALUES LESS THAN (TO_DAYS('2030-04-01')) COMMENT '2030年03月',
        PARTITION p203004 VALUES LESS THAN (TO_DAYS('2030-05-01')) COMMENT '2030年04月',
        PARTITION p203005 VALUES LESS THAN (TO_DAYS('2030-06-01')) COMMENT '2030年05月',
        PARTITION p203006 VALUES LESS THAN (TO_DAYS('2030-07-01')) COMMENT '2030年06月',
        PARTITION p203007 VALUES LESS THAN (TO_DAYS('2030-08-01')) COMMENT '2030年07月',
        PARTITION p203008 VALUES LESS THAN (TO_DAYS('2030-09-01')) COMMENT '2030年08月',
        PARTITION p203009 VALUES LESS THAN (TO_DAYS('2030-10-01')) COMMENT '2030年09月',
        PARTITION p203010 VALUES LESS THAN (TO_DAYS('2030-11-01')) COMMENT '2030年10月',
        PARTITION p203011 VALUES LESS THAN (TO_DAYS('2030-12-01')) COMMENT '2030年11月',
        PARTITION p203012 VALUES LESS THAN (TO_DAYS('2031-01-01')) COMMENT '2030年12月',
        -- 2031年
        PARTITION p203101 VALUES LESS THAN (TO_DAYS('2031-02-01')) COMMENT '2031年01月',
        PARTITION p203102 VALUES LESS THAN (TO_DAYS('2031-03-01')) COMMENT '2031年02月',
        PARTITION p203103 VALUES LESS THAN (TO_DAYS('2031-04-01')) COMMENT '2031年03月',
        PARTITION p203104 VALUES LESS THAN (TO_DAYS('2031-05-01')) COMMENT '2031年04月',
        PARTITION p203105 VALUES LESS THAN (TO_DAYS('2031-06-01')) COMMENT '2031年05月',
        PARTITION p203106 VALUES LESS THAN (TO_DAYS('2031-07-01')) COMMENT '2031年06月',
        PARTITION p203107 VALUES LESS THAN (TO_DAYS('2031-08-01')) COMMENT '2031年07月',
        PARTITION p203108 VALUES LESS THAN (TO_DAYS('2031-09-01')) COMMENT '2031年08月',
        PARTITION p203109 VALUES LESS THAN (TO_DAYS('2031-10-01')) COMMENT '2031年09月',
        PARTITION p203110 VALUES LESS THAN (TO_DAYS('2031-11-01')) COMMENT '2031年10月',
        PARTITION p203111 VALUES LESS THAN (TO_DAYS('2031-12-01')) COMMENT '2031年11月',
        PARTITION p203112 VALUES LESS THAN (TO_DAYS('2032-01-01')) COMMENT '2031年12月',
        PARTITION p_future VALUES LESS THAN MAXVALUE              COMMENT '兜底分区'
        );
