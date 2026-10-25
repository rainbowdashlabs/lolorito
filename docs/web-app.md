---
title: Web app
sidebar_position: 5
---

The web app is where most of Lolorito lives. It runs on the same address as the bot's web server, see
[Setup](./setup.md#public-address-and-https). The menu at the top links to **Dashboard**, **Offers**, **Planner**,
**Baskets**, **Alerts**, **Desynth** and **Shopping**. Your avatar opens **Settings**.

The footer lets you pick the theme (dark, light or follow system) and the language: English, German, French or
Japanese.

## Login

You log in with Discord. Click **Continue with Discord** and allow the request. Only members of the server set in
`baseSettings.botGuild` can log in. Everyone else gets "You must be a member of the configured Discord to log in."

Every page needs a login, except [shared baskets](#baskets) that their owner made public.

## Home world and offer filter

Everything is rated against your home world: the world where you sell. Your home world is part of your offer filter.
The web app and the [Discord commands](./discord.md) share this filter.

You can set your home world on the [Settings](#settings) page, in the filter of the [Offers](#offers) page, or with
`/offers filter` in Discord. Until it is set, most pages have nothing to show.

## Dashboard

The start page after login. It shows:

- **Home world**: your home world, the sales on it over the last 24 hours, and a **Plan a run** button that opens the
  planner with the gil you enter under **Gil in pocket**.
- **Top opportunities right now**: the six best offers on your data center by gil per hour.
- **Model calibration**: how close the market models were to the real sale prices over the last days.
- **Sales momentum**: items on your home world that are trending up or losing steam, with a forecast for the next
  24 hours.
- **Worst-calibrated keys**: the items where the models miss the most.
- **Calibration over time**, **Capacity** and **Refresh cadence**: how the models do over time, how much data
  Lolorito covers, and how long its background work takes.

## Offers

A list of items to buy on another world and sell on your home world. It is ranked by gil per hour: the expected profit
divided by the time you spend on it.

The filter on the left sets:

- **Home world** and **Search scope**: look on your data center or on the whole region.
- **Listing freshness**: only listings seen within this many hours count.
- **Budget (gil)** and **Free slots**: limit each row to what you can afford and carry.
- **Min gil/hour**, **Shelf time class** (any, under 1 hour, under 1 day, overnight) and switches to hide worlds with
  undercut bots, items with ghost listings and items with too few sales for a reliable model.

The table shows for each offer the source world, buy price, quantity, the expected net sell price after tax, total
profit, its spread (σ), the expected time on the shelf, gil per hour and a confidence rating. Expand a row to see the
listings on the source world and on your home world. You can add an offer to your [basket](#baskets) or open the
item.

## Item page

Opens when you click an item. Switch between normal and high quality at the top. The page shows:

- **List for**: a suggested sell price on your home world, and why. It also shows the historical mean and median
  price.
- **Market model**: the fitted expected sell price, the median, the spread and how the chance to sell changes with
  your price.
- **Sales history (7 days)** and the sales trend of the item.
- **Listings on data center**: current listings on every world of your data center.
- **Actions**: what you can do with the item. **Resale** compares buying on the cheapest world with selling at home.
  **Desynth** shows what breaking it down yields. **Craft** shows the recipe and its ingredients, each priced as buy
  or craft.

## Planner

Plans a shopping run in one go: which worlds to visit, in which order, and what to buy on each one. Set the
parameters on the left and click **Plan the run**.

The parameters include budget, carry slots, attention budget and fraction, hop weight, maximum number of worlds and
a one-off home world. **Allow crafts** and **Allow desynthesis** add crafts and desynths to the plan. They use the
skill levels from your [settings](#settings). **Retainer overnight** puts slow sellers into a separate retainer basket.
Each field explains itself in the app. You can save the whole form as a preset and load it again later.

The result is a list of stops. Each stop lists what to buy and at what price. Mark a stop done and the planner
plans the rest of the run again. At the end you get what to list, in which stack sizes and at which price. Crafts
can be copied as a list for Teamcraft.

## Shopping

Plans the purchases for one craftable item. Pick the item, how many you want and the recipe. For each ingredient
Lolorito picks the cheaper of buying or crafting it. You can switch any ingredient between **Buy** and **Craft**,
and price a buy as high quality. Below you get the shopping run: one stop per world, the biggest spend first, with
the total cost and the expected sale.

## Desynth

The desynth explorer lists items you can buy and break down, ranked by expected gil per hour on your home world. It
compares each one with simply reselling the item. Filter by buy scope (data center or region), class and minimum
desynth level. With no class picked, it shows every class your desynth levels in [settings](#settings) qualify for.

## Baskets

The basket collects items you want to buy. Add items from the offers table or an item page. The basket button at the
top opens it. The working basket is kept in your browser.

- **Basket** (full view) lets you edit quantities, see the total spend, and open the planner with **Plan a run**.
- **Save** stores the basket on the server under a name, with a visibility:
  - **Private**: only you.
  - **Signed-in users with the link**: anyone who is logged in and has the link.
  - **Anyone with the link**: no login needed.
  Saved baskets get a share link.
- **My baskets** lists your saved baskets. Load one to replace your working basket or add it to it.

A shared basket opens read only. Visitors can load it into their own basket.

## Alerts

Alert rules watch the market for you. Fill in the form and click **Create alert**:

| Field | Choices |
|---|---|
| Item | Any item. |
| Scope | A single world or a whole data center. |
| Quality | Either, HQ only or NQ only. |
| Trigger | Price drops to or below a price, price rises to or above a price, sales in the last 24 hours reach a percentage of the usual daily volume, or listings drop to or below a count. |
| Cooldown (min) | Minutes before the same rule can fire again. Default 60, at most one week. |

Lolorito checks the rules every few minutes (`value.alertScanIntervalMinutes`). When one fires, the bot sends you a
[direct message](./discord.md#alert-messages). You can pause, resume, test and delete each rule. **Test** sends the
alert right away with a made-up value past the threshold.

Under [Settings](#settings) you can also add your own webhook. Then every alert is also sent there as JSON:

```json
{
  "ruleId": "<rule id>",
  "userId": 123456789012345678,
  "itemId": 5057,
  "worldId": 33,
  "dataCenterId": null,
  "hq": null,
  "kind": "price_below",
  "threshold": 1000,
  "observed": 950
}
```

`kind` is `price_below`, `price_above`, `sale_volume_spike` or `listing_count_drop`. One of `worldId` and
`dataCenterId` is set.

:::warning
The webhook body is plain JSON, not a Discord message. A Discord channel webhook does not accept it.
:::

## Settings

Your account and preferences:

- **Defaults**: your home world and your listing freshness window.
- **Lodestone character**: link your character with its Lodestone id or URL. Lolorito shows the character and imports
  your crafter levels. **Sync now** reads it again, **Unlink** removes it.
- **My retainers**: declare your retainers by name and world. A retainer shows as seen once Universalis has seen one
  of its listings.
- **Class skill levels**: your crafting and desynth level per class. The planner and the desynth explorer only suggest
  what you can do. **Import crafter levels from Lodestone** fills in the crafting levels.
- **Alert webhook**: your own webhook URL for alerts, see [Alerts](#alerts).
- **Active sessions**: every browser you are logged in with. Revoke any you don't know.
- **Danger zone**: log out of every browser, or reset your offer filter to the defaults.
