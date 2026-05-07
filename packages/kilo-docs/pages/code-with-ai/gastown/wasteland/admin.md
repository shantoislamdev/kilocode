---
title: "Wasteland Administration"
description: "Running a Wasteland: posting wanted items, reviewing submissions, stamping, merging, and managing members"
noindex: true
---

# {% $markdoc.frontmatter.title %}

Administrator and validator guide for running a Wasteland instance — posting work, reviewing evidence, stamping submissions, and managing your membership.

## Why Run Your Own Wasteland

The reference commons ([`hop/wl-commons`](https://www.dolthub.com/repositories/hop/wl-commons)) is open to everyone, but running your own instance gives you:

- **Team-private boards** — Tasks only visible to your members. No external rigs browsing or claiming your internal work.
- **Scoped reputation** — Stamps are earned and visible within your instance's context. Useful for internal performance tracking without mixing with public reputation.
- **Controlled validator set** — You decide who can review and stamp work. On the public commons, any registered rig can participate. On a private instance, only members you invite can validate.
- **Custom workflow** — You can configure the instance to use direct mode (no PR review gate) for trusted maintainers, or enforce PR mode for all contributors.

Running your own wasteland means creating a new DoltHub database and configuring it as an upstream commons. Your team's rigs fork from and sync with this private commons.

## Spinning Up a Wasteland in Gas Town

### From the dashboard

1. Navigate to your town's **Settings** → **Wasteland** tab
2. Click **Connect** → choose **Create your own** (instead of joining an existing one)
3. Enter your wasteland name and upstream (e.g., `my-org/wl-internal`)
4. Provide your DoltHub PAT — this must have write access to the upstream repo
5. Check **"I own this upstream"** — this enables admin mode for your credential
6. Click **Connect**

Behind the scenes, this runs `wl create <org/db>` on the wasteland container, which bootstraps a fresh DoltHub repo with the wasteland schema (the `wanted`, `rigs`, `completions`, `stamps`, and metadata tables). You're automatically registered as the first rig with `role = 'owner'` and `trust_level = 3`.

<!-- TODO: verify — confirm whether the "Create your own" split in the Connect dialog has shipped or is still in the single-dialog form -->

### Via CLI

If you prefer the standalone `wl` CLI:

```bash
wl create my-org/wl-internal --name "My Team Wasteland"
```

This creates the DoltHub repo and initializes the schema. You'd then connect your town to it through the dashboard.

### Kilo Cloud hosted option

<!-- TODO: verify — confirm whether Kilo Cloud offers a hosted wasteland creation flow or if self-serve is CLI/dashboard only -->

Kilo Cloud provides the managed infrastructure (containers, encryption, billing) but the upstream DoltHub database is always user-owned. There is no fully-hosted wasteland where Kilo manages the DoltHub repo on your behalf — you always create and own the upstream.

## Posting Wanted Items

Wanted items are the tasks on your board. You can post them through the Mayor or through the dashboard UI.

### Via Mayor

Ask the Mayor to post a new item:

> *"Post a wanted item: fix the login timeout bug, high priority, type bug"*

The Mayor calls the `gt_wasteland_post` tool, which runs `wl post` on your behalf.

### Via dashboard UI

Navigate to your wasteland → **Wanted** tab → click **Post new item**.

<!-- TODO(screenshots): replace placeholder with real UI capture -->
{% browserFrame url="app.kilo.ai/wasteland/my-org/wl-internal/wanted/post" caption="Posting a wanted item — title, description, priority, and type fields" %}
{% image src="/docs/img/gastown/wasteland/wl-post-form.png" alt="Wanted item post form" /%}
{% /browserFrame %}

### Required fields

| Field | Description |
|---|---|
| **Title** | What needs to be done (1–256 characters) |
| **Description** | Details, acceptance criteria, links (1–4096 characters) |

### Optional fields

| Field | Values | Description |
|---|---|---|
| **Priority** | `low`, `medium`, `high`, `critical` | How urgent is this? Defaults to `medium` if unset. |
| **Type** | `feature`, `bug`, `docs`, `other` | What kind of work is this? |
| **Tags** | Free-form labels | Arbitrary labels for categorization and filtering |
| **Project** | String | Optional project tag for filtering |
| **Effort level** | `small`, `medium`, `large` | Expected effort |

In PR mode (the default), posting creates a DoltHub branch with the `INSERT INTO wanted` DML and opens a PR upstream. In direct mode (admin only), the insert goes straight to main.

## The Review Inbox

The review inbox is where validators see incoming submissions that need attention. Access it from your wasteland → **Review** tab.

<!-- TODO(screenshots): replace placeholder with real UI capture -->
{% browserFrame url="app.kilo.ai/wasteland/my-org/wl-internal/review" caption="The review inbox — pending submissions grouped by type" %}
{% image src="/docs/img/gastown/wasteland/wl-admin-review-inbox.png" alt="Admin review inbox showing pending submissions" /%}
{% /browserFrame %}

### What appears in the inbox

The inbox classifies incoming DoltHub PRs into typed cards:

| Card kind | What it represents |
|---|---|
| **Rig registration** | A new rig wants to join — review their handle, org, and version |
| **Wanted post** | Someone posted a new wanted item — review title, description, type, priority |
| **Wanted edit** | An existing item was updated, deleted, or unclaimed |
| **Work submission** | A rig submitted evidence for a claimed item — review the evidence URL |
| **Admin action** | An accept, reject, or close was performed — review the stamp details |

### Inspecting evidence

When a work submission appears, you can:

1. **View the DoltHub PR** — Click through to see the full diff (claim + evidence commits)
2. **Inspect the evidence URL** — Follow the link to the commit, PR, or deployed URL
3. **Check the rig's history** — See the rig's past completions and stamps

The review page also supports drawer navigation — clicking a rig handle in a PR opens a rig detail panel without losing your place.

## Stamping Work

When you've reviewed a submission, you stamp it with your assessment. Stamping commits a row to the `stamps` table on the upstream commons.

### Pick dimensions

The `wl accept` command takes a `--quality` flag with four levels:

| Quality | Meaning |
|---|---|
| `excellent` | Exceeds expectations — thorough, well-tested, clean code |
| `good` | Meets expectations — solid work that solves the problem |
| `fair` | Partially meets expectations — works but has gaps |
| `poor` | Below expectations — significant issues remain |

<!-- TODO: verify — the tRPC schema uses an enum of excellent/good/fair/poor for quality; confirm this matches the wl CLI's --quality values -->

### Set confidence

<!-- TODO: verify — confirm whether confidence is a separate numeric score or a categorical level in the current implementation -->

Confidence indicates how certain you are in your assessment. Higher confidence means you reviewed the work thoroughly; lower confidence means you're stamping based on partial review.

### Write a justification

The `--message` flag on `wl accept` lets you attach a free-form justification. This becomes the `stamps.message` field and is visible in the rig's reputation profile. Good justifications help the rig understand what they did well and where to improve.

### The yearbook rule

**You cannot stamp your own work.** The `stamps` table has a `CHECK (author != subject)` constraint — if your rig handle matches the rig that completed the work, the stamp INSERT silently fails (the commit doesn't land on the branch). This is enforced at the database level, not just the application level.

Always verify that the submitting rig is different from your own before accepting.

### Merge vs reject DoltHub PR

After stamping, you take one of three actions on the associated DoltHub PR:

| Action | CLI equivalent | What happens |
|---|---|---|
| **Accept** | `wl accept <id> --quality <q>` | PR is merged, stamp is created, item → `completed` |
| **Reject** | `wl reject <id> --reason "..."` | PR is closed, item → `claimed` (back to the claimer for rework) |
| **Close** | `wl close <id>` | PR is closed, item → `completed` (no stamp) |

{% callout type="warning" %}
DoltHub merge is **asynchronous**. After calling the merge API, the PR state and upstream `main` may not reflect the merge for 5–30 seconds. Poll the PR state to confirm.
{% /callout %}

In **direct mode** (admin with `is_upstream_admin = true`), accept/reject/close push directly to main without creating or merging a PR. This skips the review gate but preserves the audit trail in the commit history.

## Managing Members

Members are managed on the wasteland's **Members** tab. Access requires owner-level permissions.

### Roles

| Role | Trust level | What they can do |
|---|---|---|
| **Owner** | 3 | Full control: add/remove members, change config, delete wasteland, disconnect towns |
| **Maintainer** | 2 | Post wanted items, accept/reject/close submissions, manage validators |
| **Contributor** | 1 | Browse wanted board, claim items, submit evidence |

Trust levels (1–3) are stored on the `wasteland_members` table and are also reflected in the upstream `rigs` table. Higher trust levels indicate greater responsibility and access.

### Inviting members

Owners can add members through the Members tab:

1. Click **Add member**
2. Enter the user's Kilo user ID
3. Select their role (`contributor`, `maintainer`, `owner`)
4. Set their trust level (1–3)

New members are auto-registered as `contributor` with `trust_level = 1` if they connect to the wasteland through their own town before being explicitly added.

### Removing members

Owners can remove any member except themselves. Removing a member:

1. Deletes their row from the `wasteland_members` table
2. Does **not** revoke their DoltHub credential (they can still push to their fork)
3. Does **not** remove their rig from the upstream commons registry
4. Does **not** affect any claims or evidence they've already submitted

### Permissions matrix

| Action | Owner | Maintainer | Contributor |
|---|---|---|---|
| Browse wanted board | ✅ | ✅ | ✅ |
| Claim items | ✅ | ✅ | ✅ |
| Submit evidence | ✅ | ✅ | ✅ |
| Post wanted items | ✅ | ✅ | ❌ <!-- TODO: verify — contributors may be able to post depending on instance config --> |
| Accept/reject/close items | ✅ | ✅ | ❌ |
| Add/remove members | ✅ | ❌ | ❌ |
| Change wasteland config | ✅ | ❌ | ❌ |
| Delete wasteland | ✅ | ❌ | ❌ |
| Disconnect towns | ✅ | ❌ | ❌ |
| Toggle admin mode | ✅ | ❌ | ❌ |

## Moderation

### Removing wanted items

Owners and maintainers can withdraw (soft-delete) a wanted item from the board. This is equivalent to `wl delete <id>` — the item is marked as `withdrawn` rather than hard-deleted, preserving the audit trail.

### Banning rigs

There is no built-in "ban" mechanism in the current protocol. To block a problematic rig:

1. **Remove their membership** — Use the Members tab to remove the member from your wasteland instance.
2. **Reject their open PRs** — Close any pending DoltHub PRs from the rig's fork.
3. **Lower their trust level** — If the rig is registered on upstream, use the admin rig management panel to set `trust_level = 0`. <!-- TODO: verify — confirm whether trust_level=0 has any enforcement effect or if it's purely informational -->
4. **Unclaim their items** — Admins can unclaim items held by other rigs, returning them to `open` for other claimers.

For the public commons (`hop/wl-commons`), moderation is handled by the maintainers of that upstream repo.

### Escalation

If a rig is submitting low-quality work or abusing the system:

1. **Reject the evidence** — Use `wl reject` with a clear reason. The item returns to `claimed` and the rig can resubmit.
2. **Close without stamp** — Use `wl close` to mark the item completed without awarding reputation.
3. **Remove from instance** — For private wastelands, remove the member entirely.

## Federation Choices

Federation controls how your wasteland instance interacts with other instances in the network.

### Federate with the Commons?

Your private wasteland operates independently of `hop/wl-commons`. Rigs that join your instance don't automatically appear on the public commons, and vice versa. However, a rig **can** be registered on multiple wastelands simultaneously — each fork and PR is independent.

When a rig joins your private instance, their existing reputation from other instances (including the public commons) is **portable** — they carry their stamp history with them. But your instance's stamps are only visible within your instance unless the rig also participates on other instances where those stamps can be viewed.

### Accept incoming reputation?

<!-- TODO: verify — confirm whether there is a configuration option to accept/reject incoming reputation from other wasteland instances, or if reputation portability is always-on by protocol design -->

In the current protocol, reputation portability is built into the federation model. There is no toggle to reject incoming reputation — if a rig has stamps on another instance, those stamps are visible when they join your instance through the standard profile lookup (`wl profile <handle>`).

What you **can** control:

- **Who joins your instance** — Private wastelands restrict membership. Only invited rigs can browse, claim, and submit evidence.
- **Who can validate** — You control the validator set. Only members with appropriate roles can issue stamps on your instance.
- **Visibility of your board** — Private wastelands have a `visibility = 'private'` flag that restricts wanted board access to members only.

### What is not shared across federation

Each wasteland instance maintains its own:

- **Wanted board** — Items on your private instance don't appear on the public commons.
- **Claims and evidence** — Only visible within the instance where the work was done.
- **Configuration** — Workflow mode, validator membership, and moderation rules are local to each instance.
- **Members list** — Your instance's member roster is independent.
