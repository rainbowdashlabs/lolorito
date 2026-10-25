---
title: Lolorito
sidebar_position: 1
---

Lolorito helps you earn gil on the Final Fantasy XIV market board. It reads live market data from
[Universalis](https://universalis.app) and tells you what to buy on other worlds, where to buy it and what to list it
for on your home world.

Lolorito has two parts. Both run in the same program and share the same data.

- **A Discord bot** with slash commands for quick checks: prices of an item across your data center, top items on
  your world and the best cross-world offers. It also sends your price alerts as direct messages.
  See [Discord bot](./discord.md).
- **A web app** for the bigger picture. You log in with Discord and get offers ranked by gil per hour, item details,
  a route planner for a shopping run, a desynth explorer, a crafting shopping list, saved baskets and alert rules.
  See [Web app](./web-app.md).

Lolorito is made for one Discord server. The bot registers its commands on that server, and only members of that
server can log in to the web app.

## What it does

- Finds cross-world offers: items that are cheap on another world of your data center or region and sell for more on
  your home world.
- Rates every offer by expected profit, time on the shelf and gil per hour, based on a market model fitted to recent
  sales.
- Plans a shopping run: which worlds to visit, in which order, and what to buy on each one, within your budget and
  inventory space.
- Checks crafts and desynthesis: whether crafting an item or breaking one down beats buying and reselling.
- Watches the market for you and sends a Discord DM when a price, the sales volume or the number of listings crosses
  your threshold.
- Links your Lodestone character to import your crafter levels.

## Where to start

- **Run your own instance**: start with [Setup](./setup.md), then look up every setting on
  [Configuration](./configuration.md).
- **Use the bot**: every command is on [Discord bot](./discord.md).
- **Use the web app**: every page is on [Web app](./web-app.md).

## Links

- Source: [GitHub](https://github.com/rainbowdashlabs/lolorito), licensed under the AGPL-3.0
- Market data: [Universalis](https://universalis.app)
