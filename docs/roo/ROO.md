# Roo: character guide

Roo is the shared mascot across all our apps and videos: Clearoo first, Eddie's videos next, and future apps after that. This guide keeps Roo looking and sounding the same everywhere.

## Who Roo is
- **A small, round kangaroo** with tall ears and a pouch. Roo carries away your clutter.
- **Personality:** cheerful, a little dramatic, never mean. Celebrates loudly, gets nervous about streaks, sulks cutely and bounces back fast.
- **Role:** a friend nudging you along, not a teacher lecturing. Explains things the way a mate would.

## How Roo talks
- **Short and warm:** one idea per line, everyday words, one emoji at most.
- **Speaks in first person** and speaks to "you": "I found some photos you forgot about 👀".
- **Never guilt-trips.** "Save our streak? 🔥" works. "You failed" doesn't.
- **Example lines:**
  - Excited: "G'day! I'm Roo 🦘"
  - Worried: "Psst… the day's almost over."
  - Proud: "Goal smashed! I'm doing a happy hop."

## Look
| Part | Colour |
|---|---|
| Body | `#FFA24C` (gradient up to `#FFBC70`); feet, arms and paws `#F0883A` |
| Belly / muzzle | `#FFE6CC` |
| Inner ears | `#FF8FA8` |
| Eyes, nose, outlines | `#3A2440` |
| Cheeks | `#FF7F9E` at about 45% opacity |

- **Brand gradient behind Roo:** `#6E4BFF` → `#FF5F9E` → `#FFB36B`.
- **Dark background:** `#130F1E`.
- **Font:** Nunito (Black or ExtraBold for headlines), under the SIL Open Font License.

## Moods (`moods/`)
| File | Use it when |
|---|---|
| `hopeful` | Default, inviting, asking |
| `happy` | Good news, agreement |
| `excited` | Big reveals, the hook of a video |
| `proud` | Goals reached, conclusions, "and that's why…" |
| `love` | Something sweet or delightful |
| `shocked` | "Wait, what?!" facts, plot twists |
| `worried` | Warnings, risks, "be careful" |
| `sad` | Bad news, myths busted on a sad note |
| `sleepy` | Night-time, boring things, sleep topics |

## Outfits (`outfits/`)
Classic, party hat, scarf, sunglasses, wizard hat, headphones, cape, crown.

In Clearoo, outfits are rewards that users unlock. In videos they can match the topic: wizard hat for science "magic", headphones for music, crown for "kings of…" facts.

## Source of truth
- **Roo is drawn in code:** `app/src/main/java/com/clearoo/app/mascot/MascotPainter.kt`, on a 200×200 grid. That means Roo stays sharp at any size and new moods or outfits are cheap to add.
- **The PNGs here are 1024×1024 with transparent backgrounds.** Regenerate them from the painter whenever Roo changes.

## Rules
- **Keep Roo's shape and colours as they are:** don't stretch, recolour or redraw.
- **Leave space around Roo:** at least ⅛ of Roo's width.
- **Don't put words in Roo's mouth that are mean, political or misleading.**
