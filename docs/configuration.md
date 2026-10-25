---
title: Configuration
sidebar_position: 3
---

Lolorito reads its settings from `config/config.yaml`, relative to its working directory. In the Docker image that is
`/app/config/config.yaml`. The file is created with defaults on the first start. See [Setup](./setup.md#first-start).

Missing keys get their default value. On every start Lolorito writes the complete file back, so new keys from an
update show up in your file on their own.

## Full file with defaults

```yaml
baseSettings:
  botGuild: 0
  botOwner: []
  token: ""
catalog:
  enabled: true
  initialDelaySeconds: 15
  intervalHours: 24
database:
  database: "db"
  host: "localhost"
  password: "pw"
  poolSize: 5
  port: "5432"
  schema: "lolorito"
  user: "user"
discordOauth:
  clientId: ""
  clientSecret: ""
http:
  frontendBaseUrl: ""
  guildRecheckTtlBackgroundMinutes: 60
  guildRecheckTtlMinutes: 60
  host: "0.0.0.0"
  port: 8080
  publicBaseUrl: "http://localhost:8080"
  secureCookies: true
  sessionCookieName: "lolorito_session"
  sessionMaxAgeDays: 90
  sweeperIntervalMinutes: 15
  tokenEncryptionKey: ""
links:
  faq: "https://rainbowdashlabs.github.io/lolorito/faq"
  invite: "https://discord.com/oauth2/authorize?client_id=868279025251012639&scope=bot&permissions=1342532672"
  support: ""
  tos: ""
  website: "https://rainbowdashlabs.github.io/lolorito/"
planner:
  attentionBudgetHours: 8.0
  attentionFraction: 0.25
  budget: 1000000
  candidateTopK: 250
  craftChainMaxDepth: 3
  hopWeightGilPerSecond: 100
  ilpMaxNodes: 200000
  inventorySlots: 140
  maxWorlds: 5
  retainerAttentionFraction: 0.05
  retainerBudgetFraction: 0.4
  retainerListingSlots: 40
  retainerListingStackTarget: 20
  retainerShelfHoursThreshold: 6.0
  retainerSlots: 20
  tDcSeconds: 15
  tRegionSeconds: 45
universalis:
  regions:
  - "Europe"
  - "North-America"
  - "Oceania"
  - "Japan"
  - "中国"
value:
  alertScanIntervalMinutes: 5
  alertWebhookUrl: ""
  craftSecondsPerUnit: 5.0
  ghostListingWindowDays: 14
  listingFreshnessHours: 24
  mbTax: 0.05
  mbTaxByDataCenter: {}
  minSamplesPerKey: 5
  modelRefreshMinutes: 60
  priceHalfLifeDays: 7
  rateWindowDays: 14
  refitBatchSize: 2000
  residualShrinkagePriorK: 20
  residualWindowDays: 14
  responseCacheMaxSize: 1000
  responseCacheSeconds: 30
  undercutBotThresholdSeconds: 60
```

## baseSettings

| Key | Type | Default | Meaning |
|---|---|---|---|
| `token` | text | `""` | The bot token of your Discord application. |
| `botGuild` | number | `0` | The id of your Discord server. The bot registers its slash commands there, and only members of this server can log in to the web app. |
| `botOwner` | list of numbers | `[]` | Discord user ids that may use the admin endpoints of the web API. |

## database

| Key | Type | Default | Meaning |
|---|---|---|---|
| `host` | text | `"localhost"` | Host of the PostgreSQL server. |
| `port` | text | `"5432"` | Port of the PostgreSQL server. |
| `database` | text | `"db"` | Name of the database. |
| `schema` | text | `"lolorito"` | Schema for Lolorito's tables. It is created on start. |
| `user` | text | `"user"` | Database user. |
| `password` | text | `"pw"` | Password of the database user. |
| `poolSize` | number | `5` | Maximum number of database connections. |

## discordOauth

| Key | Type | Default | Meaning |
|---|---|---|---|
| `clientId` | text | `""` | OAuth2 client id of your Discord application. |
| `clientSecret` | text | `""` | OAuth2 client secret of your Discord application. |

The redirect for the Discord login is not set here. It is `<frontend address>/auth/callback`, see
[http](#http).

## http

| Key | Type | Default | Meaning |
|---|---|---|---|
| `host` | text | `"0.0.0.0"` | Address the web server listens on. |
| `port` | number | `8080` | Port the web server listens on. |
| `publicBaseUrl` | text | `"http://localhost:8080"` | The public address of the web app, for example `https://lolorito.example.com`. |
| `frontendBaseUrl` | text | `""` | Address the browser loads the web app from, if it differs from `publicBaseUrl`. Empty means `publicBaseUrl`. The Discord login redirect is this address plus `/auth/callback`, and users land here after the login. |
| `secureCookies` | true/false | `true` | Send session cookies with the `Secure` flag. Set it to `false` only for testing on plain HTTP. |
| `sessionCookieName` | text | `"lolorito_session"` | Name of the session cookie. |
| `sessionMaxAgeDays` | number | `90` | Days without any activity before a session ends. Every request moves this window forward. |
| `tokenEncryptionKey` | text | `""` | Base64 encoded 32 byte key that encrypts the Discord tokens of logged in users in the database. If empty, Lolorito creates one on start and saves it here. |
| `guildRecheckTtlMinutes` | number | `60` | Minutes after which a request checks again that the user is still on your server. |
| `guildRecheckTtlBackgroundMinutes` | number | `60` | Minutes after which the background check looks at a session again, even if it is idle. |
| `sweeperIntervalMinutes` | number | `15` | How often the background check runs. It also deletes expired sessions. |

Users who leave your server lose access to the web app right away. The checks above are a second line of defense.

## catalog

Lolorito refreshes its item icons, recipes and desynth data from XIVAPI in the background.

| Key | Type | Default | Meaning |
|---|---|---|---|
| `enabled` | true/false | `true` | Run the background refresh at all. |
| `initialDelaySeconds` | number | `15` | Seconds after start before the first refresh. |
| `intervalHours` | number | `24` | Hours between two refreshes. |

## universalis

| Key | Type | Default | Meaning |
|---|---|---|---|
| `regions` | list of texts | all five regions | Universalis regions to receive market data for. Allowed values: `Europe`, `North-America`, `Oceania`, `Japan`, `中国`. An empty list receives nothing. |

Lolorito only has market data for worlds in the regions you list. Fewer regions means less data to store.

## value

Settings of the market models and the price math.

| Key | Type | Default | Meaning |
|---|---|---|---|
| `mbTax` | decimal | `0.05` | Market board tax as a fraction. It is taken off every expected sale. |
| `mbTaxByDataCenter` | map | `{}` | Tax per data center. Keys are Universalis data center ids, values are fractions like `mbTax`. Data centers not listed use `mbTax`. |
| `modelRefreshMinutes` | number | `60` | How often the market models are fitted again. |
| `refitBatchSize` | number | `2000` | How many models are fitted per batch. |
| `minSamplesPerKey` | number | `5` | Below this many weighted sales a model counts as too thin to trust. |
| `priceHalfLifeDays` | number | `7` | Half life of a sale's weight in the price fit. Newer sales count more. |
| `rateWindowDays` | number | `14` | Days of sales used for the fit. Older sales are ignored. |
| `residualWindowDays` | number | `14` | Days of past prediction errors used to correct the next fit. |
| `residualShrinkagePriorK` | number | `20` | How strongly past prediction errors correct the model. A smaller value corrects faster. |
| `ghostListingWindowDays` | number | `14` | Window in days for spotting ghost listings. |
| `undercutBotThresholdSeconds` | number | `60` | Not used by the current version. |
| `craftSecondsPerUnit` | decimal | `5.0` | Seconds of your time one craft takes. It is counted as time spent when crafts are rated. |
| `listingFreshnessHours` | number | `24` | Listings older than this are ignored when the cheapest price for a craft ingredient or a desynth source is looked up. |
| `responseCacheSeconds` | number | `30` | How long answers for offers, plans and item details are cached. `0` turns the cache off. |
| `responseCacheMaxSize` | number | `1000` | Maximum number of cached answers per kind. |
| `alertScanIntervalMinutes` | number | `5` | How often alert rules are checked. |
| `alertWebhookUrl` | text | `""` | A URL that receives every fired alert as JSON, in addition to the Discord DM. A user's own webhook from the web app settings takes its place for that user's alerts. |

## planner

Defaults for the route planner. The web app sends its own values with every plan, so these only fill in values that
are missing from a request.

| Key | Type | Default | Meaning |
|---|---|---|---|
| `hopWeightGilPerSecond` | number | `100` | What one second of travel is worth in gil. |
| `tDcSeconds` | number | `15` | Seconds for one hop between two worlds of the same data center. |
| `tRegionSeconds` | number | `45` | Seconds for one hop between two data centers of the same region. |
| `maxWorlds` | number | `5` | Maximum number of worlds a plan visits to buy. |
| `candidateTopK` | number | `250` | How many of the best rated candidates the planner looks at. |
| `attentionBudgetHours` | decimal | `8.0` | Hours of your time a plan may take. |
| `attentionFraction` | decimal | `0.25` | Share of your attention a run gets. |
| `inventorySlots` | number | `140` | Free inventory slots. |
| `budget` | number | `1000000` | Gil you can spend. |
| `ilpMaxNodes` | number | `200000` | Search limit of the exact solver. Above it, the planner falls back to a faster, simpler method. |
| `retainerSlots` | number | `20` | How many different items the planner may put into the retainer basket. |
| `retainerListingSlots` | number | `40` | Sell listings across all your retainers. |
| `retainerListingStackTarget` | number | `20` | Units per listing when a retainer pick is split into stacks. |
| `retainerAttentionFraction` | decimal | `0.05` | Share of your attention for retainer picks. |
| `retainerShelfHoursThreshold` | decimal | `6.0` | Items expected to sit on the market at least this many hours go to the retainer basket. |
| `retainerBudgetFraction` | decimal | `0.4` | Not used by the current version. |
| `craftChainMaxDepth` | number | `3` | How many recipe levels deep the planner looks for crafts. Deeper ingredients are priced as buys. |

## links

| Key | Type | Default |
|---|---|---|
| `tos` | text | `""` |
| `invite` | text | see the full file above |
| `support` | text | `""` |
| `website` | text | `"https://rainbowdashlabs.github.io/lolorito/"` |
| `faq` | text | `"https://rainbowdashlabs.github.io/lolorito/faq"` |

These links are not used by the current version.

## Environment variables

Most keys can also be set with an environment variable. The name is the section prefix and the key in upper case,
with no extra underscores inside the key:

| Section | Prefix | Example |
|---|---|---|
| `baseSettings` | `BOT_` | `BOT_TOKEN`, `BOT_BOTGUILD` |
| `database` | `DB_` | `DB_HOST`, `DB_POOLSIZE` |
| `discordOauth` | `DISCORD_OAUTH_` | `DISCORD_OAUTH_CLIENTID`, `DISCORD_OAUTH_CLIENTSECRET` |
| `http` | `HTTP_` | `HTTP_PUBLICBASEURL`, `HTTP_SECURECOOKIES` |
| `catalog` | `CATALOG_` | `CATALOG_ENABLED` |
| `value` | `VALUE_` | `VALUE_MBTAX`, `VALUE_ALERTWEBHOOKURL` |
| `planner` | `PLANNER_` | `PLANNER_MAXWORLDS`, `PLANNER_TDCSECONDS` |
| `links` | `LINKS_` | `LINKS_SUPPORT` |

A set variable wins over the value in the file. These keys have no variable and can only be set in the file:
`baseSettings.botOwner`, `universalis.regions` and `value.mbTaxByDataCenter`.

:::warning
Lolorito writes the complete configuration back to `config.yaml` on every start, including values that came from
environment variables. A token or password you pass as a variable ends up in the file as well. Protect the
`config` folder like you would protect the variables.
:::
