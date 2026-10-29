(function () {
  function mountFireflyChart(root) {
    const node = root.querySelector(".ff-chart-config");
    if (!node) return;
    const config = JSON.parse(node.textContent || "{}");
    const options = Object.assign({}, config.options || {});
    if (config.engine === "chartjs" && window.Chart) {
      const canvas = root.querySelector("canvas");
      new window.Chart(canvas, {
        type: config.libraryType,
        data: {
          labels: config.categories,
          datasets: (config.series || []).map(function (series) {
            return { label: series.name, data: series.data };
          }),
        },
        options,
      });
    }
    if (config.engine === "apexcharts" && window.ApexCharts) {
      const target = root.querySelector(".ff-chart-target");
      const chart = Object.assign({ type: config.libraryType }, options.chart || {});
      new window.ApexCharts(
        target,
        Object.assign({}, options, {
          chart,
          labels: config.categories,
          series: (config.series || []).map(function (series) {
            return { name: series.name, data: series.data };
          }),
        }),
      ).render();
    }
  }

  window.mountFireflyChart = mountFireflyChart;

  function boot() {
    document.querySelectorAll(".ff-chart").forEach(mountFireflyChart);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
