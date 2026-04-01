#!/bin/bash

# 本地 MySQL 数据库初始化脚本
# 用途: 在本地 MySQL 中创建所需的数据库和表

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# MySQL 配置
MYSQL_HOST="localhost"
MYSQL_PORT="3306"
MYSQL_USER="root"

# 如果环境变量未设置，则尝试交互式获取
if [ -z "$MYSQL_PASSWORD" ]; then
    echo -e "${YELLOW}请输入 MySQL root 用户密码 (直接回车使用默认值: 123456):${NC}"
    read -s MYSQL_PASSWORD
    echo ""

    if [ -z "$MYSQL_PASSWORD" ]; then
        MYSQL_PASSWORD="123456"
    fi
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SQL_DIR="$PROJECT_ROOT/docker/agentflow/mysql"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  本地 MySQL 数据库初始化${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查是否已存在数据库
echo -e "${YELLOW}检查现有数据库...${NC}"
EXISTING_DBS=$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SHOW DATABASES;" 2>/dev/null | grep -E "agentflow" || true)

if [ -n "$EXISTING_DBS" ]; then
    echo -e "${YELLOW}发现以下数据库已存在:${NC}"
    echo "$EXISTING_DBS"
    echo ""
    echo -e "${YELLOW}请选择操作:${NC}"
    echo -e "  ${CYAN}1${NC} - 删除现有数据库并重新初始化 (⚠️  会丢失所有数据)"
    echo -e "  ${CYAN}2${NC} - 跳过已存在的数据库，只导入缺失的"
    echo -e "  ${CYAN}3${NC} - 取消操作"
    echo ""
    read -p "请输入选项 [1/2/3]: " choice

    case $choice in
        1)
            echo -e "${RED}警告: 将删除所有现有数据！${NC}"
            read -p "确认删除？输入 'yes' 继续: " confirm
            if [ "$confirm" != "yes" ]; then
                echo -e "${YELLOW}操作已取消${NC}"
                exit 0
            fi
            DROP_EXISTING=true
            ;;
        2)
            echo -e "${CYAN}将跳过已存在的数据库${NC}"
            DROP_EXISTING=false
            ;;
        3)
            echo -e "${YELLOW}操作已取消${NC}"
            exit 0
            ;;
        *)
            echo -e "${RED}无效选项，操作已取消${NC}"
            exit 1
            ;;
    esac
else
    DROP_EXISTING=false
fi
echo ""

# 检查 MySQL 客户端是否安装
if ! command -v mysql &> /dev/null; then
    echo -e "${RED}错误: 未找到 mysql 命令${NC}"
    echo -e "${YELLOW}请先安装 MySQL 客户端:${NC}"
    echo -e "${CYAN}  brew install mysql-client${NC}"
    echo -e "${CYAN}  echo 'export PATH=\"/opt/homebrew/opt/mysql-client/bin:\$PATH\"' >> ~/.zshrc${NC}"
    echo -e "${CYAN}  source ~/.zshrc${NC}"
    exit 1
fi

# 测试 MySQL 连接
echo -e "${YELLOW}[1/6] 测试 MySQL 连接...${NC}"
if mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SELECT 1;" &> /dev/null; then
    echo -e "${GREEN}✓ MySQL 连接成功${NC}"
else
    echo -e "${RED}✗ MySQL 连接失败${NC}"
    echo -e "${YELLOW}请检查:${NC}"
    echo -e "  1. MySQL 服务是否启动: ${CYAN}brew services list | grep mysql${NC}"
    echo -e "  2. 用户名密码是否正确: ${CYAN}root / 123456${NC}"
    echo -e "  3. 启动 MySQL: ${CYAN}brew services start mysql${NC}"
    exit 1
fi
echo ""

# 创建数据库
echo -e "${YELLOW}[2/6] 创建数据库...${NC}"

DATABASES=(
    "agentflow-console"
    "agentflow-link"
    "agentflow-workflow"
)

# 使用配置文件来避免命令行密码警告
MYSQL_CNF=$(mktemp)
echo "[client]" > "$MYSQL_CNF"
echo "user=$MYSQL_USER" >> "$MYSQL_CNF"
echo "password=$MYSQL_PASSWORD" >> "$MYSQL_CNF"
echo "host=$MYSQL_HOST" >> "$MYSQL_CNF"
echo "port=$MYSQL_PORT" >> "$MYSQL_CNF"

# 清理临时文件
trap 'rm -f "$MYSQL_CNF"' EXIT

