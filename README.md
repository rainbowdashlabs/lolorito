# Lolorito

A Discord bot and web app for the Final Fantasy XIV market board. Lolorito reads live market data from
[Universalis](https://universalis.app) and finds cross-world offers, plans shopping runs, checks crafts and
desynthesis, and sends price alerts as Discord DMs.

## Run it

Lolorito ships as a Docker image and needs PostgreSQL and a Discord application.

```yaml
services:
  lolorito:
    image: ghcr.io/rainbowdashlabs/lolorito:latest
    restart: unless-stopped
    ports:
      - "8080:8080"
    volumes:
      - ./config:/app/config
      - ./data:/app/data
      - ./logs:/app/logs
```

The first start writes `config/config.yaml` with defaults. Fill in the bot token, the OAuth client, the server id
and the database, then start it again.

Setup, configuration, commands and the web app: https://docs.chojo.dev/lolorito
