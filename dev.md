```shell
./colsrc.sh --patterns "*.py" "*.tsx" --exclude-dirs hunty-hr .venv dist node_modules
```

```shell
printf 'Привет, как у тебя дела? Сегодня я буду проводить тебе интервью на позицию java бэкенд разработчика\n' | python -m piper \
  -m ./app/models/piper/voice-ru-irinia-medium/ru-irinia-medium.onnx \
  -c ./app/models/piper/voice-ru-irinia-medium/ru-irinia-medium.onnx.json \
  -f ./piper.wav
```