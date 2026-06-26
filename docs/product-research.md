# Product Research

Poke is a proactive AI assistant built by The Interaction Company of California. Its core product idea is that the assistant lives inside familiar messaging channels rather than requiring users to open a dedicated AI app.

## What Poke Is Used For

From Poke's public site and docs, common uses include:

- managing email and drafting/sending messages
- scheduling meetings and managing calendar workflows
- setting reminders and recurring automations
- searching the web with source-aware responses
- interacting with integrations such as Notion, Gmail, GitHub, Linear, and Oura
- receiving proactive check-ins, progress updates, and reminders
- using recipes to package repeatable assistant behaviors and required integrations

## Why This Android Client Exists

Poke's strongest first-party experience is positioned around messaging channels, including Apple Messages with rich actions. Android users can use supported channels such as RCS, WhatsApp, or Telegram, but a dedicated Android app can provide:

- a faster dedicated Android path when RCS delivery feels uneven
- a stable native inbox for Poke conversations
- Android-native action buttons for approvals, quick replies, URLs, and templates
- reliable push notifications independent of carrier or RCS behavior
- foreground live updates through backend SSE
- a bridge to Poke API, MCP/SSE handlers, JSON-RPC tools, and webhook-ingested data

## Positioning Guardrails

- This project is independent and should not imply official Poke ownership.
- Avoid using Poke logos or proprietary messaging platform assets.
- Describe the app as a client/companion for Poke users, not as a replacement for Poke.
- Keep the default experience focused on assistant workflows: tasks, reminders, integrations, progress, and actionable replies.

## Sources

- https://poke.com/
- https://poke.com/docs
- https://poke.com/docs/api
- https://poke.com/docs/mcp-servers
- https://poke.com/docs/creating-recipes
- https://poke.com/faq
- https://poke.com/explore
