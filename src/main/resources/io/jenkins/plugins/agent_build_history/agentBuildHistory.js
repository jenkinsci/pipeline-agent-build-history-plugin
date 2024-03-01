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

window.abhBuildTimeTrend = function (data) {
  var table = document.getElementById("trend");
  var rootURL = document.head.getAttribute("data-rooturl");

  for (var x = 0; data.length > x; x++) {
    var e = data[x];
    var tr = document.createElement("tr");

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
    td2.setAttribute("data", e.number);

    let link2 = document.createElement("a");
    link2.href = rootURL + "/" + e.url;
    link2.classList.add("model-link", "inside");
    link2.innerText = e.displayName;
    td2.appendChild(link2);
    tr.appendChild(td2);

    let td3 = document.createElement("td");
    td3.setAttribute("data", e.timestampString2);
    td3.textContent = e.timestampString;
    tr.appendChild(td3);

    let td4 = document.createElement("td");
    td4.setAttribute("data", e.duration);
    td4.textContent = e.durationString;
    tr.appendChild(td4);

    let td5 = document.createElement("td");
    let td6 = document.createElement("td");
    let td7 = document.createElement("td");
    let tdButton = document.createElement("td");
    const agentCount = e.agents.length;
    if (agentCount > 0) {
      let i = 0;

      let tdAgentList = createCellList(td5);
      let tdLabelList = createCellList(td6);
      let tdDurationList = createCellList(td7);
      for (const agent of e.agents) {
        i++;
        let a = document.createElement("div");
        let l = document.createElement("div");
        let duration = document.createElement("div");
        tdAgentList.appendChild(a);
        tdLabelList.appendChild(l);
        tdDurationList.appendChild(duration);
        if (i > 1) {
          a.classList.add("abh-hidden", "jenkins-hidden");
          l.classList.add("abh-hidden", "jenkins-hidden");
          duration.classList.add("abh-hidden", "jenkins-hidden");
        }
        let buildInfo = null;
        let buildInfoStr = escapeHTML(agent.builtOnStr || "");
        if (agent.builtOn) {
          buildInfo = document.createElement("a");
          buildInfo.classList.add("model-link", "abh-agent-link")
          buildInfo.href = rootURL + "/computer/" + agent.builtOn;
          buildInfo.innerText = buildInfoStr;
          a.appendChild(buildInfo);
        } else {
          a.innerText = buildInfoStr;
        }
        if (agent.label) {
          let label = document.createElement("a");
          label.href = rootURL + "/label/" + agent.label;
          label.innerText = agent.label;
          l.appendChild(label);
        } else {
          l.innerText = table.dataset.noLabel;
        }
        if (agent.duration) {
          duration.innerText = agent.duration;
        }
      }
      if (agentCount > 1) {
        let d = document.createElement("div");
        d.classList.add("abh-list__button");
        let b = document.createElement("button");
        b.setAttribute("tooltip", table.dataset.showText);
        b.appendChild(generateSVGIcon("chevron-down"));
        b.classList.add("jenkins-button");
        b.dataset.hidden = "true";
        d.appendChild(b);
        tdButton.appendChild(d);
      }
    }
    tr.appendChild(td5);
    tr.appendChild(td6);
    tr.appendChild(td7);
    tr.appendChild(tdButton);

    let tdLast = document.createElement("td");
    tdLast.classList.add("jenkins-table__cell--tight");
    let div2 = document.createElement("div");
    div2.classList.add("jenkins-table__cell__button-wrapper");
    let a3 = document.createElement("a");
    a3.classList.add("jenkins-table__button");
    a3.href = e.consoleUrl;
    a3.appendChild(generateSVGIcon("console"));
    div2.appendChild(a3);
    tdLast.appendChild(div2);
    tr.appendChild(tdLast);

    table.tBodies[0].appendChild(tr);
    Behaviour.applySubtree(tr);
  }
  ts_refresh(table);
};


window.abhDisplayExtendedBuildHistory = function(data) {
  const rootUrl = document.head.getAttribute("data-rooturl");
  const table = document.getElementById("projectStatus");
  table.style.display = "";

  for (let x = 0; data.length > x; x++) {
    const run = data[x];
    const tr = document.createElement("tr");

    let td1 = document.createElement("td");
    td1.setAttribute("data", run.iconColorOrdinal);
    td1.classList.add("jenkins-table__cell--tight", "jenkins-table__icon");
    let div1 = document.createElement("div");
    div1.classList.add("jenkins-table__cell__button-wrapper");
    let svg = generateSVGIcon(run.iconName);
    div1.appendChild(svg);
    td1.appendChild(div1);
    tr.appendChild(td1);

    let td2 = document.createElement("td");
    td2.classList.add("no-wrap");
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
