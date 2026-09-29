document.getElementById('form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = document.getElementById('save');
  button.disabled = true;
  try { await window.setup.save(document.getElementById('server').value); }
  catch { document.getElementById('error').textContent = '地址无效或无法保存，请使用 HTTPS 地址；开发或局域网环境可使用如 http://192.168.x.x:8888 或 http://127.0.0.1:8888。'; button.disabled = false; }
});
