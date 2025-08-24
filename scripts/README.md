Run scripts

- `run-dev.ps1` — start the app in PowerShell using the `dev` profile (loads `application-dev.yml`).
- `run-dev.sh` — same for Unix shells.

Usage (PowerShell):
```
.\scripts\run-dev.ps1
```

Notes:
- `application-dev.yml` contains local dev configuration and is ignored by git; it will override values from `application.yml` when the `dev` profile is active.
