#!/usr/bin/env bash

# Остановка при первой ошибке
set -e

# Вывод каждой команды перед выполнением
set -x

# Проверка наличия аргумента с названием ветки
if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <branch_name>"
  exit 1
fi

# Сохраняем название ветки в переменную
BRANCH_NAME=$1

# Шаг 1: Получение последних изменений из репозитория
git fetch

# Шаг 2: Переключение на основную ветку
git checkout "$BRANCH_NAME"

# Шаг 3: Слияние последних изменений
git pull origin "$BRANCH_NAME"

# На сервере проект запускается отдельным compose-файлом: базу и объектное
# хранилище даёт common-infra, а docker-compose.yml с их локальными копиями
# используется только на машине разработчика.
COMPOSE="docker compose -f docker-compose.prod.yml"

[ -f .env.prod ] || { echo "Нет .env.prod с параметрами подключения к common-infra"; exit 1; }
docker network inspect common-infra >/dev/null 2>&1 || { echo "Нет сети common-infra - не поднята общая инфраструктура"; exit 1; }

# Шаг 4: Остановка всех работающих контейнеров
$COMPOSE down

cd ./hunty-hr
rm -rf build
rm -rf node_modules
rm -rf src/main/frontend/generated
rm -rf src/main/bundles
cd ..

# Шаг 5: Удаление прежних образов
# На чистом сервере образов ещё нет, а set -e превратил бы это в падение деплоя.
docker rmi -f hunty-ai-hunty-hr:latest || true
docker rmi -f hunty-ai-hunty-interview-backend:latest || true
docker rmi -f hunty-ai-hunty-interview-ui:latest || true

$COMPOSE rm -f
docker image prune -f --filter "label=com.docker.compose.project=hunty-ai"

# Шаг 6: Сборка hunty-hr
# Проект собирается под Java 17: на сервере версия по умолчанию другая, поэтому
# JDK выбирается явно, иначе сборка падает на несовместимом байт-коде.
JAVA_17_HOME=${JAVA_17_HOME:-/usr/lib/jvm/temurin-17-jdk-arm64}
if [ -x "$JAVA_17_HOME/bin/javac" ]; then
  export JAVA_HOME="$JAVA_17_HOME"
fi
cd ./hunty-hr
./gradlew -Dorg.gradle.jvmargs='-Xms1g -Xmx4g' -Pvaadin.productionMode=true bootJar -x test
cd ..

# Шаг 7: Запуск сервисов проекта
$COMPOSE up -d --build

echo "Deployment completed successfully on branch $BRANCH_NAME"
