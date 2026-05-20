# Review: In-World Overlays

Files:
- `src/main/java/com/imshy/bedwars/render/BedwarsOverlayRenderer.java`
- `src/main/java/com/imshy/bedwars/render/NameTagManager.java`
- `src/main/java/com/imshy/bedwars/render/LastSeenArrowRenderer.java`

## Summary

This unit renders the in-world (world-space) and screen-space overlays: per-player
threat/loadout/tracking nametag labels, the invisible-player indicator, fireball and
ender-pearl trajectory arcs, the pre-throw prediction arc, the bed-defense face
highlight, generator labels, and the screen-edge "last seen" compass arrows.

Overall the GL handling is disciplined: every `pushMatrix` is paired with a `popMatrix`,
and every method that mutates GL state restores it at the end
(`enableDepth` / `depthMask(true)` / `enableLighting` / `disableBlend` /
`color(1,1,1,1)` for world overlays; `enableTexture2D` / `disableBlend` / `color` reset
for the screen-space arrow). I found no unbalanced push/pop and no GL leak.

Two interpolation facts are worth stating up front so they are not mistakenly re-flagged:

- The threat / loadout / enemy-tracking labels receive caller-supplied `x/y/z` that are
  the **Forge-interpolated** `RenderLivingEvent.Specials.Post` event coordinates
  (`onRenderLiving` in `BedwarsRuntime`). They are correct — no jitter.
- The fireball, projectile, pre-throw arc, bed-defense, generator, and invisible-player
  paths all self-interpolate the local player's position from `prevPos*` /
  `pos*` using the `partialTicks` they are handed from `RenderWorldLastEvent`. These are
  correct as well.

The one genuine interpolation defect is in `LastSeenArrowRenderer`, which uses the raw
(non-interpolated) `rotationYaw` — see the High finding below.

`NameTagManager` is a clean pure-math singleton: no GL, no entity iteration, no threading
concerns. No findings.

A threading note: the tracked-enemy / fireball / projectile collections are read here on
the render thread and mutated on the client-tick and chat threads. In Forge 1.8.9
`ClientChatReceivedEvent` is dispatched on the **main client thread** (via
`PacketThreadUtil.checkThreadAndEnqueue` rescheduling), and tick + render handlers also
run on the main thread, so there is no genuine cross-thread `ConcurrentModificationException`
between chat clears and render iteration. The only background-thread writers (the
HypixelAPI executor `StatsCallback`) touch `chatDetectedPlayers`, which is `synchronized`,
not these maps. The remaining concern is therefore a maintainability/aliasing one, not a
crash — see the Medium finding.

## Findings

### [HIGH] Last-seen arrows use non-interpolated player yaw → arrow jitter

- **Location:** `LastSeenArrowRenderer.java:69`
  (call site `BedwarsRuntime.onRenderOverlay` → `lastSeenArrowRenderer.render(resolution, mc, enemyTrackingService)`, no `partialTicks` passed).
- **Issue:** The arrow bearing is computed from the raw per-tick value
  `mc.thePlayer.rotationYaw` (`double yawRad = Math.toRadians(mc.thePlayer.rotationYaw)`).
  This is the snapped, end-of-tick yaw, not the smoothly interpolated camera yaw. The
  arrows are drawn every frame in `RenderGameOverlayEvent.Post`, which can run many frames
  per tick.
- **Impact:** While the player turns the view, the world (and hence the true on-screen
  direction to the tracked enemy) rotates smoothly at the frame rate, but the arrow's
  forward/right basis only updates 20×/sec. The arrows visibly stutter/jump relative to
  the smoothly-rotating scene during any view movement — exactly when this overlay is most
  used (chasing a juking enemy).
- **Suggested fix:** Thread `partialTicks` through the call. `RenderGameOverlayEvent.Post`
  exposes `event.partialTicks`; pass it into `render(...)` and interpolate:
  `float yaw = mc.thePlayer.prevRotationYaw + (mc.thePlayer.rotationYaw - mc.thePlayer.prevRotationYaw) * partialTicks;`
  then `double yawRad = Math.toRadians(yaw);`. (Note: also interpolate the player position
  used at L100–101 with `prevPosX/Z` for full consistency, though position drift per tick
  is far smaller than yaw drift and is secondary.)

### [MEDIUM] GL state is restored to assumed defaults rather than saved/restored

- **Location:** `BedwarsOverlayRenderer.java` — end of `renderThreatLabel`,
  `renderEnemyLoadoutLabel`, `renderEnemyTrackingLabel`, `renderInvisiblePlayerIndicator`,
  `renderGeneratorLabel`, `renderFireballTrajectories`, `renderProjectileTrajectories`,
  `renderPreThrowArc`, `renderBedDefenseAssist`; `LastSeenArrowRenderer.java:190-192`.
- **Issue:** Each method tears down its state by *setting* what it assumes the surrounding
  pipeline default is (`enableDepth()`, `depthMask(true)`, `enableLighting()`,
  `disableBlend()`, `color(1,1,1,1)`) rather than saving the prior state and restoring it
  (e.g. `GlStateManager.pushAttrib` / `popAttrib`, or capturing the values entered with).
  Within `renderEnemyTrackingLabel` the depth state is also toggled mid-method
  (`enableDepth()` for the item row, then `disableDepth()`, then `enableDepth()` at the
  end), which is correct today but brittle.
- **Impact:** Works as long as the event fires in the standard post-render context, which
  it does. But it is fragile: if another mod (or a future code path) renders these labels
  from a context with non-default lighting/blend, the "restore" will silently corrupt that
  caller's state instead of returning it to what it was. This is a latent
  cross-mod-compatibility hazard, not a current crash.
