function generateSVGIcon(iconName) {
  const icons = document.querySelector("#jenkins-build-status-icons");
  return icons.content.querySelector(`#${iconName}`).cloneNode(true);
}

Behaviour.specify("button.toggle-flow-nodes", "abh-toggle-flow-nodes", 0 , function(button) {
  button.onclick = function() {
    let tr = button.closest("tr");
    let runId = tr.dataset.id;
    let table = tr.closest("table");
    let rows = table.querySelectorAll("tr[data-run-id='" + runId + "'");
    rows.forEach((row) => {
      row.classList.toggle("jenkins-hidden");
    });
    if (button.dataset.hidden === "true") {
      button.dataset.hidden = "false";
      button.textContent = table.dataset.hideNodesText;
    } else {
      button.dataset.hidden = "true";
      button.textContent = table.dataset.showNodesText;
    }

  }
});


window.abhDisplayExtendedBuildHistory = function(data) {
  const rootUrl = document.head.getAttribute("data-rooturl");
  const table = document.getElementById("projectStatus");
  table.style.display = "";

  function addFlowNodes(data, table) {
    for (let x = 0; data.flowNodes.length > x; x++) {
      const e = data.flowNodes[x];
      const tr = document.createElement("tr");

      tr.dataset.runId = data.runId;
      tr.classList.add("jenkins-hidden");

      let td1 = document.createElement("td");
      td1.setAttribute("data", data.iconColorOrdinal);
      td1.classList.add("jenkins-table__cell--tight", "jenkins-table__icon");
      let div1 = document.createElement("div");
      div1.classList.add("jenkins-table__cell__button-wrapper");
      let svg = generateSVGIcon(data.iconName);
      div1.appendChild(svg);
      td1.appendChild(div1);
      tr.appendChild(td1);


      let td2 = document.createElement("td");
      let a1 = document.createElement("a");
      a1.classList.add("jenkins-table__link", "model-link");
      a1.href = rootUrl + "/" + data.parentUrl;
      let span1 = document.createElement("span");
      // TODO port Functions#breakableString to JavaScript and use .textContent rather than .innerHTML
      span1.innerHTML = data.parentFullDisplayName;
      a1.appendChild(span1);
      td2.appendChild(a1);
      let a2 = document.createElement("a");
      a2.classList.add(
        "jenkins-table__link",
        "jenkins-table__badge",
        "model-link",
        "inside",
      );
      a2.href = rootUrl + "/" + data.url;
      a2.textContent = data.displayName;
      td2.appendChild(a2);
      tr.appendChild(td2);

      let span2 = document.createElement("span");
      span2.classList.add("jenkins-!-margin-left-1");
      span2.textContent = "Node: " + e.flowNodeId;
      td2.appendChild(span2);
      tr.appendChild(td2);

      let td3 = document.createElement("td");
      td3.setAttribute("data", e.startTime);
      td3.textContent = e.startTimeString;
      tr.appendChild(td3);

      let td4 = document.createElement("td");
      td4.setAttribute("data", e.duration);
      td4.textContent = e.durationString;
      tr.appendChild(td4);

      let td5 = document.createElement("td");
      td5.textContent = e.flowNodeStatusMessage;
      if (e.flowNodeStatusWorse) {
        td5.style.color = "var(--red)";
      }
      tr.appendChild(td5);


      let td6 = document.createElement("td");
      td6.classList.add("jenkins-table__cell--tight");
      let div2 = document.createElement("div");
      div2.classList.add("jenkins-table__cell__button-wrapper");
      let a3 = document.createElement("a");
      a3.classList.add("jenkins-table__button");
      a3.href = data.consoleUrl;
      a3.appendChild(generateSVGIcon("console"));
      div2.appendChild(a3);
      td6.appendChild(div2);
      tr.appendChild(td6);
      table.tBodies[0].appendChild(tr);
      Behaviour.applySubtree(tr);
    }
  }

  for (let x = 0; data.length > x; x++) {
    const e = data[x];
    const tr = document.createElement("tr");

    let td1 = document.createElement("td");
    td1.setAttribute("data", e.iconColorOrdinal);
    td1.classList.add("jenkins-table__cell--tight", "jenkins-table__icon");
    let div1 = document.createElement("div");
    div1.classList.add("jenkins-table__cell__button-wrapper");
    let svg = generateSVGIcon(e.iconName);
    div1.appendChild(svg);
    td1.appendChild(div1);
    tr.appendChild(td1);

    let td2 = document.createElement("td");
    td2.classList.add("no-wrap");
    let a1 = document.createElement("a");
    a1.classList.add("jenkins-table__link", "model-link");
    a1.href = rootUrl + "/" + e.parentUrl;
    let span1 = document.createElement("span");
    // TODO port Functions#breakableString to JavaScript and use .textContent rather than .innerHTML
    span1.innerHTML = e.parentFullDisplayName;
    a1.appendChild(span1);
    td2.appendChild(a1);
    let a2 = document.createElement("a");
    a2.classList.add(
      "jenkins-table__link",
      "jenkins-table__badge",
      "model-link",
      "inside",
    );
    a2.href = rootUrl + "/" + e.url;
    a2.textContent = e.displayName;
    td2.appendChild(a2);
    if (e.runId !== "") {
      tr.dataset.id = e.runId;
      let button = document.createElement("button");
      button.classList.add("jenkins-table__button", "jenkins-table__badge", "toggle-flow-nodes");
      button.textContent = table.dataset.showNodesText;
      button.dataset.hidden = "true";
      td2.appendChild(button);
    }
    tr.appendChild(td2);

    let td3 = document.createElement("td");
    td3.setAttribute("data", e.timestampString2);
    td3.textContent = e.timestampString;
    tr.appendChild(td3);

    let td4 = document.createElement("td");
    td4.setAttribute("data", e.duration);
    td4.innerText = escapeHTML(e.durationString);
    tr.appendChild(td4);

    let td5 = document.createElement("td");
    if (e.buildStatusSummaryWorse) {
      td5.style.color = "var(--red)";
    }
    td5.textContent = e.buildStatusSummaryMessage;
    tr.appendChild(td5);

    let td6 = document.createElement("td");
    td6.classList.add("jenkins-table__cell--tight");
    let div2 = document.createElement("div");
    div2.classList.add("jenkins-table__cell__button-wrapper");
    let a3 = document.createElement("a");
    a3.classList.add("jenkins-table__button");
    a3.href = e.consoleUrl;
    a3.appendChild(generateSVGIcon("console"));
    div2.appendChild(a3);
    td6.appendChild(div2);
    tr.appendChild(td6);
    table.tBodies[0].appendChild(tr);

    addFlowNodes(e, table);

    Behaviour.applySubtree(tr);
  }
  ts_refresh(table);
};
