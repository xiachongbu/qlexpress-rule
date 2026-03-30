<template>
  <div class="uiue-list-page">
    <div class="linkage-hint">
      <i class="el-icon-info" /> 变量在规则设计时使用，<router-link to="/test">规则测试</router-link>中可加载项目变量作为入参。支持从 Java 实体类、JSON、建表 DDL 批量导入。
    </div>

    <!-- Toolbar -->
    <div class="var-toolbar">
      <el-select v-model="currentProjectId" placeholder="选择项目" size="small" style="width:200px;" clearable @change="onProjectChange">
        <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
      </el-select>
      <div class="toolbar-right">
        <el-dropdown trigger="click" @command="handleImportCmd" :disabled="!currentProjectId">
          <el-button size="small" type="primary" icon="el-icon-upload2">批量导入 <i class="el-icon-arrow-down el-icon--right" /></el-button>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item command="java-entity" icon="el-icon-document">导入 Java 实体类</el-dropdown-item>
            <el-dropdown-item command="json-object" icon="el-icon-tickets">导入 JSON 对象</el-dropdown-item>
            <el-dropdown-item command="ddl-table" icon="el-icon-s-grid">导入 DDL 建表语句</el-dropdown-item>
            <el-dropdown-item command="java-const" icon="el-icon-coin" divided>导入 Java 常量类</el-dropdown-item>
            <el-dropdown-item command="json-const" icon="el-icon-price-tag">导入 JSON 常量</el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
        <el-button size="small" icon="el-icon-plus" @click="handlePrimaryCreate" :disabled="!currentProjectId">{{ primaryCreateLabel }}</el-button>
        <el-button size="small" icon="el-icon-video-play" type="warning" @click="handleBatchValidate" :disabled="!currentProjectId" :loading="validating">验证规则</el-button>
      </div>
    </div>

    <!-- Tabs -->
    <el-tabs v-model="activeTab" type="border-card" class="var-tabs">

      <!-- Tab 1: Variable List -->
      <el-tab-pane label="变量列表" name="list">
        <div class="tab-filter-row">
          <el-select v-model="qp.varType" clearable placeholder="数据类型" size="mini" style="width:110px;" @change="handleQuery">
            <el-option v-for="opt in varTypeFilterOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-input v-model="qp.keyword" placeholder="搜索编码或名称" size="mini" clearable style="width:180px;" @keyup.enter.native="handleQuery" />
          <el-button size="mini" type="primary" @click="handleQuery">查询</el-button>
          <el-button size="mini" @click="resetQuery">重置</el-button>
        </div>

        <!-- 1. 普通变量（系统新增） -->
        <div v-if="standaloneVars.length > 0" class="var-list-section">
          <div class="section-title">普通变量</div>
          <el-table :data="standaloneVars" border size="small" v-loading="loading" style="width:100%;">
            <el-table-column prop="varCode" label="变量编码" min-width="130" show-overflow-tooltip />
            <el-table-column prop="varLabel" label="名称（中文）" min-width="120" show-overflow-tooltip />
            <el-table-column label="脚本名称" min-width="130">
              <template slot-scope="{row}">
                <el-input v-model="row.scriptName" size="mini" placeholder="脚本名称" @blur="onVarScriptNameChange(row)" />
              </template>
            </el-table-column>
            <el-table-column prop="varType" label="类型" min-width="80" align="center">
              <template slot-scope="{ row }"><el-tag size="mini" :type="typeTagColor(row.varType)">{{ typeLabel(row.varType) }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="varSource" label="来源" min-width="80" align="center">
              <template slot-scope="{ row }">
                <el-tag size="mini" :type="sourceTagColor(row.varSource)">{{ sourceLabel(row.varSource) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="defaultValue" label="默认值" min-width="90" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" min-width="60" align="center">
              <template slot-scope="{ row }"><el-tag :type="row.status===1?'success':'info'" size="mini">{{ row.status===1?'启用':'停用' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" min-width="140" align="center">
              <template slot-scope="{ row }">
                <el-button type="text" size="small" @click="handleEdit(row)">编辑</el-button>
                <el-button type="text" size="small" @click="handleOptions(row)" v-if="row.varType==='ENUM'">选项</el-button>
                <el-button type="text" size="small" style="color:#F56C6C;" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination style="margin-top:12px;text-align:right;" :current-page="qp.pageNum" :page-size="qp.pageSize" :total="standaloneTotal"
            layout="total,sizes,prev,pager,next" :page-sizes="[10,30,50,100,200,500]"
            @current-change="p=>{qp.pageNum=p;loadData()}" @size-change="s=>{qp.pageSize=s;qp.pageNum=1;loadData()}" />
        </div>

        <!-- 空状态 -->
        <div v-if="!loading && standaloneVars.length===0" class="tab-empty">
          <template v-if="!currentProjectId">请先在顶部选择一个项目</template>
          <template v-else>暂无变量，可点击「新建变量」或「批量导入」添加</template>
        </div>
      </el-tab-pane>

      <!-- Tab 2: Data Objects -->
      <el-tab-pane label="数据对象" name="objects">
        <div v-if="!currentProjectId" class="tab-empty">请先在顶部选择一个项目</div>
        <div v-else-if="objectTree.length===0 && !objLoading" class="tab-empty">暂无数据对象，点击「批量导入」导入 Java、JSON 或 DDL</div>
        <div v-loading="objLoading" v-else>
          <div v-for="node in paginatedObjectTree" :key="node.object.id" class="var-group-card">
            <div class="var-group-header" @click="toggleObjectExpand(node)">
              <i :class="node._expanded ? 'el-icon-arrow-down' : 'el-icon-arrow-right'" class="expand-icon" />
              <span class="var-group-code">{{ node.object.objectCode }}</span>
              <span v-if="node.object.objectLabel && node.object.objectLabel !== node.object.objectCode" class="var-group-label">{{ node.object.objectLabel }}</span>
              <el-input v-model="node.object.scriptName" size="mini" placeholder="脚本名称" style="width:130px;margin-left:6px;" @blur="onObjectScriptNameChange(node.object)" @click.native.stop />
              <el-select v-model="node.object.objectType" size="mini" style="width:100px;" @change="onObjectTypeChange(node.object)" @click.native.stop>
                <el-option label="输入对象" value="INPUT" /><el-option label="输出对象" value="OUTPUT" /><el-option label="输入输出" value="INOUT" />
              </el-select>
              <el-tag size="mini" :type="objTypeColor(node.object.objectType)">{{ objTypeLabel(node.object.objectType) }}</el-tag>
              <el-tag size="mini" type="info" v-if="node.object.sourceType">{{ node.object.sourceType }}</el-tag>
              <span class="var-group-count">{{ node.variables.length }} 个字段</span>
              <el-button type="text" size="small" icon="el-icon-plus" style="margin-left:auto;" @click.stop="handleAddObjectField(node)">添加字段</el-button>
              <el-button type="text" size="small" icon="el-icon-delete" style="color:#F56C6C;" @click.stop="handleDeleteObject(node.object)" />
            </div>
            <div v-show="node._expanded" class="var-group-body">
              <el-table :data="node.variables" size="mini" border style="width:100%;">
                <el-table-column prop="varCode" label="字段编码" min-width="140" show-overflow-tooltip />
                <el-table-column prop="varLabel" label="名称" min-width="120" show-overflow-tooltip />
                <el-table-column label="脚本名称" min-width="140">
                  <template slot-scope="{row}">
                    <el-input v-model="row.scriptName" size="mini" placeholder="脚本名称" @blur="onObjectFieldScriptNameBlur(row)" />
                  </template>
                </el-table-column>
                <el-table-column prop="varType" label="类型" min-width="80" align="center">
                  <template slot-scope="{row}"><el-tag size="mini" :type="typeTagColor(row.varType)">{{ typeLabel(row.varType) }}</el-tag></template>
                </el-table-column>
                <el-table-column prop="refObjectCode" label="引用对象" min-width="110" show-overflow-tooltip>
                  <template slot-scope="{row}"><span v-if="row.refObjectCode" class="badge badge-obj">{{ row.refObjectCode }}</span><span v-else style="color:#ccc;">—</span></template>
                </el-table-column>
                <el-table-column label="操作" width="140" align="center">
                  <template slot-scope="{ row }">
                    <el-button type="text" size="small" @click="handleEditObjectField(row, node)">编辑</el-button>
                    <el-button type="text" size="small" @click="handleOptions(row, true)" v-if="row.varType==='ENUM'">选项</el-button>
                    <el-button type="text" size="small" style="color:#F56C6C;" @click="handleDeleteObjectField(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
          <el-pagination style="margin-top:12px;text-align:right;" :current-page="objPageNum" :page-size="objPageSize" :total="objectTree.length"
            layout="total,prev,pager,next" @current-change="handleObjPageChange" />
        </div>
      </el-tab-pane>

      <!-- Tab 3: 常量列表（与变量列表相同分页模型，必须有默认值） -->
      <el-tab-pane label="常量列表" name="constants">
        <div class="tab-filter-row" v-if="currentProjectId">
          <el-select v-model="constQp.varType" clearable placeholder="数据类型" size="mini" style="width:110px;" @change="handleConstQuery">
            <el-option v-for="opt in varTypeFilterOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-input v-model="constQp.keyword" placeholder="搜索编码或名称" size="mini" clearable style="width:180px;" @keyup.enter.native="handleConstQuery" />
          <el-button size="mini" type="primary" @click="handleConstQuery">查询</el-button>
          <el-button size="mini" @click="resetConstQuery">重置</el-button>
        </div>
        <div v-if="!currentProjectId" class="tab-empty">请先在顶部选择一个项目</div>
        <div v-else-if="constantRows.length===0 && !constLoading" class="tab-empty">暂无常量，可点击「新建常量」或「批量导入」添加</div>
        <div v-loading="constLoading" v-else>
          <el-table :data="constantRows" border size="small" style="width:100%;">
            <el-table-column prop="varCode" label="常量编码" min-width="130" show-overflow-tooltip />
            <el-table-column prop="varLabel" label="名称" min-width="120" show-overflow-tooltip />
            <el-table-column label="脚本名称" min-width="130">
              <template slot-scope="{row}">
                <el-input v-model="row.scriptName" size="mini" placeholder="脚本名称" @blur="onVarScriptNameChange(row)" />
              </template>
            </el-table-column>
            <el-table-column prop="varType" label="类型" min-width="80" align="center">
              <template slot-scope="{ row }"><el-tag size="mini" :type="typeTagColor(row.varType)">{{ typeLabel(row.varType) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="常量值（默认）" min-width="160">
              <template slot-scope="{row}">
                <el-input v-model="row.defaultValue" size="mini" @blur="onConstDefaultBlur(row)" />
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" min-width="60" align="center">
              <template slot-scope="{ row }"><el-tag :type="row.status===1?'success':'info'" size="mini">{{ row.status===1?'启用':'停用' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" min-width="120" align="center">
              <template slot-scope="{ row }">
                <el-button type="text" size="small" @click="handleEdit(row)">编辑</el-button>
                <el-button type="text" size="small" style="color:#F56C6C;" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination style="margin-top:12px;text-align:right;" :current-page="constQp.pageNum" :page-size="constQp.pageSize" :total="constantTotal"
            layout="total,sizes,prev,pager,next" :page-sizes="[10,30,50,100]"
            @current-change="p=>{constQp.pageNum=p;loadConstants()}" @size-change="s=>{constQp.pageSize=s;constQp.pageNum=1;loadConstants()}" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- Create/Edit Variable Dialog -->
    <el-dialog :title="variableDialogTitle" :visible.sync="dialogVisible" width="600px" :close-on-click-modal="false">
      <el-form ref="form" :model="form" :rules="rules" label-width="120px" size="small">
        <el-form-item v-if="!form.id && isObjectField && objectFieldParentId" label="所属数据对象">
          <span class="text-muted">{{ getObjectCode(objectFieldParentId) }}</span>
        </el-form-item>
        <el-form-item label="变量编码" prop="varCode">
          <el-input v-model="form.varCode" placeholder="英文标识，如 taxAmount" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="变量名称" prop="varLabel">
          <el-input v-model="form.varLabel" placeholder="中文名称，如 应纳税额" />
        </el-form-item>
        <el-form-item label="数据类型" prop="varType">
          <el-select v-model="form.varType" style="width:100%;" popper-append-to-body>
            <el-option v-for="opt in varTypeFormOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isObjectField" label="来源">
          <el-select v-model="form.varSource" :disabled="isConstantCreate" clearable placeholder="可选" style="width:100%;">
            <el-option label="输入参数" value="INPUT" /><el-option label="数据库查询" value="DB" />
            <el-option label="接口调用" value="API" /><el-option label="计算得出" value="COMPUTED" /><el-option label="常量" value="CONSTANT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isObjectField" label="默认值">
          <el-input v-model="form.defaultValue" :placeholder="form.varSource==='CONSTANT' ? '常量必填' : '可选'" />
        </el-form-item>
        <el-form-item v-if="isObjectField && (form.varType==='OBJECT' || form.varType==='LIST')" label="引用对象编码">
          <el-input v-model="form.refObjectCode" placeholder="如嵌套对象编码" />
        </el-form-item>
        <el-form-item v-if="!isObjectField" label="取值范围"><el-input v-model="form.valueRange" placeholder="如：0~100、A/B/C" /></el-form-item>
        <el-form-item v-if="!isObjectField" label="示例值"><el-input v-model="form.exampleValue" placeholder="如：15000.50" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" :max="9999" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item>
        <el-form-item v-if="!isObjectField" label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="dialogVisible=false">取消</el-button>
        <el-button size="small" type="primary" @click="handleSubmit">确定</el-button>
      </div>
    </el-dialog>

    <!-- Enum Options Dialog -->
    <el-dialog title="枚举选项管理" :visible.sync="optionDialogVisible" width="600px" :close-on-click-modal="false">
      <div style="margin-bottom:12px;">
        <span style="font-weight:bold;">{{ currentVar ? currentVar.varLabel : '' }}</span>
        <span style="color:#999;margin-left:8px;">{{ currentVar ? currentVar.varCode : '' }}</span>
      </div>
      <el-table :data="optionList" border size="small" style="width:100%;">
        <el-table-column label="选项值" min-width="160">
          <template slot-scope="{row}"><el-input v-model="row.optionValue" size="mini" placeholder="选项值" /></template>
        </el-table-column>
        <el-table-column label="选项标签（中文）" min-width="180">
          <template slot-scope="{row}"><el-input v-model="row.optionLabel" size="mini" placeholder="中文标签" /></template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template slot-scope="{$index}"><el-button type="text" size="small" style="color:#F56C6C;" @click="optionList.splice($index,1)">移除</el-button></template>
        </el-table-column>
      </el-table>
      <el-button type="text" size="small" icon="el-icon-plus" style="margin-top:8px;" @click="optionList.push({optionValue:'',optionLabel:'',sortOrder:optionList.length})">添加选项</el-button>
      <div slot="footer">
        <el-button size="small" @click="optionDialogVisible=false">取消</el-button>
        <el-button size="small" type="primary" @click="handleSaveOptions">保存选项</el-button>
      </div>
    </el-dialog>

    <!-- Java Entity Import Dialog -->
    <el-dialog title="导入 Java 实体类" :visible.sync="importJavaEntityVisible" width="700px" :close-on-click-modal="false">
      <el-form size="small" label-width="100px">
        <el-form-item label="对象类型">
          <el-radio-group v-model="importForm.objectType">
            <el-radio label="INPUT">输入对象</el-radio><el-radio label="OUTPUT">输出对象</el-radio><el-radio label="INOUT">输入输出</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="Java 源码">
          <el-input v-model="importForm.javaSource" type="textarea" :rows="14" placeholder="粘贴 Java 实体类源码，支持多个 class 定义..." style="font-family:Consolas,monospace;" />
        </el-form-item>
        <el-form-item label="或上传文件">
          <el-upload action="" :before-upload="handleJavaFileSelect" :show-file-list="false" accept=".java">
            <el-button size="small" icon="el-icon-upload">选择 .java 文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="importJavaEntityVisible=false">取消</el-button>
        <el-button size="small" type="primary" :loading="importing" @click="doImportJavaEntity">导入</el-button>
      </div>
    </el-dialog>

    <!-- JSON Object Import Dialog -->
    <el-dialog title="导入 JSON 对象" :visible.sync="importJsonObjectVisible" width="700px" :close-on-click-modal="false">
      <el-form size="small" label-width="100px">
        <el-form-item label="对象编码"><el-input v-model="importForm.objectCode" placeholder="如 TaxRequest" /></el-form-item>
        <el-form-item label="对象类型">
          <el-radio-group v-model="importForm.objectType">
            <el-radio label="INPUT">输入对象</el-radio><el-radio label="OUTPUT">输出对象</el-radio><el-radio label="INOUT">输入输出</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="JSON 样本">
          <el-input v-model="importForm.jsonContent" type="textarea" :rows="14" placeholder='粘贴 JSON 样本数据，如：{"name":"张三","age":30,"address":{"city":"北京"}}' style="font-family:Consolas,monospace;" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="importJsonObjectVisible=false">取消</el-button>
        <el-button size="small" type="primary" :loading="importing" @click="doImportJsonObject">导入</el-button>
      </div>
    </el-dialog>

    <!-- DDL Import Dialog -->
    <el-dialog title="导入 DDL 建表语句" :visible.sync="importDdlVisible" width="720px" :close-on-click-modal="false">
      <el-form size="small" label-width="100px">
        <el-form-item label="对象类型">
          <el-radio-group v-model="importForm.objectType">
            <el-radio label="INPUT">输入对象</el-radio><el-radio label="OUTPUT">输出对象</el-radio><el-radio label="INOUT">输入输出</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="建表 DDL">
          <el-input v-model="importForm.ddlSource" type="textarea" :rows="14" placeholder="粘贴 CREATE TABLE ... 语句；支持多张表。列 COMMENT 将解析为变量名称（中文名）。" style="font-family:Consolas,monospace;" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="importDdlVisible=false">取消</el-button>
        <el-button size="small" type="primary" :loading="importing" @click="doImportDdl">导入</el-button>
      </div>
    </el-dialog>

    <!-- Java Constants Import Dialog -->
    <el-dialog title="导入 Java 常量类" :visible.sync="importJavaConstVisible" width="700px" :close-on-click-modal="false">
      <el-form size="small" label-width="100px">
        <el-form-item label="Java 源码">
          <el-input v-model="importForm.javaSource" type="textarea" :rows="14" placeholder="粘贴包含 static final 字段的 Java 类源码..." style="font-family:Consolas,monospace;" />
        </el-form-item>
        <el-form-item label="或上传文件">
          <el-upload action="" :before-upload="handleJavaFileSelect" :show-file-list="false" accept=".java">
            <el-button size="small" icon="el-icon-upload">选择 .java 文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="importJavaConstVisible=false">取消</el-button>
        <el-button size="small" type="primary" :loading="importing" @click="doImportJavaConst">导入</el-button>
      </div>
    </el-dialog>

    <!-- JSON Constants Import Dialog -->
    <el-dialog title="导入 JSON 常量" :visible.sync="importJsonConstVisible" width="700px" :close-on-click-modal="false">
      <el-form size="small" label-width="100px">
        <el-form-item label="JSON 数据">
          <el-input v-model="importForm.jsonContent" type="textarea" :rows="14" placeholder='扁平 JSON 键值对，如：{"riskScore":72,"creditLevel":"A","channel":"ONLINE"}' style="font-family:Consolas,monospace;" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="importJsonConstVisible=false">取消</el-button>
        <el-button size="small" type="primary" :loading="importing" @click="doImportJsonConst">导入</el-button>
      </div>
    </el-dialog>

    <!-- Validation Results Dialog -->
    <el-dialog title="规则验证结果" :visible.sync="validateVisible" width="700px">
      <el-table :data="validateResults" size="small" border>
        <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="ruleCode" label="规则编码" min-width="120" show-overflow-tooltip />
        <el-table-column prop="modelType" label="模型" min-width="70" align="center" />
        <el-table-column label="编译" min-width="60" align="center">
          <template slot-scope="{row}"><el-tag :type="row.compileOk?'success':'danger'" size="mini">{{ row.compileOk?'通过':'失败' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="执行" min-width="60" align="center">
          <template slot-scope="{row}"><el-tag :type="row.executeOk?'success':'danger'" size="mini">{{ row.executeOk?'通过':'失败' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="错误信息" min-width="200" show-overflow-tooltip />
      </el-table>
      <div slot="footer"><el-button size="small" @click="validateVisible=false">关闭</el-button></div>
    </el-dialog>

    <!-- Import Result Dialog -->
    <el-dialog title="导入结果" :visible.sync="importResultVisible" width="500px">
      <div class="import-result-body">
        <i class="el-icon-success" style="font-size:48px;color:#67C23A;" />
        <h3>导入完成</h3>
        <p v-if="importResult.objectCount != null">创建/更新 <b>{{ importResult.objectCount }}</b> 个数据对象，<b>{{ importResult.variableCount }}</b> 个变量</p>
        <p v-if="importResult.constantCount != null">创建/更新 <b>{{ importResult.constantCount }}</b> 个常量</p>
      </div>
      <div slot="footer">
        <el-button size="small" type="warning" icon="el-icon-video-play" @click="importResultVisible=false;handleBatchValidate()">验证项目规则</el-button>
        <el-button size="small" @click="importResultVisible=false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listVariables, createVariable, updateVariable, deleteVariable, getVariableOptions, saveVariableOptions, importJavaConstants, importJsonConstants } from '@/api/variable'
import { listProjects } from '@/api/project'
import { importJavaEntity, importJsonObject, importDdlTable, getVariableTree, updateObjectType, updateObjectScriptName, deleteDataObject, batchValidateRules, createDataObjectField, updateDataObjectField, deleteDataObjectField, getDataObjectFieldOptions, saveDataObjectFieldOptions } from '@/api/dataObject'
import { VAR_TYPE_FILTER_OPTIONS, VAR_TYPE_FORM_OPTIONS, varTypeLabel, varTypeTagColor } from '@/constants/varTypes'

export default {
  name: 'VariableList',
  data() {
    return {
      activeTab: 'list',
      currentProjectId: '',
      projects: [],
      loading: false,
      tableData: [],
      total: 0,
      qp: { pageNum: 1, pageSize: 10, projectId: '', varType: '', keyword: '' },

      dialogVisible: false,
      form: this.initForm(),
      rules: {
        varCode: [{ required: true, message: '请输入变量编码', trigger: 'blur' }],
        varLabel: [{ required: true, message: '请输入变量名称', trigger: 'blur' }],
        varType: [{ required: true, message: '请选择数据类型', trigger: 'change' }]
      },

      optionDialogVisible: false,
      currentVar: null,
      optionList: [],

      // Data objects
      objLoading: false,
      objectTree: [],
      objectMap: {},

      // 常量列表（分页，与变量接口相同）
      constLoading: false,
      constQp: { pageNum: 1, pageSize: 10, keyword: '', varType: '' },
      constantRows: [],
      constantTotal: 0,

      /** 弹窗：编辑数据对象字段（非 rule_variable） */
      isObjectField: false,
      objectFieldParentId: null,
      /** 从常量 Tab 打开新建，锁定来源为 CONSTANT */
      isConstantCreate: false,
      /** 枚举选项弹窗：当前为对象字段上的 ENUM */
      optionTargetIsField: false,

      // Import
      importing: false,
      importForm: { objectType: 'INPUT', javaSource: '', jsonContent: '', ddlSource: '', objectCode: '' },
      importJavaEntityVisible: false,
      importJsonObjectVisible: false,
      importDdlVisible: false,
      importJavaConstVisible: false,
      importJsonConstVisible: false,
      importResultVisible: false,
      importResult: {},

      // Validation
      validating: false,
      validateVisible: false,
      validateResults: [],

      varTypeFilterOptions: VAR_TYPE_FILTER_OPTIONS,
      varTypeFormOptions: VAR_TYPE_FORM_OPTIONS,

      // 数据对象 tab 分页与展开
      objPageNum: 1,
      objPageSize: 10,
      objExpanded: {},

    }
  },
  created() {
    this.loadProjects()
  },
  computed: {
    standaloneVars() {
      return this.tableData || []
    },
    standaloneTotal() {
      return this.total
    },
    paginatedObjectTree() {
      const list = this.objectTree || []
      const start = (this.objPageNum - 1) * this.objPageSize
      return list.slice(start, start + this.objPageSize).map(n => ({
        ...n,
        _expanded: this.objExpanded[n.object.id] === true
      }))
    },
    primaryCreateLabel() {
      return this.activeTab === 'constants' ? '新建常量' : '新建变量'
    },
    /** 变量/对象字段/常量 弹窗标题 */
    variableDialogTitle() {
      if (this.isObjectField) return this.form.id ? '编辑对象字段' : '添加对象字段'
      if (this.form.id) return '编辑变量'
      if (this.isConstantCreate) return '新建常量'
      return '新建变量'
    }
  },
  watch: {
    activeTab(tab) {
      if (tab === 'objects' && this.currentProjectId) this.loadObjectTree()
      if (tab === 'constants' && this.currentProjectId) this.loadConstants()
    }
  },
  methods: {
    initForm() {
      return {
        id: null,
        projectId: '',
        varCode: '',
        varLabel: '',
        varType: 'STRING',
        varSource: 'INPUT',
        refObjectCode: '',
        defaultValue: '',
        valueRange: '',
        exampleValue: '',
        sortOrder: 0,
        status: 1,
        description: ''
      }
    },
    async loadProjects() {
      try {
        const res = await listProjects({ pageNum: 1, pageSize: 1000 })
        this.projects = (res.data && res.data.records) ? res.data.records : []
      } catch (e) { this.projects = [] }
    },
    onProjectChange(pid) {
      this.currentProjectId = pid
      this.qp.projectId = pid
      this.qp.pageNum = 1
      this.loadData()
      this.objExpanded = {}
      this.objPageNum = 1
      this.constQp.pageNum = 1
      if (this.activeTab === 'objects') this.loadObjectTree()
      if (this.activeTab === 'constants') this.loadConstants()
    },
    toggleObjectExpand(node) {
      this.$set(this.objExpanded, node.object.id, !this.objExpanded[node.object.id])
    },
    handleObjPageChange(p) { this.objPageNum = p },
    async loadData() {
      this.loading = true
      try {
        const params = { ...this.qp, standaloneOnly: true }
        if (!params.projectId) delete params.projectId
        if (!params.varType) delete params.varType
        if (!params.keyword) delete params.keyword
        const res = await listVariables(params)
        const data = res.data || {}
        this.tableData = data.records || []
        this.total = data.total != null ? data.total : 0
      } catch (err) {
        this.$message.error('加载变量列表失败')
        this.tableData = []; this.total = 0
      } finally { this.loading = false }
    },
    handleQuery() { this.qp.pageNum = 1; this.loadData() },
    resetQuery() { this.qp = { pageNum: 1, pageSize: this.qp.pageSize, projectId: this.currentProjectId, varType: '', keyword: '' }; this.loadData() },

    // ── Objects ──
    async loadObjectTree() {
      if (!this.currentProjectId) return
      this.objLoading = true
      try {
        const res = await getVariableTree(this.currentProjectId)
        this.objectTree = res.data || []
        this.objectMap = {}
        this.objectTree.forEach(n => { this.objectMap[n.object.id] = n.object })
      } catch (e) { this.objectTree = [] }
      finally { this.objLoading = false }
    },
    async onObjectTypeChange(obj) {
      await updateObjectType(obj.id, obj.objectType)
      this.$message.success('对象类型已更新')
    },
    async onObjectScriptNameChange(obj) {
      await updateObjectScriptName(obj.id, obj.scriptName)
    },
    /**
     * 列表行内修改脚本名：变量走 variable 接口；数据对象字段走 dataobject field 接口。
     */
    async onVarScriptNameChange(row) {
      if (row.objectField) {
        await this.onObjectFieldScriptNameBlur(row)
        return
      }
      await updateVariable(row)
    },
    /**
     * 数据对象表格中字段脚本名失焦保存。
     */
    async onObjectFieldScriptNameBlur(row) {
      await updateDataObjectField({
        id: row.id,
        projectId: row.projectId,
        objectId: row.objectId,
        varCode: row.varCode,
        varLabel: row.varLabel,
        scriptName: row.scriptName,
        varType: row.varType,
        refObjectCode: row.refObjectCode || null,
        sortOrder: row.sortOrder,
        status: row.status
      })
    },
    /**
     * 常量列表行内修改默认值后保存。
     */
    async onConstDefaultBlur(row) {
      if (!row.defaultValue || !String(row.defaultValue).trim()) {
        this.$message.warning('常量值不能为空')
        await this.loadConstants()
        return
      }
      await updateVariable({ ...row, varSource: 'CONSTANT' })
    },
    async handleDeleteObject(obj) {
      await this.$confirm(`确定删除对象「${obj.objectCode}」及其所有变量？`, '确认删除', { type: 'warning' })
      await deleteDataObject(obj.id)
      this.$message.success('删除成功')
      this.objPageNum = 1
      this.loadObjectTree()
      this.loadData()
    },
    /**
     * 在指定数据对象下新建字段（写入 rule_data_object_field）。
     */
    handleAddObjectField(node) {
      const obj = node.object
      const nextOrder = (node.variables && node.variables.length) ? node.variables.length : 0
      this.isObjectField = true
      this.isConstantCreate = false
      this.objectFieldParentId = obj.id
      this.form = {
        id: null,
        projectId: this.currentProjectId,
        varCode: '',
        varLabel: '',
        scriptName: '',
        varType: 'STRING',
        refObjectCode: '',
        sortOrder: nextOrder,
        status: 1
      }
      this.dialogVisible = true
      this.$nextTick(() => { if (this.$refs.form) this.$refs.form.clearValidate() })
    },
    /**
     * 编辑数据对象下的字段行。
     */
    handleEditObjectField(row, node) {
      this.isObjectField = true
      this.isConstantCreate = false
      this.objectFieldParentId = node.object.id
      this.form = {
        id: row.id,
        projectId: row.projectId,
        varCode: row.varCode,
        varLabel: row.varLabel,
        scriptName: row.scriptName,
        varType: row.varType || 'STRING',
        refObjectCode: row.refObjectCode || '',
        sortOrder: row.sortOrder != null ? row.sortOrder : 0,
        status: row.status != null ? row.status : 1
      }
      this.dialogVisible = true
      this.$nextTick(() => { if (this.$refs.form) this.$refs.form.clearValidate() })
    },
    /**
     * 删除数据对象字段。
     */
    async handleDeleteObjectField(row) {
      await this.$confirm(`确定删除字段「${row.varLabel || row.varCode}」？`, '确认删除', { type: 'warning' })
      await deleteDataObjectField(row.id)
      this.$message.success('删除成功')
      this.loadObjectTree()
    },
    getObjectCode(objectId) {
      const obj = this.objectMap[objectId]
      return obj ? obj.objectCode : objectId
    },

    // ── 常量列表（分页） ──
    async loadConstants() {
      if (!this.currentProjectId) return
      this.constLoading = true
      try {
        const params = {
          pageNum: this.constQp.pageNum,
          pageSize: this.constQp.pageSize,
          projectId: this.currentProjectId,
          varSource: 'CONSTANT'
        }
        if (this.constQp.keyword) params.keyword = this.constQp.keyword
        if (this.constQp.varType) params.varType = this.constQp.varType
        const res = await listVariables(params)
        const data = res.data || {}
        this.constantRows = data.records || []
        this.constantTotal = data.total != null ? data.total : 0
      } catch (e) {
        this.constantRows = []
        this.constantTotal = 0
      } finally { this.constLoading = false }
    },
    handleConstQuery() { this.constQp.pageNum = 1; this.loadConstants() },
    resetConstQuery() {
      this.constQp = { pageNum: 1, pageSize: this.constQp.pageSize, keyword: '', varType: '' }
      this.loadConstants()
    },

    // ── CRUD ──
    /**
     * 顶部「新建」：变量列表新建变量；常量列表新建常量。
     */
    handlePrimaryCreate() {
      if (this.activeTab === 'constants') {
        this.isConstantCreate = true
        this.isObjectField = false
        this.objectFieldParentId = null
        this.form = { ...this.initForm(), projectId: this.currentProjectId, varSource: 'CONSTANT', status: 1 }
        this.dialogVisible = true
        this.$nextTick(() => { if (this.$refs.form) this.$refs.form.clearValidate() })
        return
      }
      this.handleCreate()
    },
    handleCreate() {
      this.isConstantCreate = false
      this.isObjectField = false
      this.objectFieldParentId = null
      this.form = this.initForm()
      this.form.projectId = this.currentProjectId
      this.dialogVisible = true
      this.$nextTick(() => { if (this.$refs.form) this.$refs.form.clearValidate() })
    },
    handleEdit(row) {
      this.isObjectField = false
      this.isConstantCreate = false
      this.objectFieldParentId = null
      this.form = { ...this.initForm(), ...row }
      this.dialogVisible = true
      this.$nextTick(() => { if (this.$refs.form) this.$refs.form.clearValidate() })
    },
    handleSubmit() {
      this.$refs.form.validate(async (valid) => {
        if (!valid) return
        if (this.isObjectField) {
          if (!this.form.projectId) this.form.projectId = this.currentProjectId
          const payload = {
            id: this.form.id,
            projectId: this.form.projectId,
            objectId: this.objectFieldParentId,
            varCode: this.form.varCode,
            varLabel: this.form.varLabel,
            scriptName: this.form.scriptName,
            varType: this.form.varType,
            refObjectCode: this.form.refObjectCode || null,
            sortOrder: this.form.sortOrder,
            status: this.form.status
          }
          if (this.form.id) {
            await updateDataObjectField(payload)
          } else {
            await createDataObjectField(this.objectFieldParentId, payload)
          }
          this.$message.success('操作成功')
          this.dialogVisible = false
          this.isObjectField = false
          this.loadObjectTree()
          return
        }
        if (!this.form.projectId) this.form.projectId = this.currentProjectId
        if (this.form.varSource === 'CONSTANT') {
          if (!this.form.defaultValue || !String(this.form.defaultValue).trim()) {
            this.$message.warning('常量必须填写默认值')
            return
          }
        }
        const wasConstant = this.form.varSource === 'CONSTANT' || this.isConstantCreate
        if (this.form.id) { await updateVariable(this.form) } else { await createVariable(this.form) }
        this.$message.success('操作成功')
        this.dialogVisible = false
        this.isConstantCreate = false
        this.loadData()
        if (wasConstant) this.loadConstants()
      })
    },
    handleDelete(row) {
      this.$confirm(`确定删除「${row.varLabel}」？`, '确认删除', { type: 'warning' })
        .then(async () => {
          await deleteVariable(row.id)
          this.$message.success('删除成功')
          this.loadData()
          if (row.varSource === 'CONSTANT') this.loadConstants()
        }).catch(() => {})
    },
    /**
     * @param {boolean} isField - 是否为数据对象字段上的枚举
     */
    async handleOptions(row, isField) {
      this.currentVar = row
      this.optionTargetIsField = !!isField
      try {
        const res = isField ? await getDataObjectFieldOptions(row.id) : await getVariableOptions(row.id)
        this.optionList = res.data || []
      } catch (e) { this.optionList = [] }
      this.optionDialogVisible = true
    },
    async handleSaveOptions() {
      const invalid = this.optionList.some(o => !o.optionValue || !o.optionLabel)
      if (invalid) { this.$message.warning('请填写完整的选项值和标签'); return }
      if (this.optionTargetIsField) {
        await saveDataObjectFieldOptions(this.currentVar.id, this.optionList)
      } else {
        await saveVariableOptions(this.currentVar.id, this.optionList)
      }
      this.$message.success('保存成功')
      this.optionDialogVisible = false
    },

    // ── Import ──
    handleImportCmd(cmd) {
      this.importForm = { objectType: 'INPUT', javaSource: '', jsonContent: '', ddlSource: '', objectCode: '' }
      if (cmd === 'java-entity') this.importJavaEntityVisible = true
      else if (cmd === 'json-object') this.importJsonObjectVisible = true
      else if (cmd === 'ddl-table') this.importDdlVisible = true
      else if (cmd === 'java-const') this.importJavaConstVisible = true
      else if (cmd === 'json-const') this.importJsonConstVisible = true
    },
    handleJavaFileSelect(file) {
      const reader = new FileReader()
      reader.onload = (e) => { this.importForm.javaSource = e.target.result }
      reader.readAsText(file)
      return false
    },
    async doImportJavaEntity() {
      if (!this.importForm.javaSource.trim()) { this.$message.warning('请输入或上传 Java 源码'); return }
      this.importing = true
      try {
        const res = await importJavaEntity(this.currentProjectId, this.importForm.objectType, this.importForm.javaSource)
        this.importResult = res.data || {}
        this.importJavaEntityVisible = false
        this.importResultVisible = true
        this.loadData(); this.loadObjectTree()
      } catch (e) { this.$message.error('导入失败: ' + (e.message || '')) }
      finally { this.importing = false }
    },
    async doImportJsonObject() {
      if (!this.importForm.objectCode.trim()) { this.$message.warning('请输入对象编码'); return }
      if (!this.importForm.jsonContent.trim()) { this.$message.warning('请输入 JSON 内容'); return }
      this.importing = true
      try {
        const res = await importJsonObject(this.currentProjectId, this.importForm.objectType, this.importForm.objectCode, this.importForm.jsonContent)
        this.importResult = res.data || {}
        this.importJsonObjectVisible = false
        this.importResultVisible = true
        this.loadData(); this.loadObjectTree()
      } catch (e) { this.$message.error('导入失败: ' + (e.message || '')) }
      finally { this.importing = false }
    },
    /** 从 CREATE TABLE DDL 导入数据对象（COMMENT → 变量名称） */
    async doImportDdl() {
      if (!this.importForm.ddlSource || !this.importForm.ddlSource.trim()) { this.$message.warning('请输入建表 DDL'); return }
      this.importing = true
      try {
        const res = await importDdlTable(this.currentProjectId, this.importForm.objectType, this.importForm.ddlSource)
        this.importResult = res.data || {}
        this.importDdlVisible = false
        this.importResultVisible = true
        this.loadData(); this.loadObjectTree()
      } catch (e) { this.$message.error('导入失败: ' + (e.message || '')) }
      finally { this.importing = false }
    },
    async doImportJavaConst() {
      if (!this.importForm.javaSource.trim()) { this.$message.warning('请输入或上传 Java 源码'); return }
      this.importing = true
      try {
        const res = await importJavaConstants(this.currentProjectId, this.importForm.javaSource)
        this.importResult = res.data || {}
        this.importJavaConstVisible = false
        this.importResultVisible = true
        this.loadData(); this.loadConstants()
      } catch (e) { this.$message.error('导入失败: ' + (e.message || '')) }
      finally { this.importing = false }
    },
    async doImportJsonConst() {
      if (!this.importForm.jsonContent.trim()) { this.$message.warning('请输入 JSON 内容'); return }
      this.importing = true
      try {
        const res = await importJsonConstants(this.currentProjectId, this.importForm.jsonContent)
        this.importResult = res.data || {}
        this.importJsonConstVisible = false
        this.importResultVisible = true
        this.loadData(); this.loadConstants()
      } catch (e) { this.$message.error('导入失败: ' + (e.message || '')) }
      finally { this.importing = false }
    },

    // ── Batch Validate ──
    async handleBatchValidate() {
      if (!this.currentProjectId) { this.$message.warning('请先选择项目'); return }
      this.validating = true
      try {
        const res = await batchValidateRules(this.currentProjectId)
        this.validateResults = res.data || []
        this.validateVisible = true
      } catch (e) { this.$message.error('验证失败: ' + (e.message || '')) }
      finally { this.validating = false }
    },

    // ── Helpers ──
    typeLabel: varTypeLabel,
    typeTagColor: varTypeTagColor,
    sourceLabel(s) { return { INPUT:'输入', COMPUTED:'计算', CONSTANT:'常量', DB:'数据库', API:'接口' }[s] || s },
    sourceTagColor(s) { return { INPUT:'', COMPUTED:'warning', CONSTANT:'success', DB:'info', API:'info' }[s] || '' },
    objTypeLabel(t) { return { INPUT:'输入对象', OUTPUT:'输出对象', INOUT:'输入输出' }[t] || t },
    objTypeColor(t) { return { INPUT:'', OUTPUT:'success', INOUT:'warning' }[t] || '' }
  }
}
</script>

<style scoped>
.linkage-hint { font-size:12px; color:#909399; margin-bottom:12px; padding:8px 12px; background:#f5f7fa; border-radius:4px; }
.linkage-hint a { color:#1890ff; text-decoration:none; }
.linkage-hint a:hover { text-decoration:underline; }

.var-toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; flex-wrap:wrap; gap:8px; }
.toolbar-right { display:flex; align-items:center; gap:8px; }

.var-tabs { margin-bottom:16px; }
.tab-filter-row { display:flex; gap:8px; align-items:center; margin-bottom:12px; flex-wrap:wrap; }
.tab-empty { text-align:center; padding:48px 0; color:#c0c4cc; font-size:14px; }
.text-muted { color:#909399; font-size:13px; }

/* 变量列表分区样式 */
.var-list-section { margin-bottom:24px; }
.var-list-section .section-title { font-size:14px; font-weight:600; color:#303133; margin-bottom:10px; padding-bottom:6px; border-bottom:1px solid #ebeef5; }
.var-group-card { border:1px solid #ebeef5; border-radius:6px; margin-bottom:10px; overflow:hidden; }
.var-group-header { display:flex; align-items:center; gap:8px; padding:10px 14px; background:#fafafa; border-bottom:1px solid #ebeef5; cursor:pointer; flex-wrap:wrap; }
.var-group-header:hover { background:#f0f2f5; }
.var-group-header .expand-icon { font-size:14px; color:#909399; margin-right:4px; transition:transform 0.2s; }
.var-group-code { font-weight:bold; font-size:14px; color:#303133; font-family:Consolas,monospace; }
.var-group-label { color:#909399; font-size:13px; }
.var-group-count { font-size:12px; color:#909399; }
.var-group-body { padding:10px; background:#fff; }

/* Object cards */
.obj-card { border:1px solid #ebeef5; border-radius:6px; margin-bottom:12px; overflow:hidden; }
.obj-card-header { display:flex; align-items:center; gap:8px; padding:10px 14px; background:#fafafa; border-bottom:1px solid #ebeef5; flex-wrap:wrap; }
.obj-code { font-weight:bold; font-size:14px; color:#303133; font-family:Consolas,monospace; }
.obj-label { color:#909399; font-size:13px; }
.obj-var-count { font-size:12px; color:#909399; }

/* Constant cards */
.const-toolbar { margin-bottom:12px; }
.const-group-card { border:1px solid #ebeef5; border-radius:6px; margin-bottom:10px; overflow:hidden; }
.const-group-header { display:flex; align-items:center; gap:8px; padding:10px 14px; background:#fafafa; border-bottom:1px solid #ebeef5; cursor:pointer; flex-wrap:wrap; }
.const-group-header:hover { background:#f0f2f5; }
.const-group-code { font-weight:bold; font-size:14px; color:#303133; font-family:Consolas,monospace; }
.const-group-label { color:#909399; font-size:13px; }
.const-count { font-size:12px; color:#909399; }
.const-group-body { padding:8px; }

/* Badges */
.badge { display:inline-block; padding:1px 6px; border-radius:3px; font-size:11px; font-family:Consolas,monospace; }
.badge-obj { background:#e6f7ff; color:#1890ff; border:1px solid #91d5ff; }
.badge-const { background:#f6ffed; color:#52c41a; border:1px solid #b7eb8f; }

/* Import result */
.import-result-body { text-align:center; padding:20px 0; }
.import-result-body h3 { margin:12px 0 8px; }
.import-result-body p { color:#606266; font-size:14px; margin:4px 0; }
</style>
