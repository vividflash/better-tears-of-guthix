# Efficient Tears of Guthix

Feeling inefficient at collecting tears?\
This plugin highlights the most efficient spot(s) to pick in Tears of Guthix.

## Features

Runs on worlds whose stream order is `GGGBBB` or `BBBGGG`. On any other
order the panel shows `Order not supported` and no wall is highlighted.

**Walls**

- **Highlight**: outlines the wall to go to and the next best one while you
  should move, in **Best wall colour** and **Second best colour**. `Wall`,
  `Tile` or `Both`, default `Both`.
- **Mark bad walls**: thin outline on the ground in front of walls where no
  blue stream can land next, or a green one can, in **Bad wall colour**.
  Default `off`.
- **Mark possible walls**: thin outline on walls a blue stream that is about
  to move can land on, in **Possible wall colour**. Default `on`.
- **Stream countdown**: ticks until the blue stream on your wall moves, orange
  for the last `3`. Default `on`.

**Interface**

- **Info panel**: the stream order of your world (`GGGBBB` is three green
  streams moving, then three blue), the session ticks left, and at Juna
  `Enter: now` or `Enter in:` with the ticks to wait before the last dialog
  click. Default `on`.
- **Tear change**: each tear gained or lost, beside the tear counter. Default
  `on`.

**Left click**

- **Deprioritise green walls**: a left click on a wall with a green stream
  walks there, `Collect-from` stays on right click. Default `on`.
- **Deprioritise Juna**: on a world whose order is not supported, a left click
  on Juna walks, her options stay on right click. Default `on`.

**Worlds**

- **Allow BBBGGG worlds**: `off` treats `BBBGGG` as not supported. Blue
  streams move first there, so leaving a dry wall late costs tears. Default
  `on`.