- **Suggested fix:** Bracket each overlay method's state changes with
  `GL11.glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)` /
  `GL11.glPopAttrib()` (GlStateManager in 1.8.9 does not wrap these), or snapshot/restore
  the specific flags toggled. At minimum, document the assumed entry state.

### [MEDIUM] `getAllTrackedEnemies()` exposes the live mutable map (leaky abstraction)

- **Location:** consumed at `LastSeenArrowRenderer.java:48,77`; source
  `EnemyTrackingService.getAllTrackedEnemies()` returns `state.trackedEnemies` directly
  (a plain `HashMap`, no wrapper).
- **Issue:** The renderer iterates the live backing map. The fireball and projectile
  services return `Collections.unmodifiableCollection(...)` for the analogous case; this
  one does not, so the API surface is inconsistent. As established in the Summary, there
  is no cross-thread CME today (all writers are on the main thread), so this is an
  encapsulation/consistency issue rather than a crash.
- **Impact:** A renderer could accidentally mutate tracking state; and if any future writer
  is moved onto a background thread (the fetch executor already exists), iteration here
  would begin throwing `ConcurrentModificationException` with no compile-time warning. The
  inconsistency with the sibling services also invites confusion.
- **Suggested fix:** Return a read-only view (`Collections.unmodifiableMap(...)`) or a
  snapshot, matching `FireballTrackingService` / `ProjectileTrackingService`. If iteration
  ever spans threads, snapshot to a `new ArrayList<>(map.entrySet())` before looping.

### [LOW] Label vertical clamp handles only the bottom edge, not the top

- **Location:** `LastSeenArrowRenderer.java:135`.
- **Issue:** The label-Y clamp is one-directional:
  `if (labelY > screenHeight - 12) labelY = (int)(arrowY - LABEL_OFFSET - 8);`. It pushes a
  too-low label above the arrow, but there is no symmetric guard for the flipped result
  going off the top of the screen (`labelY < 0`). For an arrow near the top edge the
  fallback `arrowY - LABEL_OFFSET - 8` can be negative.
- **Impact:** Minor visual clipping — the distance label can be partially drawn off the top
  of the screen for arrows pointing roughly "behind and above". Cosmetic only.
- **Suggested fix:** After the flip, clamp again: `if (labelY < 2) labelY = 2;`.

### [LOW] Per-frame allocations in hot render paths

- **Location:**
  `LastSeenArrowRenderer.java:141-150` (`colorForThreat` returns `new float[]{...}` per
  arrow per frame);
  `BedwarsOverlayRenderer.java` `computeFaceCorners` (returns `new double[]` per bed face
  per frame — up to 6 faces × beds × frame) and the `StringBuilder` allocated per call in
  `renderEnemyTrackingLabel`.
- **Issue:** Each render frame allocates short-lived arrays/objects proportional to the
  number of tracked entities / bed faces.
- **Impact:** Low. These are small objects and the entity counts in Bedwars are modest, so
  GC pressure is minor — but it is avoidable churn in code that runs every frame.
- **Suggested fix:** Return packed `int` colors / reuse a thread-local scratch array, or
  switch `colorForThreat` to return a pre-baked `0xRRGGBB` int. For `computeFaceCorners`,
  fill a reused buffer. Optional.

### [LOW] `renderInvisiblePlayerIndicator` dereferences `mc.thePlayer` without an internal guard

- **Location:** `BedwarsOverlayRenderer.java` `renderInvisiblePlayerIndicator` (~L151,
  reads `mc.thePlayer.prevPosX/posX` etc. with no local null check).
- **Issue:** The method assumes `mc.thePlayer` is non-null. It is, because the only caller
  (`BedwarsRuntime.onRenderWorldLast`) guards `mc.thePlayer == null` before invoking. But
  the method is `public` and the precondition is undocumented.
- **Impact:** None today (caller-guarded). A future caller could NPE.
- **Suggested fix:** Add a defensive `if (mc.thePlayer == null) return;` at the top, or
  document the precondition.

### [LOW] Magic literal `7` for `GL_QUADS` in immediate-mode draws

- **Location:** `BedwarsOverlayRenderer.java` (`drawWireframeBox` / `drawFaceQuad` and the
  trajectory draws use the raw integer mode `7`).
- **Issue:** `worldRenderer.begin(7, ...)` uses the magic number `7` where
  `GL11.GL_QUADS` would be self-documenting (the arrow renderer correctly uses
  `GL11.GL_TRIANGLES`).
- **Impact:** Readability only.
- **Suggested fix:** Replace `7` with `GL11.GL_QUADS`.

## Improvements

- **Cross-unit call-site bug (observed, not a finding for this unit's files):** in
  `BedwarsRuntime.onRenderWorldLast`, the invisible-player indicator loop is nested inside
  `if (ModConfig.isGeneratorDisplayEnabled())`. As a result invisible-player alerts only
  render when the *generator display* toggle is also on, even though they have their own
  `isInvisiblePlayerAlertsEnabled()` flag. The two features are independent and should not
  share a gate. (Belongs to the runtime unit, flagged here because it directly defeats one
  of this unit's overlays.)
- Plumb `partialTicks` into `LastSeenArrowRenderer.render(...)` (see the High finding) and,
  while doing so, interpolate the player position used for the distance/direction math for
  full smoothness.
- Consider extracting the repeated "set ESP GL state / restore ESP GL state" preamble and
  postamble (used by nearly every method in `BedwarsOverlayRenderer`) into small
  `beginEsp()` / `endEsp()` helpers, ideally `pushAttrib`/`popAttrib`-based (see the
  Medium GL finding). This removes duplication and fixes the save/restore fragility in one
  place.
- Align `EnemyTrackingService.getAllTrackedEnemies()` with the unmodifiable-view contract
  used by the fireball/projectile services for API consistency.
