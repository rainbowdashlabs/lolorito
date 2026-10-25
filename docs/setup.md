---
title: Setup
sidebar_position: 2
---

This page shows how to run your own Lolorito with Docker. Lolorito is one program: the Discord bot, the web app and
its API all run in the same container on port 8080.

## What you need

- **Docker** with Docker Compose.
- **PostgreSQL.** Lolorito keeps all its data in PostgreSQL. It is developed and tested against PostgreSQL 18. The
  example below brings its own database.
- **A Discord application** with a bot. You need its bot token, its OAuth2 client id and its client secret.
- **A Discord server** for the bot. Lolorito registers its commands on this one server, and only members of it can
  log in to the web app.
- **Internet access** from the container. Lolorito streams market data from Universalis and refreshes its item,
  recipe and desynth catalog from XIVAPI.

## Create the Discord application

1. Open the [Discord developer portal](https://discord.com/developers/applications) and create an application.
2. On the **Bot** page, copy the token. Turn on the **Server Members Intent**. Lolorito needs it to check that a user
   is still on your server.
3. On the **OAuth2** page, copy the client id and the client secret. Add a redirect:

   ```text
   <your public address>/auth/callback
   ```

   For example `https://lolorito.example.com/auth/callback`. The address must match `http.publicBaseUrl` (or
   `http.frontendBaseUrl` when you set it). See [Configuration](./configuration.md#http).
4. Invite the bot to your server with the `bot` and `applications.commands` scopes.
5. Copy the id of your server. Turn on developer mode in Discord, then right click the server and pick **Copy Server
   ID**.

The web login asks Discord for the `identify` and `guilds` scopes. You don't have to set them anywhere.

## Run it with Docker Compose

Create a folder for Lolorito and put this `compose.yaml` into it:

```yaml
services:
  lolorito:
    image: ghcr.io/rainbowdashlabs/lolorito:latest
    restart: unless-stopped
    depends_on:
      - db
    ports:
      - "8080:8080"
    volumes:
      - ./config:/app/config
      - ./data:/app/data
      - ./logs:/app/logs

  db:
    image: postgres:18-alpine
    restart: unless-stopped
    environment:
      POSTGRES_USER: lolorito
      POSTGRES_PASSWORD: <your database password>
      POSTGRES_DB: lolorito
    volumes:
      - lolorito_pg:/var/lib/postgresql

volumes:
  lolorito_pg:
```

The container runs from `/app`. These folders matter:

| Folder | What is in it |
|---|---|
| `/app/config` | `config.yaml`, the configuration file. Keep this one. |
| `/app/data` | A cache of item icons. You can lose it, it fills up again. |
| `/app/logs` | Log files. `app.log` is the current one. It rolls over every day and on every start. |

## First start

1. Start it once:

   ```bash
   docker compose up -d
   ```

   On the first start Lolorito writes `config/config.yaml` with every setting at its default, before it connects to
   the database. The defaults can't reach your database, so stop it again:

   ```bash
   docker compose down
   ```

2. Open `config/config.yaml` and fill in at least these keys:

   ```yaml
   baseSettings:
     token: "<your bot token>"
     botGuild: <your server id>
   database:
     host: "db"
     port: "5432"
     database: "lolorito"
     schema: "lolorito"
     user: "lolorito"
     password: "<your database password>"
   discordOauth:
     clientId: "<your client id>"
     clientSecret: "<your client secret>"
   http:
     publicBaseUrl: "https://lolorito.example.com"
   ```

   Every key is explained on [Configuration](./configuration.md).

3. Start it again:

   ```bash
   docker compose up -d
   ```

Lolorito creates its database schema and tables on its own. Then it connects to Discord, starts the web app on port
8080 and begins to stream market data. Offers and prices get better over the first hours, as sales come in and the
market models are fitted.

:::tip
You don't have to keep the full file. Write only the keys you change, and Lolorito fills in the rest with defaults.
It writes the complete file back on every start.
:::

:::note
`http.tokenEncryptionKey` can stay empty. If it is empty, Lolorito creates a key on start and saves it to
`config.yaml`. Keep that key: if it changes, every user has to log in again.
:::

## Public address and HTTPS

The web app needs to know the address people use to open it. Set `http.publicBaseUrl` to that address, without a
trailing slash. Lolorito builds the Discord login redirect from it.

Put a reverse proxy with HTTPS in front of port 8080:

- Session cookies are sent with the `Secure` flag by default (`http.secureCookies: true`), so browsers only accept
  them over HTTPS. If you test on plain HTTP, set `http.secureCookies` to `false`.
- The proxy must pass WebSocket connections. The web app uses one on `/api/v1/ws/valuations` to refresh prices.
- Lolorito reads the `X-Forwarded-For` header to rate limit login attempts per client.

## Upgrading

Pull the new image and restart:

```bash
docker compose pull
docker compose up -d
```

Lolorito updates its database schema on start.
