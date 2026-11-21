// Fills repeated bits so each mockup stays short.
document.querySelectorAll('.status').forEach(s => {
  s.innerHTML = '<span>14:32</span><span class="sig"><i style="height:4px"></i><i style="height:7px"></i><i style="height:10px"></i><i style="height:13px"></i>&nbsp;&nbsp;87%</span>';
});
document.querySelectorAll('.bar[data-n]').forEach(b => {
  const n = +b.dataset.n;
  b.innerHTML = Array.from({ length: 20 }, (_, i) => `<i class="${i < n ? 'on' : ''}"></i>`).join('');
});
// Header icons (gear / back arrow), filled from data-ico.
const ICO = {
  gear: '<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="6.2" stroke-width="2.4"/><circle cx="12" cy="12" r="9.2" stroke-width="3.6" stroke-dasharray="3.6 3.6"/><circle cx="12" cy="12" r="2.2" stroke-width="2"/></svg>',
  lock: '<svg viewBox="0 0 24 24" width="24" height="24"><rect x="4" y="11" width="16" height="11" fill="currentColor"/><path d="M7.5 11V8a4.5 4.5 0 0 1 9 0v3" fill="none" stroke="currentColor" stroke-width="2.6"/></svg>',
  back: '<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="square"><path d="M20 12H5M11 6l-6 6 6 6"/></svg>'
};
document.querySelectorAll('[data-ico]').forEach(el => { el.innerHTML = ICO[el.dataset.ico]; });
