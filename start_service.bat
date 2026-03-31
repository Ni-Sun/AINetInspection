@echo off
chcp 65001
setlocal ENABLEDELAYEDEXPANSION

goto :main

REM 日志函数
:log
echo [%time:~0,8%] %*
goto :eof

REM start_service name path cmd...
:start_service
set "svc_name=%~1"
set "svc_path=%~2"

REM 把第 3~9 个参数组合成命令
set "svc_cmd="
if not "%~3"=="" set "svc_cmd=%~3"
if not "%~4"=="" set "svc_cmd=%svc_cmd% %~4"
if not "%~5"=="" set "svc_cmd=%svc_cmd% %~5"
if not "%~6"=="" set "svc_cmd=%svc_cmd% %~6"
if not "%~7"=="" set "svc_cmd=%svc_cmd% %~7"
if not "%~8"=="" set "svc_cmd=%svc_cmd% %~8"
if not "%~9"=="" set "svc_cmd=%svc_cmd% %~9"

REM 检测是否已经启动（tasklist 或 docker ps 中有名字即可）
tasklist | findstr /I "%svc_name%" 2>&1
if not errorlevel 1 (
    call :log "%svc_name% 已启动"
    goto :eof
)

docker ps 2 | findstr /I "%svc_name%" 2>&1
if not errorlevel 1 (
    call :log "%svc_name% 已启动(docker)"
    goto :eof
)

REM 没启动则启动
pushd "%svc_path%"
start "" /B %svc_cmd%
popd
goto :eof

REM 检查是否启动成功
:check
set "svc_name=%~1"

tasklist | findstr /I "%svc_name%" 2>&1
if not errorlevel 1 (
    call :log "%svc_name% 启动成功"
    goto :eof
)

docker ps 2 | findstr /I "%svc_name%" 2>&1
if not errorlevel 1 (
    call :log "%svc_name% 启动成功(docker)"
) else (
    call :log "%svc_name% 启动失败"
)
goto :eof

REM ================== 实际要启动的服务入口 ==================
:main

REM elasticsearch     http://localhost:9200
call :start_service "elasticsearch" "D:\Program Files\elasticsearch\elasticsearch-9.0.0\bin" "elasticsearch.bat"

REM zlmedia
call :start_service "zlm" "" "docker" "start" "zlmediakit"

REM nginx 使用项目中的 nginx.conf（监听 35300）
call :start_service "nginx" "D:\Program Files\nginx-1.24.0" "nginx.exe" "-c" "D:\Study\Graduation_Project\AINetInspection-robotdog\nginx\nginx.conf"

REM redis             http://localhost:6379
call :start_service "redis" "D:\Program Files\Redis" "redis-server.exe" "redis.conf"

REM minio 管理员权限  http://localhost:9000(UI) or 9005(API)
call :start_service "minio" "D:\Program Files\minio" "minio_start.bat"

REM rabbitmq          http://localhost:15672
call :start_service "rabbitmq" "D:\Program Files\RabbitMQ Server\rabbitmq_server-4.2.4\sbin" "rabbitmq-service.bat" "start"

REM 前端服务
call :start_service "node" "D:\Study\Graduation_Project\AINetInspection-robotdog\front" "npm" "run" "serve"

REM 等待
timeout /t 60 /nobreak

REM 检查服务状态
call :check "elasticsearch"
call :check "zlm"
call :check "nginx"
call :check "redis"
call :check "minio"
call :check "rabbitmq"

endlocal