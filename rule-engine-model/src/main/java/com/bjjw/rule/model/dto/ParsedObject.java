package com.bjjw.rule.model.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ParsedObject {
    private String objectCode;
    private String objectLabel;
    /** 脚本中的对象引用名 */
    private String scriptName;
    private List<ParsedField> fields = new ArrayList<>();
    private List<ParsedObject> nestedObjects = new ArrayList<>();
}
