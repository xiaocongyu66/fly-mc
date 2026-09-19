# FlyBrain (Fabric mod)

Drives Minecraft mobs with a **real fruit-fly connectome** running in
[fly-server](https://github.com/xiaocongyu66/fly-server) — an OpenAI-style
LIF simulation API. Game state becomes sensory stimulus; the brain's VNC
motor readout (spiking neurons, bucketed by id into forward / turn /
jump-attack channels) becomes movement.

## Install
1. Run fly-server (see its README), then drop this jar into `mods/`.
2. First launch writes `config/flybrain.json` — point `baseUrl` at your
   fly-server, pick `stimRegion` (a region of the loaded substrate) and
   `targetType` (e.g. `minecraft:zombie`).

## Behavior note
The connectome is untrained: expect stimulus-reactive locomotion
(threat proximity → agitation), not goal-directed intelligence.
Train the brain with fly-server's post-training and plug the
`.flydelta` file in — the mob's behavior changes accordingly.
