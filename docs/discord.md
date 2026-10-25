---
title: Discord bot
sidebar_position: 4
---

The bot has three slash commands: `/check`, `/top` and `/offers`. It registers them only on the server set in
`baseSettings.botGuild`. It also sends your [price alerts](#alert-messages) as direct messages.

## Your home world

Every command works from your home world. Your home world is part of your offer filter, which the bot and the
web app share. Set it in one of these places:

- in the web app, on the [Settings](./web-app.md#settings) page or in the filter of the
  [Offers](./web-app.md#offers) page,
- with `/offers filter world:<your world>`.

Without a home world, `/check` shows no listings, `/offers get` shows "No match" and `/top` shows "No items match".

## /check

Shows the current listings of one item on every world of your home data center. The answer is only visible to you.

There is one subcommand per language of the item name:

| Subcommand | Item name in |
|---|---|
| `/check de` | German |
| `/check en` | English |
| `/check fr` | French |
| `/check jp` | Japanese |

| Option | Required | Meaning |
|---|---|---|
| `name` | yes | The item name. It suggests names while you type. |
| `hq` | no | `True` for high quality only, `False` for normal quality only. Leave it out for both. |

For each world you see a table with price per unit, amount and total, and when the listings were last updated. The
title links to the item on Universalis.

## /top

Lists the top items on your home world or your home data center, one item per page. Everyone in the channel sees the
answer. Each page shows the item's
market volume, interest, popularity, sales, views, minimum and average price, sold amounts and number of listings.

| Option | Meaning |
|---|---|
| `scope` | `WORLD` (default) or `DATACENTER`. |
| `order` | What to sort by: `SALES`, `MARKET_VOLUME` (default), `INTEREST`, `POPULARITY`, `MIN_PRICE` or `AVG_PRICE`. |
| `hq` | `True` for high quality only, `False` for normal quality only. |
| `min_sales` | Minimum number of sales. |
| `min_popularity` | Minimum popularity. |
| `min_interest` | Minimum interest. |
| `min_market_volume` | Minimum market volume. |
| `min_price` | Minimum price. |
| `min_avg_price` | Minimum average price. |

`scope` and `order` suggest their values while you type. Any other value gets a reply with the allowed ones. All
options are optional.

## /offers

Finds items that are cheaper on another world than they sell for on your home world. `/offers` has two subcommands.

:::note
`/offers` is for server administrators by default. Server admins can allow it for other members or roles under
**Server Settings > Integrations**.
:::

### /offers filter

Changes your saved offer filter and shows it. Every option is optional. Options you leave out keep their current
value. The filter is the same one the web app uses.

| Option | Range | Default | Meaning |
|---|---|---|---|
| `world` | | none | Your home world. It suggests worlds while you type. |
| `limit` | 1 to 10000 | 1000 | How many offers to fetch. |
| `unit_price` | 1 or more | 1000 | Minimum price of a single unit. |
| `factor` | 1 or more | 2.0 | Minimum profit factor between buy price and sell price. |
| `profit` | 1 to 1000000 | 100 | Minimum profit. |
| `effective_profit` | 1 to 1000000 | 10000 | Minimum effective profit. |
| `popularity` | 0 to 100 | 0 | Minimum popularity. |
| `refresh_hours` | 1 to 120 | 1 | Maximum age of a listing in hours. |
| `market_volume` | 0 to 100 | 0 | Minimum market volume of the item on your world. |
| `interest` | 0 to 100 | 0 | Minimum interest in the item on your world. |
| `sales` | 0 or more | 0 | Minimum number of sales in the last 7 days. |
| `views` | 0 or more | 0 | Minimum number of views in the last 7 days. |
| `target` | | `DATA_CENTER` | Where to look for offers: `DATA_CENTER` or `REGION`. |

The defaults apply until you change a value. You can reset the whole filter in the web app under
[Settings](./web-app.md#settings).

### /offers get

Shows the best offers for your saved filter, one item per page. The answer is only visible to you. Each page shows
the item's statistics on your home world, a recommendation with how many to buy, what to sell for and the expected
profit range, and a table per source world with price, amount, total, factor and profit.

## Alert messages

You create alert rules in the web app on the [Alerts](./web-app.md#alerts) page. When a rule fires, the bot sends you
a direct message. It has the item name in the title and one of these lines:

| Trigger | Message |
|---|---|
| Price drops | Cheapest listing on *world* dropped to *price* gil. |
| Price rises | Cheapest listing on *world* climbed to *price* gil. |
| Sales spike | Sales on *world* in the last 24 h are at *percent* of the usual daily volume. |
| Listings drop | Only *count* listing(s) left on *world*. |

The message also shows the threshold, the quality (`high`, `normal` or `either`) and the rule id. For a rule on a
whole data center, the message names the data center by its id.

:::tip
The bot can only send you a DM if your privacy settings allow direct messages from members of the server. Use the
**Test** button on the Alerts page to check.
:::
