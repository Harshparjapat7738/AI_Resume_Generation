# Config repository (native backend)

`config-server` serves these files to every other service at startup.

- `application.yml` — shared defaults applied to all services
- `<service-name>.yml` — overrides for one service (e.g. `jd-service.yml`)
- `<service-name>-<profile>.yml` — overrides for one service in one profile

**Never put a secret in this directory.** It is committed to Git. Use `${ENV_VAR}`
placeholders; the value is resolved from the environment of the service that consumes
the config, which is populated from `.env` / your secrets manager.
