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

Behaviour.specify(".abh-agent-list__button button", "abh-agent-list__button", 0, function(button) {
  button.onclick = function() {
    let tr = button.closest("tr");
    let table = button.closest("table");
    let rows = tr.querySelectorAll(".abh-agent-hidden");
    for (row of rows) {
      row.classList.toggle("jenkins-hidden");
    }
    if (button.dataset.hidden === "true") {
      button.dataset.hidden = "false";
      button.setAttribute("tooltip", table.dataset.hideAgentsText);
    } else {
      button.dataset.hidden = "true";
      button.setAttribute("tooltip", table.dataset.showAgentsText);
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
    link2.innerText = escapeHTML(e.displayName);
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
      let tdAgentList = document.createElement("div");
      let tdLabelList = document.createElement("div");
      let tdDurationList = document.createElement("div");
      tdAgentList.classList.add("abh-agent-list");
      tdLabelList.classList.add("abh-label-list");
      tdDurationList.classList.add("abh-duration-list");
      td5.appendChild(tdAgentList);
      td6.appendChild(tdLabelList);
      td7.appendChild(tdDurationList);
      for (const agent of e.agents) {
        i++;
        let a = document.createElement("div");
        let l = document.createElement("div");
        let duration = document.createElement("div");
        tdAgentList.appendChild(a);
        tdLabelList.appendChild(l);
        tdDurationList.appendChild(duration);
        if (i > 1) {
          a.classList.add("abh-agent-hidden", "jenkins-hidden");
          l.classList.add("abh-agent-hidden", "jenkins-hidden");
          duration.classList.add("abh-agent-hidden", "jenkins-hidden");
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
        d.classList.add("abh-agent-list__button");
        let b = document.createElement("button");
        b.setAttribute("tooltip", table.dataset.showAgentsText);
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

      let td3a = document.createElement("td");
      tr.appendChild(td3a);

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
    tr.appendChild(td2);
    let td3a = document.createElement("td");
    if (e.runId !== "") {
      tr.dataset.id = e.runId;
      let button = document.createElement("button");
      button.classList.add("jenkins-table__button", "jenkins-table__badge", "toggle-flow-nodes");
      button.textContent = table.dataset.showNodesText;
      button.dataset.hidden = "true";
      td3a.appendChild(button);
    }
    tr.appendChild(td3a);

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
