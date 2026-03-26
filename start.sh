#!/bin/bash

# 配置路径（使用 Windows 路径格式）
REDIS_PATH="redis-server.exe"
REDIS_CONF="redis.conf"
ES_PATH="D:/Program Files/elasticsearch/elasticsearch-9.0.0/bin/elasticsearch.bat"

# 日志函数
log() {
    echo "[$(date '+%H:%M:%S')] $1"
}

# 启动函数
start_service() {
    local name=$1
    local cmd=$2
    local workdir=$3
    
    log "启动 $name..."
    
    # 检查是否已运行
    if tasklist | grep -i "$name" ; then
        log "$name 已在运行"
        return
    fi
    
    # 后台启动
    (
        cd "$workdir" || exit
        eval "$cmd" &
    )
    
    sleep 8
    
    if tasklist | grep -i "$name" ; then
        log "$name 启动成功 ✓"
    else
        log "$name 启动失败 ✗"
    fi

}

# ========== 主程序 ==========
echo "========== 服务启动脚本 =========="

start_service "redis-server" "$REDIS_PATH $REDIS_CONF" "D:/Program Files/Redis"
start_service "elasticsearch" "\"$ES_PATH\"" "D:/Program Files/elasticsearch/elasticsearch-9.0.0"

echo "========== 执行完毕 =========="
read -p "按回车键退出..."

