@echo off
chcp 65001 >nul
echo ====================================
echo 医院智能排队叫号系统 - 数据库初始化
echo ====================================
echo.

:input_password
set /p MYSQL_PASSWORD="请输入MySQL root密码: "

echo.
echo 正在连接MySQL并初始化数据库...
echo.

mysql -u root -p%MYSQL_PASSWORD% < src\main\resources\db\hospital_queue.sql 2>error.log

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ====================================
    echo ✅ 数据库初始化成功！
    echo ====================================
    echo.
    echo 初始化内容：
    echo - 创建数据库: hospital_queue
    echo - 创建9张表（患者、科室、医生、挂号单等）
    echo - 插入测试数据（10个科室、20个医生、50个患者等）
    echo.
    echo 下一步：
    echo 1. 修改 src\main\resources\application.yaml 中的数据库密码
    echo 2. 启动项目
    echo.
    del error.log 2>nul
    pause
) else (
    echo.
    echo ====================================
    echo ❌ 数据库初始化失败！
    echo ====================================
    echo.
    echo 错误信息：
    type error.log
    echo.
    echo 可能的原因：
    echo 1. 密码错误 - 请重新输入正确的密码
    echo 2. MySQL服务未启动 - 请先启动MySQL服务
    echo 3. 端口被占用 - 检查3306端口
    echo.
    set /p retry="是否重试？(Y/N): "
    if /i "%retry%"=="Y" (
        del error.log 2>nul
        goto input_password
    )
    pause
)
