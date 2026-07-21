# MineTopFakePlayers Pro v2

## Requires
- Java 21
- Paper 1.21.10 backend
- Citizens matching your Paper build
- Velocity 3.4+
- TAB installed on Velocity for fake visual TAB slots

## Commands
`/fake create <name>`
`/fake remove <name>`
`/fake tphere <name>`
`/fake tp <name>`
`/fake skin <name> <MinecraftName>`
`/fake follow <name> [off]`
`/fake sit <name>`
`/fake animation <name> <swing|hurt|jump>`
`/fake list`
`/fake tabexport`

## TAB entries
Run `/fake tabexport`, then open `plugins/MineTopFakePlayers/TAB-layout-snippet.yml` and paste its `fixed-slots` into TAB's `layout` section on Velocity. Enable TAB layout and run `/btab reload`.

Velocity plugin controls the multiplayer-list count. `sample-names: []` keeps the hover list empty.
