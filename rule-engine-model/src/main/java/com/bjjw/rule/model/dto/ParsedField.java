package com.bjjw.rule.model.dto;

import lombok.Data;

@Data
public class ParsedField {
    private String fieldName;
    private String fieldLabel;
    /** 脚本中的字段名 */
    private String scriptName;
    private String varType;
    private String refObjectCode;
    private String genericType;
}
