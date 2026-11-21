// Stand-in launcher icons (grayscale, like Enough renders real installed icons).
const ICONS = {
  instagram: `<rect width="48" height="48" rx="12" fill="#5a5a5a"/><rect x="11" y="11" width="26" height="26" rx="8" fill="none" stroke="#fff" stroke-width="3.2"/><circle cx="24" cy="24" r="6.2" fill="none" stroke="#fff" stroke-width="3.2"/><circle cx="31.6" cy="16.4" r="2" fill="#fff"/>`,
  youtube: `<rect width="48" height="48" rx="12" fill="#f2f2f2"/><rect x="8" y="13" width="32" height="22" rx="7" fill="#6e6e6e"/><path d="M21 19 L30 24 L21 29 Z" fill="#fff"/>`,
  reddit: `<rect width="48" height="48" rx="24" fill="#7a7a7a"/><ellipse cx="24" cy="28" rx="13" ry="9" fill="#fff"/><circle cx="35" cy="21" r="3.2" fill="#fff"/><circle cx="13" cy="21" r="3.2" fill="#fff"/><circle cx="19" cy="27" r="2.3" fill="#7a7a7a"/><circle cx="29" cy="27" r="2.3" fill="#7a7a7a"/><path d="M19 32 Q24 35 29 32" stroke="#7a7a7a" stroke-width="1.8" fill="none" stroke-linecap="round"/><path d="M24 19 L26 11 L32 12.5" stroke="#fff" stroke-width="1.8" fill="none"/><circle cx="33" cy="12.5" r="2.4" fill="#fff"/>`,
  tiktok: `<rect width="48" height="48" rx="12" fill="#111"/><path d="M27 11 V28.5 a6.5 6.5 0 1 1 -6.5 -6.5" stroke="#bdbdbd" stroke-width="4.2" fill="none" transform="translate(1.2 1.2)"/><path d="M27 11 V28.5 a6.5 6.5 0 1 1 -6.5 -6.5" stroke="#fff" stroke-width="4.2" fill="none"/><path d="M27 11 q1.5 6.5 8.5 7.5" stroke="#fff" stroke-width="4.2" fill="none"/>`,
  x: `<rect width="48" height="48" rx="12" fill="#111"/><path d="M14 13 H20.5 L34 35 H27.5 Z" fill="#fff"/><path d="M33 13 L15 35" stroke="#fff" stroke-width="2.6"/>`,
  chrome: `<circle cx="24" cy="24" r="22" fill="#8f8f8f"/><path d="M24 2 A22 22 0 0 1 43.05 13 H24 Z" fill="#b8b8b8"/><path d="M43.05 13 A22 22 0 0 1 24 46 L33.5 29.5 Z" fill="#6b6b6b"/><circle cx="24" cy="24" r="10" fill="#fff"/><circle cx="24" cy="24" r="7.2" fill="#5a5a5a"/>`,
};
document.querySelectorAll('.icon[data-app]').forEach(el => {
  el.innerHTML = `<svg viewBox="0 0 48 48" width="100%" height="100%">${ICONS[el.dataset.app]}</svg>`;
});
