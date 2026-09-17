// Original synthesized alert: no samples or copyrighted recordings are used.
// Run with Node.js to reproduce android/app/src/main/res/raw/yanji_ringtone.wav.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const output = resolve(dirname(fileURLToPath(import.meta.url)), '../android/app/src/main/res/raw/yanji_ringtone.wav');
const rate = 24000;
const frames = rate * 2;
const data = Buffer.alloc(frames * 2);
for (let frame = 0; frame < frames; frame++) {
  const t = frame / rate;
  const segment = t < 0.36 ? t : t >= 0.56 && t < 0.92 ? t - 0.56 : -1;
  // Two 360ms pulses followed by silence; a 12ms envelope avoids clicks.
  if (segment >= 0) {
    const envelope = Math.min(1, segment / 0.012, (0.36 - segment) / 0.012);
    const value = 0.23 * envelope * (Math.sin(2 * Math.PI * 440 * t) + Math.sin(2 * Math.PI * 660 * t));
    data.writeInt16LE(Math.round(value * 32767), frame * 2);
  }
}
const header = Buffer.alloc(44);
header.write('RIFF', 0); header.writeUInt32LE(36 + data.length, 4); header.write('WAVE', 8);
header.write('fmt ', 12); header.writeUInt32LE(16, 16); header.writeUInt16LE(1, 20);
header.writeUInt16LE(1, 22); header.writeUInt32LE(rate, 24); header.writeUInt32LE(rate * 2, 28);
header.writeUInt16LE(2, 32); header.writeUInt16LE(16, 34);
header.write('data', 36); header.writeUInt32LE(data.length, 40);
mkdirSync(dirname(output), { recursive: true });
writeFileSync(output, Buffer.concat([header, data]));
console.log(`Generated 2 seconds of 24 kHz mono PCM16: ${output}`);
