
const { App } = require('@slack/bolt');
require('dotenv').config();

// Create custom app + bot token at https://api.slack.com/apps
// Set SLACK_BOT_TOKEN=xoxb-...
// Set SLACK_SIGNING_SECRET=...

const app = new App({
  token: process.env.SLACK_BOT_TOKEN,
  signingSecret: process.env.SLACK_SIGNING_SECRET
});

function generateStatements() {
  const truths = [
    "I have never left my home country.",
    "I can play a musical instrument.",
    "I have been skydiving.",
    "I speak 3+ languages.",
    "I have a tattoo.",
    "I am vegetarian.",
    "I have run a marathon.",
    "I can code in Python."
  ];
  const lies = [
    "I was born on Feb 29.",
    "I have met a celebrity.",
    "I can juggle 5 balls.",
    "I have been to space.",
    "I own a pet tiger.",
    "I am a secret agent.",
    "I won the lottery."
  ];
  
  // Pick 2 truths, 1 lie random
  const truth1 = truths[Math.floor(Math.random() * truths.length)];
  const truth2 = truths[Math.floor(Math.random() * truths.length)];
  const lie = lies[Math.floor(Math.random() * lies.length)];
  
  const statements = [truth1, truth2, lie].sort(() => Math.random() - 0.5);
  return statements.map((s, i) => `${i+1}. ${s}`).join('\\n');
}

app.message(/^(hi|hello|start game)/i, async ({ message, say }) => {
  await say(`Hello <@${message.user}>! Ready for 2 Truths 1 Lie? Say "play"`);
});

app.message(/play/i, async ({ message, say }) => {
  const statements = generateStatements();
  await say({
    blocks: [{
      type: 'section',
      text: {
        type: 'mrkdwn',
        text: `*2 Truths 1 Lie Game!*\n\nGuess which is the lie:\n${statements}\n\nReply with number 1-3`
      }
    }]
  });
});

app.message(/^\d$/i, async ({ message, say }) => {
  const guess = parseInt(message.text);
  const lieIndex = 2; // Demo: always 3rd is lie (randomize in prod)
  const result = guess === lieIndex ? 'Correct! 🎉' : 'Wrong! Try again! 😅';
  await say(result);
});

(async () => {
  await app.start(process.env.PORT || 3000);
  console.log('Two Truths Lie Slack Bot running!');
})();

