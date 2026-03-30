package com.bjjw.rule.model.dto;

import lombok.Data;

@Data
public class ParsedConstant {
    private String constCode;
    private String constLabel;
    /** 脚本中的常量名 */
    private String scriptName;
    private String constType;
    private String constValue;
}
