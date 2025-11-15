Behaviour.specify(".abh-list__button button", "abh-list__button", 0, function(button) {
  button.onclick = function() {
    let tr = button.closest("tr");
    let table = button.closest("table");
    let rows = tr.querySelectorAll(".abh-hidden");
    for (row of rows) {
      row.classList.toggle("jenkins-hidden");
    }
    if (button.dataset.hidden === "true") {
      button.dataset.hidden = "false";
      button.setAttribute("tooltip", table.dataset.hideText);
    } else {
      button.dataset.hidden = "true";
      button.setAttribute("tooltip", table.dataset.showText);
    }
    Behaviour.applySubtree(tr);
  }
});

function setCookie(name, value) {
  const expires = new Date();
  expires.setFullYear(expires.getFullYear() + 1); // Cookie expires in 1 year
  document.cookie = `${name}=${encodeURIComponent(value)}; expires=${expires.toUTCString()}; path=/`;
}

document.addEventListener("DOMContentLoaded", function () {
  const pageSizeInput = document.getElementById("pageSizeInput");
  const pageInput = document.getElementById("pageInput");
  const statusFilter = document.getElementById("abh-status-filter");
  const container = document.getElementById("abh-pagination-filter-container");

  // Handle page size changes
  pageSizeInput.addEventListener("change", function () {
    const pageSize = pageSizeInput.value;
    setCookie("pageSize", pageSize);
    const page = 1; // Reset to the first page when page size changes
    const sortColumn = container.getAttribute('data-sort-column');
    const sortOrder = container.getAttribute('data-sort-order');

    const newUrl = `${window.location.pathname}?page=${page}&pageSize=${pageSize}&sortColumn=${sortColumn}&sortOrder=${sortOrder}&status=${statusFilter.value}`;
    window.location.href = newUrl;
  });

  // Handle page input changes
  pageInput.addEventListener("change", function () {
    const page = pageInput.value;
    const pageSize = container.getAttribute('data-page-size');
    const sortColumn = container.getAttribute('data-sort-column');
    const sortOrder = container.getAttribute('data-sort-order');

    const newUrl = `${window.location.pathname}?page=${page}&pageSize=${pageSize}&sortColumn=${sortColumn}&sortOrder=${sortOrder}&status=${statusFilter.value}`;
    window.location.href = newUrl;
  });

  statusFilter.addEventListener("change", function () {
    const page = 1;
    const pageSize = container.getAttribute('data-page-size');
    const sortColumn = container.getAttribute('data-sort-column');
    const sortOrder = container.getAttribute('data-sort-order');
    const status = statusFilter.value;
    const newUrl = `${window.location.pathname}?page=${page}&pageSize=${pageSize}&sortColumn=${sortColumn}&sortOrder=${sortOrder}&status=${status}`;
    window.location.href = newUrl;
  });

  const sortLinks = document.querySelectorAll('.sortheader');
  sortLinks.forEach(function (link) {
    link.addEventListener('click', function (event) {
      event.preventDefault();  // Prevent default link behavior

      const urlParams = new URLSearchParams(link.search);
      const sortColumn = urlParams.get('sortColumn');
      const sortOrder = urlParams.get('sortOrder');

      // Set cookies for sortColumn and sortOrder
      setCookie("sortColumn", sortColumn);
      setCookie("sortOrder", sortOrder);

      // Redirect to the new URL with sorting parameters
      const pageSize = pageSizeInput ? pageSizeInput.value : '20';
      const page = pageInput ? pageInput.value : '1';

      const newUrl = `${window.location.pathname}?page=${page}&pageSize=${pageSize}&sortColumn=${sortColumn}&sortOrder=${sortOrder}`;
      window.location.href = newUrl;
    });
  });
});

