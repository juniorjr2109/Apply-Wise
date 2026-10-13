'use strict';
const crypto = require('node:crypto');
const { promisify } = require('node:util');

const scrypt = promisify(crypto.scrypt);
const PARAMS = { N: 16384, r: 8, p: 1 };
const KEY_LENGTH = 64;

/** Hash format: scrypt$N$r$p$<salt b64>$<hash b64> — self-describing for future upgrades. */
async function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const hash = await scrypt(String(password), salt, KEY_LENGTH, PARAMS);
  return ['scrypt', PARAMS.N, PARAMS.r, PARAMS.p, salt.toString('base64'), hash.toString('base64')].join('$');
}

async function verifyPassword(password, stored) {
  const parts = String(stored || '').split('$');
  if (parts.length !== 6 || parts[0] !== 'scrypt') return false;
  const [, N, r, p, saltB64, hashB64] = parts;
  const expected = Buffer.from(hashB64, 'base64');
  const actual = await scrypt(String(password), Buffer.from(saltB64, 'base64'), expected.length, {
    N: Number(N), r: Number(r), p: Number(p)
  });
  return actual.length === expected.length && crypto.timingSafeEqual(actual, expected);
}

/** A real hash of a random secret, verified when a username does not exist so
 *  "unknown user" and "wrong password" take the same time (no user enumeration). */
let dummyHashPromise;
function dummyHash() {
  dummyHashPromise ??= hashPassword(crypto.randomBytes(16).toString('hex'));
  return dummyHashPromise;
}

const newToken = () => crypto.randomBytes(32).toString('base64url');
const newRef = () => crypto.randomBytes(12).toString('base64url');
const sha256 = (value) => crypto.createHash('sha256').update(String(value)).digest('hex');
const randomPassword = () => crypto.randomBytes(12).toString('base64url');

module.exports = { hashPassword, verifyPassword, dummyHash, newToken, newRef, sha256, randomPassword };
