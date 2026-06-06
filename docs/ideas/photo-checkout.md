# Photo Checkout (Visual Cashier)

> Status: Refined idea — ready to expand into a spec.
> Last refined: 2026-06-06

## Problem Statement

**How might we** let a cashier photograph a counter of *our own products* — which have **no barcodes** — and get an accurate, itemized, priced receipt, fast enough for a line and cheap enough to run on every sale?

## Context (locked decisions)

| Decision | Answer | Implication |
|---|---|---|
| Who / where | Cashier at a counter | Speed + trust matter; integrates into a checkout flow |
| Item set | Our own catalog (closed set) | Closed-set recognition — far easier than "anything" |
| Item labeling | **No / mostly no barcodes** | Visual recognition is mandatory, not optional |
| Price source | Our own price list | Pricing is a deterministic DB lookup, not a guess |
| Constraint | Cost-sensitive | Per-scan cloud cost must trend to ~$0 at scale |
| Platform | Android (mobile) | — |

## Recommended Direction

**A cloud-vision prototype that recognizes our catalog items from a single counter photo, then prices them deterministically from our own list — with a mandatory 1-tap cashier confirm — architected so the recognition hot path can later move on-device to kill per-scan cost.**

Three rules that shape everything:

1. **The model identifies; the database prices.** The multimodal LLM returns catalog matches (SKU IDs + quantities + per-item confidence). Our database applies the price and computes tax/total. The model is never trusted with money. This makes every cent auditable and lets us swap the recognition engine later without touching pricing logic.

2. **Draft, don't decide.** The app proposes a draft receipt; the cashier confirms, edits a quantity, removes a wrong item, or adds a missed one — then commits. Wrong-item recognition becomes a one-tap fix instead of a refund. Non-negotiable for anything touching cash.

3. **Cloud now, on-device later — behind a vendor-agnostic interface.** Recognition sits behind a single `Recognizer` interface that takes a photo + catalog context and returns `[{sku, qty, confidence, bbox?}]`. Phase 1 implements that interface with a cloud multimodal LLM to validate that closed-set recognition is accurate enough at all — **which vendor (Claude, GPT, Gemini, an open model, etc.) is a swappable config choice, not a hardcoded dependency.** Phase 2 adds a second implementation backed by on-device image embeddings (match counter crops against enrolled product reference photos), dropping marginal cost per scan toward zero and removing the internet dependency. Pricing, catalog, and UI are unchanged by any swap — that's the point of rule #1.

The catalog needs an **enrollment** step regardless of engine: each product gets a name, price, and a few reference photos. Those photos seed the LLM prompt context now and become the embedding gallery later — so enrollment work is never thrown away.

### Rough data flow (Phase 1)

```
Cashier taps "Scan"
  → capture + downscale photo (cost/latency control)
  → Recognizer.recognize(photo, catalog)   // pluggable: any cloud vision LLM, later on-device
  → returns: [{sku, qty, confidence, bbox?}, ...]
  → app maps SKUs → own price list, computes subtotal/tax/total
  → render DRAFT receipt (low-confidence items flagged)
  → cashier confirms / edits
  → commit sale → (inventory decrement, payment, receipt)
```

## Key Assumptions to Validate

- [ ] **Closed-set visual recognition is accurate enough on real counters.** *Test:* assemble 50 representative multi-item counter photos, run them through the cloud model, measure item-level precision/recall and how often the cashier must correct. Target a correction rate low enough to still beat manual entry.
- [ ] **One photo of the whole counter beats item-by-item entry.** *Test:* time cashiers on 2-, 5-, and 10-item orders, photo vs. manual. The multi-item shot likely only wins above ~5 items — find the real break-even.
- [ ] **Per-scan cloud cost is tolerable for the prototype window.** *Test:* measure tokens/cost per scan at chosen image resolution × expected daily volume; confirm it's acceptable until the on-device migration lands. Benchmark a few vendors/tiers and start with a cost-efficient one before reaching for the largest model.
- [ ] **Cashiers accept a confirm step instead of full magic.** *Test:* watch 3–5 cashiers use the draft-and-confirm flow; do they trust it, and is the edit UX faster than the errors it prevents?
- [ ] **Enrollment is light enough to actually get done.** *Test:* time enrolling 20 products end-to-end; if it's painful, the catalog never gets populated and nothing works.

## MVP Scope

**In:**
- Product catalog with name, price, and reference photo(s) per item — plus a basic enrollment screen.
- Single-photo capture → cloud recognition → SKU+quantity results.
- Deterministic pricing from the local price list; subtotal + tax + total.
- Draft receipt with per-item confidence flags; cashier can edit qty / remove / add manually.
- Commit a sale and show/save a receipt.
- Per-scan cost + accuracy logging (so we can prove the assumptions).

**Out (for MVP):**
- On-device recognition (Phase 2) and any vendor-specific tuning — but the `Recognizer` interface ships in MVP so engines/vendors are swappable from day one.
- Payment processing, full inventory management, returns/voids, multi-store, accounts/roles.
- Self-checkout / shopper-facing mode.
- Barcode scanning (catalog has none).

## Not Doing (and Why)

- **Letting the AI set prices** — money must be deterministic, auditable, and owned by our database, not a model's guess.
- **Open-set "recognize anything"** — we own a closed catalog; scoping to it is what makes recognition accurate *and* cheap. Generality would tank both.
- **Building on-device ML first** — premature. Validate that recognition works and is wanted before investing in the harder engine. Cloud is the fastest path to that proof.
- **Full POS suite (payments, inventory, returns) in v1** — the camera-to-priced-receipt loop is the risky, novel part; prove that before bolting on commodity POS features.
- **Auto-committing sales without confirmation** — guarantees refunds and erodes cashier trust on day one.

## Open Questions (resolve during spec)

- How many distinct SKUs in the catalog, and how visually similar are they? (Near-identical variants — e.g., flavors — are the recognition failure mode and may need a disambiguation UI.)
- How are items typically arranged at the counter — spread out, stacked, overlapping? Drives whether one photo is even feasible vs. a short multi-angle capture.
- What's expected daily transaction volume? Sets the real cloud-cost ceiling and how urgent the on-device migration is.
- Which vision model/vendor balances accuracy vs. cost for this task? (Benchmark 2–3 providers behind the same `Recognizer` interface; start with a cheaper tier.) What's the abstraction boundary — raw image bytes in, structured `{sku, qty, confidence}` out — so any provider conforms?
- What happens on low confidence or an unrecognized item — silent drop, "unknown item" placeholder, or force manual add? (Lean toward never silently dropping.)
- Where does the catalog/price data live and who maintains it (on-device DB, synced backend)?
```
