# Learning Assistant Startup Scripts

Use this script from the backend project root:

```bash
./scripts/learning-assistant.sh start
```

Short aliases are also available:

```bash
./scripts/start.sh
./scripts/status.sh
./scripts/logs.sh
./scripts/stop.sh
```

Common commands:

```bash
./scripts/learning-assistant.sh status
./scripts/learning-assistant.sh logs
./scripts/learning-assistant.sh restart
./scripts/learning-assistant.sh stop
```

Service-specific commands:

```bash
./scripts/learning-assistant.sh start backend
./scripts/learning-assistant.sh start frontend
./scripts/learning-assistant.sh logs backend
./scripts/learning-assistant.sh logs frontend
```

Default URLs:

```text
Backend:  http://127.0.0.1:16681
Frontend: http://127.0.0.1:5173
```

The script stores runtime pid files under `.run/` and console logs under `logs/dev/`.

If Node.js or Maven is installed in a non-standard location, set:

```bash
MVN_BIN=/path/to/mvn NODE_BIN=/path/to/node ./scripts/learning-assistant.sh start
```