for db in "${DATABASES[@]}"; do
    if [ "$DROP_EXISTING" = true ]; then
        echo -e "${RED}删除现有数据库: ${db}${NC}"
        mysql --defaults-extra-file="$MYSQL_CNF" <<EOF
DROP DATABASE IF EXISTS \`${db}\`;
EOF
    fi

    echo -e "${BLUE}创建数据库: ${db}${NC}"
    mysql --defaults-extra-file="$MYSQL_CNF" <<EOF
CREATE DATABASE IF NOT EXISTS \`${db}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EOF
    echo -e "${GREEN}✓ ${db} 创建成功${NC}"
done
echo ""

# 导入主数据库 schema
echo -e "${YELLOW}[3/6] 导入 agentflow-console 数据库表结构...${NC}"
if [ -f "$SQL_DIR/schema.sql" ]; then
    echo -e "${BLUE}正在导入: schema.sql (可能需要几分钟)...${NC}"
    if mysql --defaults-extra-file="$MYSQL_CNF" agentflow-console < "$SQL_DIR/schema.sql" 2>&1 | tee /tmp/mysql_import.log | grep -v "Warning" >/dev/null; then
        echo -e "${GREEN}✓ schema.sql 导入成功${NC}"
    else
        MYSQL_EXIT_CODE=${PIPESTATUS[0]}
        if [ $MYSQL_EXIT_CODE -eq 0 ]; then
            echo -e "${GREEN}✓ schema.sql 导入成功${NC}"
        else
            if grep -q "Duplicate entry" /tmp/mysql_import.log; then
                echo -e "${YELLOW}⚠ 检测到重复数据，部分数据已存在（这是正常的）${NC}"
            else
                echo -e "${RED}✗ schema.sql 导入失败${NC}"
                echo -e "${YELLOW}查看详细错误: cat /tmp/mysql_import.log${NC}"
                exit 1
            fi
        fi
    fi
else
    echo -e "${YELLOW}⚠ schema.sql 文件不存在，跳过${NC}"
fi
echo ""

# 导入 workflow 表
echo -e "${YELLOW}[4/6] 导入 agentflow-workflow 表结构...${NC}"
if [ -f "$SQL_DIR/workflow.sql" ]; then
    echo -e "${BLUE}正在导入: workflow.sql${NC}"
    mysql --defaults-extra-file="$MYSQL_CNF" agentflow-workflow < "$SQL_DIR/workflow.sql"
    echo -e "${GREEN}✓ workflow.sql 导入成功${NC}"
else
    echo -e "${YELLOW}⚠ workflow.sql 文件不存在，跳过${NC}"
fi
echo ""

# 导入 link 表
echo -e "${YELLOW}[5/6] 导入 agentflow-link 表结构...${NC}"
if [ -f "$SQL_DIR/link.sql" ]; then
    echo -e "${BLUE}正在导入: link.sql${NC}"
    mysql --defaults-extra-file="$MYSQL_CNF" agentflow-link < "$SQL_DIR/link.sql"
    echo -e "${GREEN}✓ link.sql 导入成功${NC}"
else
    echo -e "${YELLOW}⚠ link.sql 文件不存在，跳过${NC}"
fi
echo ""

# 验证数据库
echo -e "${YELLOW}[6/6] 验证数据库创建...${NC}"
echo -e "${CYAN}已创建的数据库:${NC}"
mysql --defaults-extra-file="$MYSQL_CNF" -e "SHOW DATABASES;" | grep -E "agentflow"
echo ""

# 显示主要表
echo -e "${CYAN}agentflow-console 主要表:${NC}"
mysql --defaults-extra-file="$MYSQL_CNF" agentflow-console -e "SHOW TABLES;" | head -20
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  数据库初始化完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

echo -e "${CYAN}数据库连接信息:${NC}"
echo -e "  Host: ${YELLOW}localhost${NC}"
echo -e "  Port: ${YELLOW}3306${NC}"
echo -e "  User: ${YELLOW}root${NC}"
echo -e "  Password: ${YELLOW}123456${NC}"
echo ""

echo -e "${CYAN}已创建的数据库:${NC}"
for db in "${DATABASES[@]}"; do
    echo -e "  - ${GREEN}${db}${NC}"
done
echo ""

echo -e "${CYAN}下一步:${NC}"
echo -e "  1. 启动 Console Hub (在 IDEA 中,Active profiles: local)"
echo -e "  2. 启动 Java Workflow Engine"
echo -e "  3. 启动前端:"
echo -e "     ${YELLOW}cd console/frontend && npm run dev${NC}"
echo ""
