package com.bjjw.rule.server.controller.mgmt;

import com.bjjw.rule.model.entity.RuleProject;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.RuleProjectService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rule/project")
public class RuleProjectController {

    @Resource
    private RuleProjectService projectService;

    @GetMapping("/list")
    public R<IPage<RuleProject>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return R.ok(projectService.pageList(pageNum, pageSize, keyword));
    }

    @GetMapping("/{id}")
    public R<RuleProject> get(@PathVariable Long id) {
        return R.ok(projectService.getById(id));
    }

    @PostMapping
    public R<Map<String, Object>> create(@RequestBody RuleProject project) {
        String token = projectService.createProjectWithToken(project);
        Map<String, Object> result = new HashMap<>();
        result.put("project", project);
        result.put("accessToken", token);
        return R.ok(result);
    }

    @PutMapping
    public R<Void> update(@RequestBody RuleProject project) {
        projectService.updateById(project);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        projectService.removeById(id);
        return R.ok();
    }
    
    /**
     * 获取项目Token脱敏显示
     */
    @GetMapping("/{id}/token/masked")
    public R<String> getMaskedToken(@PathVariable Long id) {
        String maskedToken = projectService.getMaskedToken(id);
        return R.ok(maskedToken);
    }
}
