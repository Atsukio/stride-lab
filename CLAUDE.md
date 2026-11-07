@AGENTS.md

## Claude Code specifics

- The shell is Git Bash on Windows; use the Bash tool with forward slashes. PowerShell is also available.
- Read a file before editing it, and re-read the surrounding code before changing a shared helper:
  `ui/ResultVisuals.kt` is used by the results screen, the setup guide and the share card.
- Run the full check (`./gradlew testDebugUnitTest lintDebug assembleDebug -q`) before saying a change is done,
  and compare the lint count with the baseline in AGENTS.md.
- For a long build or emulator start, run it in the background rather than sleeping.
- End commit messages with the Co-Authored-By line the session gives you.
- Keep this file and AGENTS.md current. When you learn a new gotcha, command or user preference, add it to
  AGENTS.md (shared with other agents), not here.
