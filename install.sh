#!/bin/bash
# 自动安装脚本
echo "正在切换至 main 分支并同步最新代码..."

echo "正在构建并安装应用到手机..."
./gradlew installDebug --info

if [ $? -eq 0 ]; then
    echo "安装成功！"
else
    echo "安装失败，请检查设备连接或构建日志。"
    exit 1
fi
