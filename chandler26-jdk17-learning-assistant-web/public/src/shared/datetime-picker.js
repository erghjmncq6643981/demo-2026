export function initDatetimePicker(container, onChange) {
  if (!container) return;

  const placeholder = container.dataset.placeholder || '选择日期时间';
  const hiddenInput = container.querySelector('input[type="hidden"]');
  const trigger = container.querySelector('.datetime-picker-trigger');
  const label = container.querySelector('.datetime-picker-label');
  const popover = container.querySelector('.datetime-picker-popover');
  const title = container.querySelector('.datetime-picker-title');
  const prevBtn = container.querySelector('.datetime-picker-nav.prev');
  const nextBtn = container.querySelector('.datetime-picker-nav.next');
  const daysGrid = container.querySelector('.datetime-picker-days');
  const hourSelect = container.querySelector('.datetime-picker-hour');
  const minuteSelect = container.querySelector('.datetime-picker-minute');
  const clearBtn = container.querySelector('.datetime-picker-clear');
  const nowBtn = container.querySelector('.datetime-picker-now');

  let currentValue = hiddenInput.value || ''; // Format: YYYY-MM-DDTHH:mm
  let tempDate = currentValue ? new Date(currentValue) : new Date();
  if (isNaN(tempDate.getTime())) tempDate = new Date();

  let viewYear = tempDate.getFullYear();
  let viewMonth = tempDate.getMonth(); // 0-indexed

  // Populate hour select (00-23)
  hourSelect.innerHTML = Array.from({ length: 24 }, (_, i) => {
    const val = String(i).padStart(2, '0');
    return `<option value="${val}">${val}</option>`;
  }).join('');

  // Populate minute select (00-59)
  minuteSelect.innerHTML = Array.from({ length: 60 }, (_, i) => {
    const val = String(i).padStart(2, '0');
    return `<option value="${val}">${val}</option>`;
  }).join('');

  // Initialize time select values
  if (currentValue) {
    const timeParts = currentValue.split('T')[1]?.split(':') || ['00', '00'];
    hourSelect.value = timeParts[0];
    minuteSelect.value = timeParts[1];
  } else {
    hourSelect.value = String(new Date().getHours()).padStart(2, '0');
    minuteSelect.value = String(new Date().getMinutes()).padStart(2, '0');
  }

  // Helper to format date label
  function formatLabel(val) {
    if (!val) return placeholder;
    const d = new Date(val);
    if (isNaN(d.getTime())) return placeholder;
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  function updateValue(val) {
    currentValue = val;
    hiddenInput.value = val;
    label.textContent = formatLabel(val);
    label.classList.toggle('placeholder', !val);

    if (val) {
      const parts = val.split('T');
      const dateParts = parts[0].split('-');
      viewYear = parseInt(dateParts[0], 10);
      viewMonth = parseInt(dateParts[1], 10) - 1;
      const timeParts = parts[1].split(':');
      hourSelect.value = timeParts[0];
      minuteSelect.value = timeParts[1];
    }

    if (onChange) onChange(val);
    hiddenInput.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function renderCalendar() {
    title.textContent = `${viewYear}年${viewMonth + 1}月`;

    const firstDay = new Date(viewYear, viewMonth, 1);
    const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
    // JS getDay() returns 0 for Sunday, 1 for Monday etc. We want Monday as index 0.
    const leadingBlanks = (firstDay.getDay() + 6) % 7;

    const cells = [];
    for (let i = 0; i < leadingBlanks; i++) {
      cells.push('<span class="datetime-picker-day blank"></span>');
    }

    const todayStr = new Date().toISOString().split('T')[0];
    const selectedDateStr = currentValue ? currentValue.split('T')[0] : '';

    for (let d = 1; d <= daysInMonth; d++) {
      const currentMonthStr = String(viewMonth + 1).padStart(2, '0');
      const currentDayStr = String(d).padStart(2, '0');
      const dateStr = `${viewYear}-${currentMonthStr}-${currentDayStr}`;

      const classes = ['datetime-picker-day'];
      if (dateStr === todayStr) classes.push('today');
      if (dateStr === selectedDateStr) classes.push('selected');

      cells.push(`<button class="${classes.join(' ')}" type="button" data-date="${dateStr}">${d}</button>`);
    }

    daysGrid.innerHTML = cells.join('');

    // Bind day buttons click
    daysGrid.querySelectorAll('button').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        const date = btn.dataset.date;
        const hour = hourSelect.value;
        const minute = minuteSelect.value;
        updateValue(`${date}T${hour}:${minute}`);
        popover.classList.add('hidden');
      });
    });
  }

  // Bind trigger click
  trigger.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    const isHidden = popover.classList.contains('hidden');
    // Close other pickers
    document.querySelectorAll('.datetime-picker-popover').forEach(p => p.classList.add('hidden'));
    if (isHidden) {
      popover.classList.remove('hidden');
      // Reset view to current value or today
      const refDate = currentValue ? new Date(currentValue) : new Date();
      viewYear = refDate.getFullYear();
      viewMonth = refDate.getMonth();
      renderCalendar();
    } else {
      popover.classList.add('hidden');
    }
  });

  // Prev/Next month
  prevBtn.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (viewMonth === 0) {
      viewMonth = 11;
      viewYear--;
    } else {
      viewMonth--;
    }
    renderCalendar();
  });

  nextBtn.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (viewMonth === 11) {
      viewMonth = 0;
      viewYear++;
    } else {
      viewMonth++;
    }
    renderCalendar();
  });

  // Time select change
  const handleTimeChange = () => {
    if (currentValue) {
      const datePart = currentValue.split('T')[0];
      const hour = hourSelect.value;
      const minute = minuteSelect.value;
      updateValue(`${datePart}T${hour}:${minute}`);
    }
  };
  hourSelect.addEventListener('change', handleTimeChange);
  minuteSelect.addEventListener('change', handleTimeChange);

  // Clear button
  clearBtn.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    updateValue('');
    popover.classList.add('hidden');
  });

  // Now button
  nowBtn.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    const now = new Date();
    const pad = (n) => String(n).padStart(2, '0');
    const localISO = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}`;
    updateValue(localISO);
    popover.classList.add('hidden');
  });

  // Close when clicking outside
  document.addEventListener('click', (e) => {
    if (!container.contains(e.target)) {
      popover.classList.add('hidden');
    }
  });

  // Initial label render
  label.textContent = formatLabel(currentValue);
  label.classList.toggle('placeholder', !currentValue);
}
