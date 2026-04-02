const fs = require('fs');
const path = require('path');

// 确保输出目录存在
const outputDir = path.join(__dirname);
if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
}

// ============ 图1: 系统架构图 (SVG) ============
const architectureSvg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 900 700" width="900" height="700">
  <defs>
    <linearGradient id="grad1" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" style="stop-color:#667eea;stop-opacity:1" />
      <stop offset="100%" style="stop-color:#764ba2;stop-opacity:1" />
    </linearGradient>
    <linearGradient id="grad2" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" style="stop-color:#11998e;stop-opacity:1" />
      <stop offset="100%" style="stop-color:#38ef7d;stop-opacity:1" />
    </linearGradient>
    <linearGradient id="grad3" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" style="stop-color:#fc4a1a;stop-opacity:1" />
      <stop offset="100%" style="stop-color:#f7b733;stop-opacity:1" />
    </linearGradient>
    <linearGradient id="grad4" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" style="stop-color:#45637a;stop-opacity:1" />
      <stop offset="100%" style="stop-color:#5a7a9a;stop-opacity:1" />
    </linearGradient>
    <filter id="shadow" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="2" dy="2" stdDeviation="3" flood-opacity="0.3"/>
    </filter>
  </defs>

  <!-- 标题 -->
  <text x="450" y="35" text-anchor="middle" font-family="Arial, sans-serif" font-size="22" font-weight="bold" fill="#333">AgentFlow 系统架构图</text>

  <!-- 第一层: 前端表达层 -->
  <g transform="translate(50, 60)" filter="url(#shadow)">
    <rect x="0" y="0" width="800" height="120" rx="10" fill="url(#grad1)" opacity="0.15"/>
    <rect x="5" y="5" width="790" height="110" rx="8" fill="white" stroke="#667eea" stroke-width="2"/>
    <text x="400" y="30" text-anchor="middle" font-family="Arial, sans-serif" font-size="16" font-weight="bold" fill="#667eea">前端表达层 (React + TypeScript)</text>
    
    <!-- 组件 -->
    <rect x="30" y="45" width="160" height="60" rx="6" fill="#f0f4ff" stroke="#667eea" stroke-width="1.5"/>
    <text x="110" y="70" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#333">工作流画布</text>
    <text x="110" y="88" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#666">(ReactFlow)</text>

    <rect x="210" y="45" width="160" height="60" rx="6" fill="#f0f4ff" stroke="#667eea" stroke-width="1.5"/>
    <text x="290" y="70" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#333">节点配置面板</text>
    <text x="290" y="88" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#666">(Ant Design)</text>

    <rect x="390" y="45" width="160" height="60" rx="6" fill="#f0f4ff" stroke="#667eea" stroke-width="1.5"/>
    <text x="470" y="70" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#333">执行监控</text>
    <text x="470" y="88" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#666">(实时 SSE)</text>

    <rect x="570" y="45" width="160" height="60" rx="6" fill="#f0f4ff" stroke="#667eea" stroke-width="1.5"/>
    <text x="650" y="70" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#333">日志查看</text>
    <text x="650" y="88" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#666">(Monaco Editor)</text>
  </g>

  <!-- 连接线1 -->
  <g stroke="#667eea" stroke-width="2" fill="none" marker-end="url(#arrow)">
    <line x1="450" y1="185" x2="450" y2="215"/>
    <polygon points="450,220 445,210 455,210" fill="#667eea"/>
  </g>

  <!-- 第二层: 控制台中枢层 -->
  <g transform="translate(50, 230)" filter="url(#shadow)">
    <rect x="0" y="0" width="800" height="120" rx="10" fill="url(#grad2)" opacity="0.15"/>
    <rect x="5" y="5" width="790" height="110" rx="8" fill="white" stroke="#11998e" stroke-width="2"/>
    <text x="400" y="30" text-anchor="middle" font-family="Arial, sans-serif" font-size="16" font-weight="bold" fill="#11998e">控制台中枢层 (Console Hub - Java)</text>
    
    <!-- 组件 -->
    <rect x="30" y="45" width="160" height="60" rx="6" fill="#e8f8f5" stroke="#11998e" stroke-width="1.5"/>
    <text x="110" y="70" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#333">用户鉴权</text>
    <text x="110" y="88" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#666">(Spring Security)</text>

    <rect x="210" y="45" width="160" height="60" rx="6" fill="#e8f8f5" stroke="#11998e" stroke-width="1.5"/>
    <text x="290" y="70" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#333">模型管理</text>
    <text x="290" y="88" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#666">(Spring AI)</text>

    <rect x="390" y="45" width="160" height="60" rx="6" fill="#e8f8f5" stroke="#11998e" stroke-width="1.5"/>
    <text x="470" y="70" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#333">流程元数据</text>
    <text x="470" y="88" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#666">(MyBatis-Plus)</text>

    <rect x="570" y="45" width="160" height="60" rx="6" fill="#e8f8f5" stroke="#11998e" stroke-width="1.5"/>
    <text x="650" y="70" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#333">执行调度</text>
    <text x="650" y="88" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#666">(REST API)</text>
  </g>

  <!-- 连接线2 -->
  <line x1="450" y1="355" x2="450" y2="385" stroke="#11998e" stroke-width="2" marker-end="url(#arrow)"/>
  <polygon points="450,390 445,380 455,380" fill="#11998e"/>

  <!-- 第三层: 工作流引擎 -->
  <g transform="translate(50, 400)" filter="url(#shadow)">
    <rect x="0" y="0" width="800" height="140" rx="10" fill="url(#grad3)" opacity="0.15"/>
    <rect x="5" y="5" width="790" height="130" rx="8" fill="white" stroke="#fc4a1a" stroke-width="2"/>
    <text x="400" y="30" text-anchor="middle" font-family="Arial, sans-serif" font-size="16" font-weight="bold" fill="#fc4a1a">Java 工作流引擎 (Workflow Engine)</text>
    
    <!-- 左侧: 核心执行器 -->
    <rect x="20" y="45" width="340" height="80" rx="6" fill="#fff5e6" stroke="#fc4a1a" stroke-width="1.5"/>
    <text x="190" y="65" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#333">核心执行器 (NodeExecutor)</text>
    
    <rect x="35" y="75" width="60" height="40" rx="4" fill="#ffe0cc" stroke="#fc4a1a" stroke-width="1"/>
    <text x="65" y="100" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#333">Start</text>

    <rect x="105" y="75" width="60" height="40" rx="4" fill="#ffe0cc" stroke="#fc4a1a" stroke-width="1"/>
    <text x="135" y="100" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#333">LLM</text>

    <rect x="175" y="75" width="60" height="40" rx="4" fill="#ffe0cc" stroke="#fc4a1a" stroke-width="1"/>
    <text x="205" y="100" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#333">Plugin</text>

    <rect x="245" y="75" width="60" height="40" rx="4" fill="#ffe0cc" stroke="#fc4a1a" stroke-width="1"/>
    <text x="275" y="100" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#333">If-Else</text>

    <rect x="315" y="75" width="30" height="40" rx="4" fill="#ffe0cc" stroke="#fc4a1a" stroke-width="1"/>
    <text x="330" y="100" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#333">...</text>

    <!-- 右侧: 核心组件 -->
    <rect x="380" y="45" width="190" height="80" rx="6" fill="#fff5e6" stroke="#fc4a1a" stroke-width="1.5"/>
    <text x="475" y="65" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#333">VariablePool</text>
    <text x="475" y="82" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#666">节点间数据传递</text>
    <text x="475" y="97" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#666">模板变量解析</text>

    <rect x="590" y="45" width="190" height="80" rx="6" fill="#fff5e6" stroke="#fc4a1a" stroke-width="1.5"/>
    <text x="685" y="65" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#333">WorkflowDSL</text>
    <text x="685" y="82" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#666">DSL 解析</text>
    <text x="685" y="97" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#666">Kahn 环路检测</text>
  </g>

  <!-- 连接线3 -->
  <line x1="450" y1="545" x2="450" y2="575" stroke="#45637a" stroke-width="2" marker-end="url(#arrow)"/>
  <polygon points="450,580 445,570 455,570" fill="#45637a"/>

  <!-- 第四层: 基础设施层 -->
  <g transform="translate(50, 590)" filter="url(#shadow)">
    <rect x="0" y="0" width="800" height="90" rx="10" fill="url(#grad4)" opacity="0.15"/>
    <rect x="5" y="5" width="790" height="80" rx="8" fill="white" stroke="#45637a" stroke-width="2"/>
    <text x="400" y="28" text-anchor="middle" font-family="Arial, sans-serif" font-size="16" font-weight="bold" fill="#45637a">基础设施层 (Infrastructure)</text>
    
    <rect x="50" y="45" width="140" height="35" rx="6" fill="#e8ecf0" stroke="#45637a" stroke-width="1.5"/>
    <text x="120" y="67" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#333">MySQL</text>

    <rect x="220" y="45" width="140" height="35" rx="6" fill="#e8ecf0" stroke="#45637a" stroke-width="1.5"/>
    <text x="290" y="67" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#333">Redis</text>

    <rect x="390" y="45" width="140" height="35" rx="6" fill="#e8ecf0" stroke="#45637a" stroke-width="1.5"/>
    <text x="460" y="67" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#333">MinIO</text>

    <rect x="560" y="45" width="140" height="35" rx="6" fill="#e8ecf0" stroke="#45637a" stroke-width="1.5"/>
    <text x="630" y="67" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#333">Nginx</text>
  </g>
