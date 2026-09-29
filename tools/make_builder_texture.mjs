// Regenerates the platform builder block texture (16x16, fully opaque).
// Usage: node tools/make_builder_texture.mjs
import { deflateSync } from 'node:zlib';
import { writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const W = 16, H = 16;
const img = new Uint8Array(W * H * 4);

const set = (x, y, r, g, b) => {
  if (x < 0 || y < 0 || x >= W || y >= H) return;
  const i = (y * W + x) * 4;
  img[i] = r; img[i + 1] = g; img[i + 2] = b; img[i + 3] = 255;
};
const noise = (x, y) => ((x * 73 + y * 151) % 7) - 3;

for (let y = 0; y < H; y++) {
  for (let x = 0; x < W; x++) {
    const n = noise(x, y);
    set(x, y, 136 + n, 136 + n, 141 + n);
  }
}
for (let i = 0; i < W; i++) { set(i, 0, 69, 69, 73); set(i, H - 1, 69, 69, 73); }
for (let i = 0; i < H; i++) { set(0, i, 69, 69, 73); set(W - 1, i, 69, 69, 73); }
for (let i = 2; i <= 13; i++) { set(i, 2, 102, 102, 108); set(i, 13, 102, 102, 108); }
for (let i = 2; i <= 13; i++) { set(2, i, 102, 102, 108); set(13, i, 102, 102, 108); }
for (const [rx, ry] of [[4, 4], [11, 4], [4, 11], [11, 11]]) {
  set(rx, ry, 51, 51, 56); set(rx + 1, ry, 51, 51, 56);
  set(rx, ry + 1, 51, 51, 56); set(rx + 1, ry + 1, 51, 51, 56);
  set(rx, ry, 166, 166, 172);
}
for (let y = 5; y <= 10; y++) {
  for (let x = 5; x <= 10; x++) set(x, y, 157, 157, 163);
}
for (const vy of [7, 9]) {
  for (let x = 6; x <= 9; x++) set(x, vy, 114, 114, 122);
}

const crcTable = new Int32Array(256).map((_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c;
});
function crc32(buf) {
  let c = 0xffffffff;
  for (const byte of buf) c = crcTable[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const out = Buffer.alloc(8 + data.length + 4);
  out.writeUInt32BE(data.length, 0);
  out.write(type, 4, 'ascii');
  data.copy(out, 8);
  out.writeUInt32BE(crc32(out.subarray(4, 8 + data.length)), 8 + data.length);
  return out;
}

const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(W, 0);
ihdr.writeUInt32BE(H, 4);
ihdr[8] = 8;  // bit depth
ihdr[9] = 6;  // RGBA
const raw = Buffer.alloc(H * (1 + W * 4));
for (let y = 0; y < H; y++) {
  raw[y * (1 + W * 4)] = 0; // filter: none
  Buffer.from(img.buffer, y * W * 4, W * 4).copy(raw, y * (1 + W * 4) + 1);
}

const png = Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  chunk('IHDR', ihdr),
  chunk('IDAT', deflateSync(raw)),
  chunk('IEND', Buffer.alloc(0)),
]);

const outPath = resolve(dirname(fileURLToPath(import.meta.url)),
  '../src/main/resources/assets/industrial_platform/textures/block/builder/platform_builder.png');
writeFileSync(outPath, png);
console.log('wrote', outPath, png.length, 'bytes');
