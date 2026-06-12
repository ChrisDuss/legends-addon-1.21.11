# Dev Server

This folder gives you a local Paper server for vault UI testing without touching your real server.

## What it does

- Downloads a stable Paper `1.21.11` server jar from Paper's official downloads service.
- Builds a local mock `PlayerVaultsX` plugin if you do not provide a real one.
- Keeps the server runtime isolated in `dev-server/runtime`.

## Scripts

- `setup-dev-server.ps1`: downloads Paper, copies drop-in jars, builds the mock plugin, writes basic server config.
- `start-dev-server.ps1`: runs the local Paper server on port `25566`.
- `verify-dev-server.ps1`: boots the server once, waits for startup, then sends `stop`.
- `setup-dev-server.cmd`, `start-dev-server.cmd`, `verify-dev-server.cmd`: Windows launchers that call the PowerShell scripts with execution-policy bypass.

## Quick start

- Double-click `start-dev-server.cmd`, or run `.\dev-server\start-dev-server.cmd` from the repo root.
- The first boot downloads Paper and builds the mock plugin, so it takes longer than later boots.
- Connect your client to `localhost:25566`.

## Mock plugin commands

- `/pv 1` through `/pv 14`: open a numbered vault.
- `/pv`: open the storage menu.
- `/pvgui`: open the storage menu.
- `/storage`: open the storage menu.
- `/pvmax <number>`: change your unlocked vault count for testing locked/unlocked states.
- `/pvmax <player> <number>`: op/admin only.

## Using the real plugin later

Put your licensed jars into `dev-server/dropins`, then rerun setup:

- `PlayerVaultsX.jar`
- `PlayerVaultsGUI.jar`
- `Vault.jar`
- any economy plugin if your GUI setup needs one

If a real `PlayerVaults` or `PlayerVaultsX` jar is present in `dropins`, the local mock plugin is skipped automatically.

## Notes

- The server uses `online-mode=false` and port `25566` by default for local testing.
- The mock plugin is intentionally narrow: it only reproduces the storage menu plus numbered vault workflow your mod needs.
- `verify-dev-server` force-stops the process after Paper reaches `Done (...)`; it is only a smoke test, not the normal way to run the server.
