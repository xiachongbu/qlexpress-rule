package com.bjjw.rule.server.controller.mgmt;

import com.bjjw.rule.model.dto.RulePushMessage;
import com.bjjw.rule.model.dto.mgmt.RuleFunctionPageRequest;
import com.bjjw.rule.model.dto.mgmt.RuleFunctionProjectAllRequest;
import com.bjjw.rule.model.entity.RuleFunction;
import com.bjjw.rule.model.entity.RuleProject;
import com.bjjw.rule.server.common.Result;
import com.bjjw.rule.server.publish.RulePushService;
import com.bjjw.rule.server.service.RuleFunctionService;
import com.bjjw.rule.server.service.RuleProjectService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/rule/function")
public class RuleFunctionController {

    @Resource
    private RuleFunctionService functionService;

    @Resource
    private RulePushService pushService;

    @Resource
    private RuleProjectService projectService;

    /**
     * 按项目分页查询函数列表（POST + JSON）
     */
    @PostMapping("/project/{projectId}/list")
    public Result<IPage<RuleFunction>> listByProject(
            @PathVariable Long projectId,
            @RequestBody(required = false) RuleFunctionPageRequest req) {
        int pageNum = req == null || req.getPageNum() == null ? 1 : req.getPageNum();
        int pageSize = req == null || req.getPageSize() == null ? 20 : req.getPageSize();
        return Result.ok(functionService.pageByProject(projectId, pageNum, pageSize));
    }

    /**
     * 按项目查询全部启用函数（POST + JSON）
     */
    @PostMapping("/project/all")
    public Result<List<RuleFunction>> listAllByProject(@RequestBody RuleFunctionProjectAllRequest req) {
        return Result.ok(functionService.listByProject(req.getProjectId()));
    }

    @GetMapping("/{id}")
    public Result<RuleFunction> getById(@PathVariable Long id) {
        return Result.ok(functionService.getById(id));
    }

    @PostMapping
    public Result<Void> create(@RequestBody RuleFunction func) {
        functionService.create(func);
        pushFuncUpdate(func);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody RuleFunction func) {
        functionService.update(func);
        pushFuncUpdate(func);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        RuleFunction func = functionService.getById(id);
        functionService.delete(id);
        if (func != null) {
            RuleProject project = projectService.getById(func.getProjectId());
            RulePushMessage msg = new RulePushMessage();
            msg.setAction("FUNC_DELETE");
            msg.setFuncCode(func.getFuncCode());
            msg.setProjectCode(project.getProjectCode());
            msg.setPublishTime(System.currentTimeMillis());
            pushService.push(msg);
        }
        return Result.ok();
    }

    /** 推送函数新增/更新消息 */
    private void pushFuncUpdate(RuleFunction func) {
        RuleProject project = projectService.getById(func.getProjectId());
        RulePushMessage msg = new RulePushMessage();
        msg.setAction("FUNC_UPDATE");
        msg.setFuncCode(func.getFuncCode());
        msg.setFuncName(func.getFuncName());
        msg.setFuncImplType(func.getImplType());
        msg.setFuncImplScript(func.getImplScript());
        msg.setFuncImplClass(func.getImplClass());
        msg.setFuncImplMethod(func.getImplMethod());
        msg.setFuncImplBeanName(func.getImplBeanName());
        msg.setFuncParamsJson(func.getParamsJson());
        msg.setProjectCode(project.getProjectCode());
        msg.setPublishTime(System.currentTimeMillis());
        pushService.push(msg);
    }
}
