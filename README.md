# Efficient Tears of Guthix

Feeling inefficient at collecting tears?\
This plugin highlights the most efficient spot(s) to pick in Tears of Guthix.

## Features

Works on `GGGBBB` and `BBBGGG` worlds.

**Walls**

- **Highlight**: the wall to go to and the next best one, in **Best wall
  colour** and **Second best colour**. `Wall`, `Tile` or `Both`, default
  `Both`.
- **Mark bad walls**: walls no blue stream can land on next, or a green one
  can, in **Bad wall colour**. Default `off`.
- **Mark possible walls**: walls the next blue stream can land on, in
  **Possible wall colour**. Default `on`.
- **Stream countdown**: ticks until your wall's blue stream moves. Default
  `on`.
- **Dry wall countdown**: in red, ticks until the wait at a dry wall ends.
  Default `off`.
- **Reaction speed**: ticks you take to click, `0` or `1`. At `1`, on the
  last blue stream of a burst the next wall shows as soon as the first stream
  lands, a tick earlier, so you can hover it and click on the countdown.
  Default `0`.

**Interface**

- **Info panel**: stream order, session ticks left, and at Juna when to
  enter. Default `on`.
- **Separate timer box**: the entry timing and ticks left in a box of their
  own. Default `off`.
- **XP skill**: at Juna, the skill that gets the XP. Default `on`.
- **Refresh XP skill**: picks the skill again after each XP gain while Juna
  stays loaded, otherwise only when Juna comes into view. Default `off`.
- **Tear change**: tears gained or lost, beside the tear counter. Default
  `on`.
- **Login reminder**: chat message on login once the next run is open,
  counted from the last run the plugin saw. Default `off`.

**Left click**

- **Deprioritise green walls**: left click on a green wall walks. Default
  `on`.
- **Deprioritise Juna**: until the stream order is synced and supported,
  left click on Juna walks. Default `on`.

**Worlds**

- **Allow BBBGGG worlds**: default `on`.
