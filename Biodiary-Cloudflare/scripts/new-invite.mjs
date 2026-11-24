import { randomBytes, createHash } from 'node:crypto';

const count = process.argv[2] === undefined ? 1 : Number(process.argv[2]);
if (!Number.isInteger(count) || count < 1 || count > 60) {
  console.error('発行数は1〜60の整数で指定してください。例: node scripts/new-invite.mjs 5');
  process.exit(1);
}

const createdAt = Math.floor(Date.now() / 1000);
const expiresAt = createdAt + 14 * 86400;
const invites = Array.from({ length: count }, () => {
  const code = `BD-COM-${randomBytes(12).toString('hex').toUpperCase()}`;
  return { code, hash: createHash('sha256').update(code).digest('hex') };
});

console.log(`招待コード（1人に1つずつ伝えるもの・14日間有効）:`);
invites.forEach(({ code }, index) => console.log(`${index + 1}. ${code}`));
console.log('\nCloudflare D1に一度だけ登録するSQL:');
console.log('INSERT INTO community_invites(code_hash, created_at, expires_at) VALUES\n' +
  invites.map(({ hash }) => `  ('${hash}', ${createdAt}, ${expiresAt})`).join(',\n') + ';');