</svg>`;

// ============ 图2: 工作流执行流程图 ============
const workflowFlowSvg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 850" width="1000" height="850">
  <defs>
    <linearGradient id="flowGrad1" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" style="stop-color:#4facfe;stop-opacity:1" />
      <stop offset="100%" style="stop-color:#00f2fe;stop-opacity:1" />
    </linearGradient>
    <linearGradient id="flowGrad2" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" style="stop-color:#fa709a;stop-opacity:1" />
      <stop offset="100%" style="stop-color:#fee140;stop-opacity:1" />
    </linearGradient>
    <filter id="flowShadow" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="2" dy="2" stdDeviation="3" flood-opacity="0.2"/>
    </filter>
  </defs>

  <!-- 标题 -->
  <text x="500" y="30" text-anchor="middle" font-family="Arial, sans-serif" font-size="20" font-weight="bold" fill="#333">工作流执行流程图</text>

  <!-- 步骤1: 用户请求 -->
  <g transform="translate(400, 50)" filter="url(#flowShadow)">
    <rect x="0" y="0" width="200" height="50" rx="25" fill="url(#flowGrad1)"/>
    <text x="100" y="30" text-anchor="middle" font-family="Arial, sans-serif" font-size="14" font-weight="bold" fill="white">用户发起请求</text>
  </g>

  <!-- 箭头 -->
  <line x1="500" y1="105" x2="500" y2="135" stroke="#4facfe" stroke-width="2" marker-end="url(#arrow)"/>
  <polygon points="500,140 495,130 505,130" fill="#4facfe"/>

  <!-- 步骤2: Hub处理 -->
  <g transform="translate(250, 145)" filter="url(#flowShadow)">
    <rect x="0" y="0" width="500" height="80" rx="10" fill="white" stroke="#4facfe" stroke-width="2"/>
    <text x="250" y="25" text-anchor="middle" font-family="Arial, sans-serif" font-size="14" font-weight="bold" fill="#4facfe">Console Hub (8081)</text>
    <text x="250" y="45" text-anchor="middle" font-family="Arial, sans-serif" font-size="11" fill="#666">1. 鉴权验证  2. 加载工作流DSL  3. 验证流程配置</text>
    <text x="250" y="63" text-anchor="middle" font-family="Arial, sans-serif" font-size="11" fill="#666">4. 转发至工作流引擎</text>
  </g>

  <!-- 箭头 -->
  <line x1="500" y1="230" x2="500" y2="260" stroke="#4facfe" stroke-width="2"/>
  <polygon points="500,265 495,255 505,255" fill="#4facfe"/>

  <!-- 步骤3: DSL解析 -->
  <g transform="translate(250, 270)" filter="url(#flowShadow)">
    <rect x="0" y="0" width="500" height="90" rx="10" fill="white" stroke="#667eea" stroke-width="2"/>
    <text x="250" y="25" text-anchor="middle" font-family="Arial, sans-serif" font-size="14" font-weight="bold" fill="#667eea">Core Workflow Engine (7880) - DSL 解析</text>
    <rect x="30" y="40" width="100" height="35" rx="5" fill="#f0f4ff" stroke="#667eea"/>
    <text x="80" y="63" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#333">WorkflowDSL</text>
    <text x="80" y="73" text-anchor="middle" font-family="Arial, sans-serif" font-size="8" fill="#666">解析</text>
    
    <line x1="135" y1="57" x2="165" y2="57" stroke="#667eea" stroke-width="1.5"/>
    <polygon points="165,57 160,54 160,60" fill="#667eea"/>
    
    <rect x="170" y="40" width="100" height="35" rx="5" fill="#f0f4ff" stroke="#667eea"/>
    <text x="220" y="63" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#333">Node Map</text>
    <text x="220" y="73" text-anchor="middle" font-family="Arial, sans-serif" font-size="8" fill="#666">构建</text>

    <line x1="275" y1="57" x2="305" y2="57" stroke="#667eea" stroke-width="1.5"/>
    <polygon points="305,57 300,54 300,60" fill="#667eea"/>

    <rect x="310" y="40" width="100" height="35" rx="5" fill="#f0f4ff" stroke="#667eea"/>
    <text x="360" y="63" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#333">Edge Map</text>
    <text x="360" y="73" text-anchor="middle" font-family="Arial, sans-serif" font-size="8" fill="#666">构建</text>

    <line x1="415" y1="57" x2="445" y2="57" stroke="#667eea" stroke-width="1.5"/>
    <polygon points="445,57 440,54 440,60" fill="#667eea"/>

    <rect x="450" y="40" width="20" height="35" rx="5" fill="#ffe0cc" stroke="#fc4a1a"/>
    <text x="460" y="63" text-anchor="middle" font-family="Arial, sans-serif" font-size="8" fill="#333">✓</text>
  </g>

  <!-- Kahn算法说明 -->
  <g transform="translate(800, 285)">
    <rect x="0" y="0" width="160" height="60" rx="8" fill="#fff5e6" stroke="#fc4a1a" stroke-width="1.5"/>
    <text x="80" y="20" text-anchor="middle" font-family="Arial, sans-serif" font-size="11" font-weight="bold" fill="#fc4a1a">环路检测</text>
    <text x="80" y="38" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#666">Kahn's Algorithm</text>
    <text x="80" y="52" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#666">拓扑排序验证DAG</text>
  </g>

  <!-- 箭头 -->
  <line x1="500" y1="365" x2="500" y2="395" stroke="#667eea" stroke-width="2"/>
  <polygon points="500,400 495,390 505,390" fill="#667eea"/>

  <!-- 步骤4: 执行链路 -->
  <g transform="translate(200, 405)" filter="url(#flowShadow)">
    <rect x="0" y="0" width="600" height="100" rx="10" fill="white" stroke="#11998e" stroke-width="2"/>
    <text x="300" y="25" text-anchor="middle" font-family="Arial, sans-serif" font-size="14" font-weight="bold" fill="#11998e">执行链路构建</text>
    
    <!-- 节点 -->
    <rect x="30" y="40" width="70" height="45" rx="6" fill="#e8f8f5" stroke="#11998e" stroke-width="1.5"/>
    <text x="65" y="60" text-anchor="middle" font-family="Arial, sans-serif" font-size="11" font-weight="bold" fill="#333">START</text>
    <text x="65" y="75" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#666">起始节点</text>

    <line x1="105" y1="62" x2="135" y2="62" stroke="#11998e" stroke-width="2"/>
    <polygon points="135,62 130,58 130,66" fill="#11998e"/>

    <rect x="140" y="40" width="70" height="45" rx="6" fill="#e8f8f5" stroke="#11998e" stroke-width="1.5"/>
    <text x="175" y="60" text-anchor="middle" font-family="Arial, sans-serif" font-size="11" font-weight="bold" fill="#333">LLM</text>
    <text x="175" y="75" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#666">大模型</text>

    <line x1="215" y1="62" x2="245" y2="62" stroke="#11998e" stroke-width="2"/>
    <polygon points="245,62 240,58 240,66" fill="#11998e"/>

    <rect x="250" y="40" width="70" height="45" rx="6" fill="#e8f8f5" stroke="#11998e" stroke-width="1.5"/>
    <text x="285" y="58" text-anchor="middle" font-family="Arial, sans-serif" font-size="11" font-weight="bold" fill="#333">If-Else</text>
    <text x="285" y="73" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#666">条件分支</text>

    <!-- True分支 -->
    <line x1="325" y1="50" x2="355" y2="35" stroke="#11998e" stroke-width="1.5"/>
    <text x="340" y="38" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#11998e">True</text>
    <polygon points="355,30 350,40 360,40" fill="#11998e"/>
    <rect x="360" y="20" width="70" height="35" rx="6" fill="#e8f8f5" stroke="#11998e" stroke-width="1"/>
    <text x="395" y="42" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#333">Branch A</text>

    <!-- False分支 -->
    <line x1="325" y1="75" x2="355" y2="90" stroke="#fc4a1a" stroke-width="1.5"/>
    <text x="340" y="88" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#fc4a1a">False</text>
    <polygon points="355,95 350,85 360,85" fill="#fc4a1a"/>
    <rect x="360" y="80" width="70" height="35" rx="6" fill="#fff5e6" stroke="#fc4a1a" stroke-width="1"/>
    <text x="395" y="102" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#333">Branch B</text>

    <!-- 汇聚 -->
    <line x1="465" y1="37" x2="495" y2="50" stroke="#11998e" stroke-width="1.5"/>
    <line x1="465" y1="97" x2="495" y2="85" stroke="#11998e" stroke-width="1.5"/>
    <polygon points="495,50 490,47 492,54" fill="#11998e"/>
    <polygon points="495,85 492,78 490,85" fill="#11998e"/>

    <rect x="500" y="40" width="70" height="45" rx="6" fill="#e8f8f5" stroke="#11998e" stroke-width="1.5"/>
    <text x="535" y="60" text-anchor="middle" font-family="Arial, sans-serif" font-size="11" font-weight="bold" fill="#333">END</text>
    <text x="535" y="75" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#666">结束</text>
  </g>

  <!-- 箭头 -->
  <line x1="500" y1="510" x2="500" y2="540" stroke="#11998e" stroke-width="2"/>
  <polygon points="500,545 495,535 505,535" fill="#11998e"/>

  <!-- 步骤5: 节点执行 -->
  <g transform="translate(150, 550)" filter="url(#flowShadow)">
    <rect x="0" y="0" width="700" height="110" rx="10" fill="white" stroke="#fc4a1a" stroke-width="2"/>
    <text x="350" y="25" text-anchor="middle" font-family="Arial, sans-serif" font-size="14" font-weight="bold" fill="#fc4a1a">节点执行 (NodeExecutor)</text>
    
    <!-- 流程 -->
    <rect x="20" y="40" width="130" height="55" rx="6" fill="#fff5e6" stroke="#fc4a1a"/>
    <text x="85" y="60" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" font-weight="bold" fill="#333">resolveInputs()</text>
    <text x="85" y="75" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#666">解析模板变量</text>
    <text x="85" y="87" text-anchor="middle" font-family="Arial, sans-serif" font-size="8" fill="#999">"{{node-llm::002.output}}"</text>

    <line x1="155" y1="67" x2="185" y2="67" stroke="#fc4a1a" stroke-width="1.5"/>
    <polygon points="185,67 180,63 180,71" fill="#fc4a1a"/>

    <rect x="190" y="40" width="130" height="55" rx="6" fill="#fff5e6" stroke="#fc4a1a"/>
    <text x="255" y="60" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" font-weight="bold" fill="#333">executeNode()</text>
    <text x="255" y="75" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#666">执行节点逻辑</text>
    <text x="255" y="87" text-anchor="middle" font-family="Arial, sans-serif" font-size="8" fill="#999">LLM调用/插件执行</text>

    <line x1="325" y1="67" x2="355" y2="67" stroke="#fc4a1a" stroke-width="1.5"/>
    <polygon points="355,67 350,63 350,71" fill="#fc4a1a"/>

    <rect x="360" y="40" width="130" height="55" rx="6" fill="#fff5e6" stroke="#fc4a1a"/>
    <text x="425" y="60" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" font-weight="bold" fill="#333">storeOutputs()</text>
    <text x="425" y="75" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#666">保存到VariablePool</text>
    <text x="425" y="87" text-anchor="middle" font-family="Arial, sans-serif" font-size="8" fill="#999">node-id.output-name</text>

    <line x1="495" y1="67" x2="525" y2="67" stroke="#fc4a1a" stroke-width="1.5"/>
    <polygon points="525,67 520,63 520,71" fill="#fc4a1a"/>

    <rect x="530" y="40" width="130" height="55" rx="6" fill="#fff5e6" stroke="#fc4a1a"/>
    <text x="595" y="60" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" font-weight="bold" fill="#333">success/error</text>
    <text x="595" y="75" text-anchor="middle" font-family="Arial, sans-serif" font-size="9" fill="#666">回调通知</text>
    <text x="595" y="87" text-anchor="middle" font-family="Arial, sans-serif" font-size="8" fill="#999">onNodeEnd()</text>
  </g>

  <!-- 箭头 -->
  <line x1="500" y1="665" x2