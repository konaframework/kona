(function () {
  function sparklineColor(token) {
    const root = document.querySelector(".ff-panel") || document.documentElement;
    const styles = getComputedStyle(root);
    if (token === "success") return "#16a34a";
    if (token === "warning") return "#d97706";
    if (token === "danger") return "#dc2626";
    if (token === "info") return "#2563eb";
    if (token === "gray") return styles.getPropertyValue("--ff-body-subtle").trim() || "#64748b";
    return styles.getPropertyValue("--ff-primary").trim() || "#DF8723";
  }

  function mountFireflySparkline(root) {
    if (!window.ApexCharts || root.dataset.ffSparklineMounted === "1") return;
    const raw = root.dataset.ffSparkline || "";
    const data = raw
      .split(",")
      .map(function (part) {
        return Number(part.trim());
      })
      .filter(function (n) {
        return !Number.isNaN(n);
      });
    if (!data.length) return;
    const target = root.querySelector(".ff-stat-sparkline") || root;
    const color = sparklineColor(root.dataset.ffSparklineColor || "primary");
    root.dataset.ffSparklineMounted = "1";
    new window.ApexCharts(target, {
      chart: {
        type: "area",
        height: 40,
        sparkline: { enabled: true },
        animations: { enabled: false },
      },
      stroke: { curve: "smooth", width: 2 },
      fill: {
        type: "gradient",
        gradient: { opacityFrom: 0.45, opacityTo: 0.05 },
      },
      colors: [color],
      series: [{ name: "Trend", data: data }],
      tooltip: { enabled: false },
    }).render();
  }

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
      const chart = Object.assign({ type: config.libraryType, height: 220 }, options.chart || {});
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
  window.mountFireflySparkline = mountFireflySparkline;

  function boot() {
    document.querySelectorAll(".ff-chart").forEach(mountFireflyChart);
    document.querySelectorAll("[data-ff-sparkline]").forEach(mountFireflySparkline);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
