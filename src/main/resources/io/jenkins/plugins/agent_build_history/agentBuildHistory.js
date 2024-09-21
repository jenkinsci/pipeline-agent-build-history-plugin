function generateSVGIcon(iconName) {
  const icons = document.querySelector("#jenkins-build-status-icons");
  return icons.content.querySelector(`#${iconName}`).cloneNode(true);
}

function createCellList(td) {
  let list = document.createElement("div");
  list.classList.add("abh-cell-list");
  td.appendChild(list);
  return list;
}

function addCellRow(cellList, content, hidden) {
  const cell = document.createElement("div");
  cellList.appendChild(cell);
  cell.textContent = content;
  if (hidden) {
    cell.classList.add("abh-hidden", "jenkins-hidden");
  }
  return cell;
}

Behaviour.specify(".abh-rerun__button", "abh-rerun__button", 0, function(button) {
  button.onclick = function(event) {
    event.preventDefault();
    const runid = button.dataset.runid;
    let body = new URLSearchParams({ number: runid });
    fetch("replay", {
      method: "POST",
      headers: crumb.wrap({}),
      body,
    });
  }
});

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

window.abhDisplayExtendedBuildHistory = function(data) {
  const rootUrl = document.head.getAttribute("data-rooturl");
  const table = document.getElementById("projectStatus");
  table.style.display = "";

  for (let x = 0; data.length > x; x++) {
    const run = data[x];
    const tr = document.createElement("tr");

    // Set a data attribute for sorting by startTimeInMillis
    tr.setAttribute("data-start-time", run.startTimeInMillis);

    let td1 = document.createElement("td");
    td1.setAttribute("data", run.iconColorOrdinal);
    td1.classList.add("jenkins-table__cell--tight", "jenkins-table__icon", "abh-status");
    let div1 = document.createElement("div");
    let svg = generateSVGIcon(run.iconName);
    div1.appendChild(svg);
    td1.appendChild(div1);
    tr.appendChild(td1);

    let td2 = document.createElement("td");
    td2.classList.add("no-wrap");
    td2.setAttribute("data", run.parentFullDisplayName + "/" + run.number);
    let a1 = document.createElement("a");
    a1.classList.add("jenkins-table__link", "model-link");
    a1.href = rootUrl + "/" + run.parentUrl;
    let span1 = document.createElement("span");
    // TODO port Functions#breakableString to JavaScript and use .textContent rather than .innerHTML
    span1.innerHTML = run.parentFullDisplayName;
    a1.appendChild(span1);
    td2.appendChild(a1);
    let a2 = document.createElement("a");
    a2.classList.add(
      "jenkins-table__link",
      "jenkins-table__badge",
      "model-link",
      "inside",
    );
    a2.href = rootUrl + "/" + run.url;
    a2.textContent = run.displayName;
    td2.appendChild(a2);
    tr.appendChild(td2);

    let tdMessage = document.createElement("td");
    tdMessage.innerText = run.shortDescription;

    let tdStarted = document.createElement("td");
    tdStarted.innerText = run.startTimeReadable;

    let tdTimeSince = document.createElement("td");
    let tdDuration = document.createElement("td");
    let tdStatus = document.createElement("td");
    let tdButton = document.createElement("td");

    let tdTimeSinceList = createCellList(tdTimeSince);
    let tdDurationList = createCellList(tdDuration);
    let tdStatusList = createCellList(tdStatus);
    let tdNodeList = createCellList(tdButton);

    tdTimeSince.setAttribute("data", run.timestampString2);
    addCellRow(tdTimeSinceList, run.timestampString, false);

    tdDuration.setAttribute("data", run.duration);
    addCellRow(tdDurationList, escapeHTML(run.durationString), false);

    if (run.buildStatusSummaryWorse) {
      tdStatus.style.color = "var(--red)";
    }
    addCellRow(tdStatusList, run.buildStatusSummaryMessage, false);

    if (run.runId !== "") {
      tr.dataset.id = run.runId;
      let d = document.createElement("div");
      d.classList.add("abh-list__button");
      let button = document.createElement("button");
      button.setAttribute("tooltip", table.dataset.showText);
      button.classList.add("jenkins-button", "toggle-flow-nodes");
      button.appendChild(generateSVGIcon("chevron-down"));
      button.dataset.hidden = "true";
      d.appendChild(button);
      tdNodeList.appendChild(d);
      for (let x = 0; run.flowNodes.length > x; x++) {
        const e = run.flowNodes[x];
        addCellRow(tdTimeSinceList, e.startTimeString, true);
        addCellRow(tdDurationList, e.durationString, true);
        addCellRow(tdNodeList,"Node: " + e.flowNodeId, true);
        const status = addCellRow(tdStatusList, e.flowNodeStatusMessage, true);
        if (e.flowNodeStatusWorse) {
          status.style.color = "var(--red)";
        }
      }
    }

    let tdConsole = document.createElement("td");
    tdConsole.classList.add("jenkins-table__cell--tight");
    let div2 = document.createElement("div");
    div2.classList.add("jenkins-table__cell__button-wrapper");
    let a3 = document.createElement("a");
    a3.classList.add("jenkins-table__button");
    a3.href = run.consoleUrl;
    a3.appendChild(generateSVGIcon("console"));
    div2.appendChild(a3);
    tdConsole.appendChild(div2);

    tr.appendChild(tdMessage);
    tr.appendChild(tdStarted);
    tr.appendChild(tdTimeSince);
    tr.appendChild(tdDuration);
    tr.appendChild(tdStatus);
    tr.appendChild(tdButton);
    tr.appendChild(tdConsole);
    table.tBodies[0].appendChild(tr);

    Behaviour.applySubtree(tr);
  }
  ts_refresh(table);
};

function setCookie(name, value) {
  const expires = new Date();
  expires.setFullYear(expires.getFullYear() + 1); // Cookie expires in 1 year
  document.cookie = `${name}=${encodeURIComponent(value)}; expires=${expires.toUTCString()}; path=/`;
}

document.addEventListener("DOMContentLoaded", function () {
  const pageSizeInput = document.getElementById("pageSizeInput");
  const pageInput = document.getElementById("pageInput");

  // Handle page size changes
  if (pageSizeInput) {
    pageSizeInput.addEventListener("change", function () {
      const pageSize = pageSizeInput.value;
      setCookie("pageSize", pageSize);
      const page = 1; // Reset to the first page when page size changes
      const sortColumn = pageSizeInput.getAttribute('data-sort-column');
      const sortOrder = pageSizeInput.getAttribute('data-sort-order');

      const newUrl = `${window.location.pathname}?page=${page}&pageSize=${pageSize}&sortColumn=${sortColumn}&sortOrder=${sortOrder}`;
      window.location.href = newUrl;
    });
  }

  // Handle page input changes
  if (pageInput) {
    pageInput.addEventListener("change", function () {
      const page = pageInput.value;
      const pageSize = pageInput.getAttribute('data-page-size');
      const sortColumn = pageInput.getAttribute('data-sort-column');
      const sortOrder = pageInput.getAttribute('data-sort-order');

      const newUrl = `${window.location.pathname}?page=${page}&pageSize=${pageSize}&sortColumn=${sortColumn}&sortOrder=${sortOrder}`;
      window.location.href = newUrl;
    });
  }
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

