#!/usr/bin/env bash

# Путь к виртуальному окружению
VENV_DIR=".venv"

# Проверяем, существует ли venv
if [ ! -d "$VENV_DIR" ]; then
    echo "Создаю виртуальное окружение..."
    python -m venv $VENV_DIR
fi

# Активируем venv
. $VENV_DIR/bin/activate

python -m ensurepip --upgrade

# Обновляем pip
pip install --upgrade pip setuptools wheel

# Устанавливаем зависимости
if [ -f "requirements.txt" ]; then
    pip install -r requirements.txt
else
    echo "Файл requirements.txt не найден!"
fi

echo "Готово! Активируйте окружение командой:"
echo "source $VENV_DIR/bin/activate"
