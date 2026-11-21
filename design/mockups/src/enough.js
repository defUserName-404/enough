// Fills repeated bits so each mockup stays short.
document.querySelectorAll('.status').forEach(s => {
  s.innerHTML = '<span>14:32</span><span class="sig"><i style="height:4px"></i><i style="height:7px"></i><i style="height:10px"></i><i style="height:13px"></i>&nbsp;&nbsp;87%</span>';
});
document.querySelectorAll('.bar[data-n]').forEach(b => {
  const n = +b.dataset.n;
  b.innerHTML = Array.from({ length: 20 }, (_, i) => `<i class="${i < n ? 'on' : ''}"></i>`).join('');
});
