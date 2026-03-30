package com.bjjw.rule.server.service;

import com.bjjw.rule.model.entity.RuleProject;
import com.bjjw.rule.server.mapper.RuleProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@Service
public class RuleProjectService extends ServiceImpl<RuleProjectMapper, RuleProject> {

    public IPage<RuleProject> pageList(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<RuleProject> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(RuleProject::getProjectName, keyword)
                   .or()
                   .like(RuleProject::getProjectCode, keyword);
        }
        wrapper.orderByDesc(RuleProject::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }
    
    /**
     * 创建项目并自动生成AccessToken
     */
    @Transactional
    public String createProjectWithToken(RuleProject project) {
        // 生成UUID Token
        String token = UUID.randomUUID().toString().replace("-", "");
        
        // 直接存储明文Token
        project.setAccessToken(token);
        
        // 保存项目
        save(project);
        
        log.info("Created project with access token: {}", project.getProjectCode());
        return token;
    }
    
    /**
     * 根据Token验证并返回项目
     */
    public RuleProject validateToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        
        LambdaQueryWrapper<RuleProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleProject::getAccessToken, token);
        RuleProject project = getOne(wrapper);
        
        if (project == null) {
            log.warn("Token not found for any project");
            return null;
        }
        
        // 检查项目状态
        if (project.getStatus() == null || project.getStatus() != 1) {
            log.warn("Project is disabled: {}", project.getProjectCode());
            return null;
        }
        
        return project;
    }
    
    /**
     * 获取Token脱敏显示
     */
    public String getMaskedToken(Long projectId) {
        RuleProject project = getById(projectId);
        if (project == null || !StringUtils.hasText(project.getAccessToken())) {
            return null;
        }
        return maskToken(project.getAccessToken());
    }
    
    /**
     * Token脱敏显示
     */
    private String maskToken(String token) {
        if (!StringUtils.hasText(token) || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
}
