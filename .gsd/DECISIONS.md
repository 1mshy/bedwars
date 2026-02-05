# DECISIONS.md

> **Effective Date**: 2026-02-05

## Decision Log

| Date | ID | Title | Status |
|------|----|-------|--------|
| 2026-02-05 | ADR-001 | Feature Selection | Accepted |

### ADR-001: Feature Selection
**Context**: User requested "Awareness" features with "Safe" tolerance.
**Decision**: Implementing HUD-based tools (Resources, Radar, Scoreboard) that rely solely on client-side data rendering.
**Consequences**:
- No automated inputs (Safe).
- No ESP/Wallhacks (Safe).
- High implementation effort on UI rendering.
