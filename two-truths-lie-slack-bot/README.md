
# Two Truths & 1 Lie Slack Bot (Node.js)

## Files
- package.json - Deps (@slack/bolt)
- bot.js - Bot logic, game gen, respond
- .env - Add your tokens (create below)

## Setup
1. `cd two-truths-lie-slack-bot`
2. `npm install`
3. Create Slack App: https://api.slack.com/apps → New App → Bot Token + Signing Secret
4. Create `.env`:
```
SLACK_BOT_TOKEN=xoxb-your-bot-token
SLACK_SIGNING_SECRET=your-signing-secret
```
5. Invite bot to channel: `/invite @botname`
6. `npm start`

## Play
`hi` / `play` → Bot posts 3 statements, guess 1-3.

Console bot, no GUI.

