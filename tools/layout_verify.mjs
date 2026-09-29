#!/usr/bin/env node
// Visual verification of PlatformLayout: prints the top-layer role map for a
// given configuration, exactly as the build would place it.
// Usage: node tools/layout_verify.mjs [size countX countZ linkWidth channelWidth]
const [size = 16, countX = 3, countZ = 3, linkWidth = 3, channelWidth = 0] = process.argv.slice(2).map(Number);

const CELL = size;
const GAP = linkWidth > 0 ? linkWidth + 2 : 0;
const SIZE_X = countX * CELL + (countX - 1) * GAP;
const SIZE_Z = countZ * CELL + (countZ - 1) * GAP;
// builder is the center of the CENTER CELL — expansion grows outward around it
const CI = Math.floor((countX - 1) / 2);
const CJ = Math.floor((countZ - 1) / 2);
const ANCHOR_X = CI * (CELL + GAP) + (CELL - 1) / 2;
const ANCHOR_Z = CJ * (CELL + GAP) + (CELL - 1) / 2;
console.log(`anchor(cell-grid) = (${ANCHOR_X}, ${ANCHOR_Z}) of ${SIZE_X}x${SIZE_Z}`);

function cellRole(lx, lz, sx, sz, gi, gj) {
  const ring = (lx === 0 && gi === 0) || (lx === sx - 1 && gi === countX - 1)
    || (lz === 0 && gj === 0) || (lz === sz - 1 && gj === countZ - 1);
  if (ring) return 'B';
  const cxMin = sx % 2 === 0 ? sx / 2 - 1 : (sx - 1) / 2;
  const cxMax = sx % 2 === 0 ? sx / 2 : cxMin;
  const czMin = sz % 2 === 0 ? sz / 2 - 1 : (sz - 1) / 2;
  const czMax = sz % 2 === 0 ? sz / 2 : czMin;
  if (sx >= 3 && sz >= 3 && lx >= cxMin && lx <= cxMax && lz >= czMin && lz <= czMax) return 'C';
  if (channelWidth > 0) {
    const cx = (sx - 1) / 2, cz = (sz - 1) / 2;
    const half = (channelWidth - 1) / 2;
    if (Math.abs(lx - cx) <= half) return 'T';
  }
  return 'F';
}

function roleAt(x, z) {
  const periodX = CELL + GAP;
  const periodZ = CELL + GAP;
  const gi = Math.floor(x / periodX);
  const gj = Math.floor(z / periodZ);
  const px = x % periodX;
  const pz = z % periodZ;
  const inCellX = px < CELL;
  const inCellZ = pz < CELL;
  if (inCellX && inCellZ) return cellRole(px, pz, CELL, CELL, gi, gj);
  const stripX = !inCellX;
  const stripZ = !inCellZ;
  const interior = (stripX && px > CELL && px < periodX - 1) || (stripZ && pz > CELL && pz < periodZ - 1);
  if (interior) return 'R';
  return 'B';
}

console.log(`size=${size} grid=${countX}x${countZ} link=${linkWidth} -> ${SIZE_X}x${SIZE_Z}`);
const LEGEND = { B: 'B', F: '.', R: 'R', C: 'C', T: 'T' };
let out = '';
for (let z = 0; z < SIZE_Z; z++) {
  let row = '';
  for (let x = 0; x < SIZE_X; x++) {
    row += (x === ANCHOR_X && z === ANCHOR_Z) ? '@' : LEGEND[roleAt(x, z)];
  }
  out += row + '\n';
}
console.log(out);
// checks
let centers = 0, borders = 0, roads = 0, fills = 0;
for (let z = 0; z < SIZE_Z; z++) for (let x = 0; x < SIZE_X; x++) {
  const r = roleAt(x, z);
  if (r === 'C') centers++;
  else if (r === 'B') borders++;
  else if (r === 'R') roads++;
  else if (r === 'F') fills++;
}
const expectedCenters = countX * countZ * (size % 2 === 0 ? 4 : 1);
console.log(`centers=${centers} (expect ${expectedCenters}) borders=${borders} roads=${roads} fills=${fills}`);
