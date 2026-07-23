package com.bjjw.rule.server.controller.mgmt;

import com.bjjw.rule.model.dto.RuleSysDictItemDTO;
import com.bjjw.rule.model.dto.mgmt.RuleSysDictItemsRequest;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.RuleSysDictService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 系统字典接口：供管理端拉取下拉等配置项。
 */
@Slf4j
@RestController
@RequestMapping("/api/rule/dict")
public class RuleSysDictController {

    @Resource
    private RuleSysDictService ruleSysDictService;

    /**
     * 按字典类型查询启用项（POST + JSON），例如 type=COMP_SCOPE。
     */
    @PostMapping("/items")
    public R<List<RuleSysDictItemDTO>> items(@RequestBody RuleSysDictItemsRequest req) {
        return R.ok(ruleSysDictService.listEnabledByType(req.getType()));
    }
}
