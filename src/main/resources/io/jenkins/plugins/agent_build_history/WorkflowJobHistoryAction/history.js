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

document.addEventListener("DOMContentLoaded", function () {
  const statusFilter = document.getElementById("abh-status-filter");
  const agentFilter = document.getElementById("abh-agent-filter");
  const startFilter = document.getElementById("abh-start-filter");

  statusFilter.addEventListener("change", function () {
    const newUrl = `${window.location.pathname}?status=${statusFilter.value}&agent=${agentFilter.value}&startBuild=${startFilter.value}`;
    window.location.href = newUrl;
  });

  agentFilter.addEventListener("change", function () {
    const newUrl = `${window.location.pathname}?status=${statusFilter.value}&agent=${agentFilter.value}&startBuild=${startFilter.value}`;
    window.location.href = newUrl;
  });

  startFilter.addEventListener("change", function () {
    const newUrl = `${window.location.pathname}?status=${statusFilter.value}&agent=${agentFilter.value}&startBuild=${startFilter.value}`;
    window.location.href = newUrl;
  });

});
