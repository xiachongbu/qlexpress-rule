package com.bjjw.rule.model.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ParsedConstantGroup {
    private String groupCode;
    private String groupLabel;
    /** 脚本中的常量组引用名 */
    private String scriptName;
    private List<ParsedConstant> constants = new ArrayList<>();
}
