package org.konaframework.firefly.widgets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.konaframework.firefly.schemas.Schema;
import org.konaframework.firefly.schemas.Text;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class WidgetJavaTest {
    @AfterEach
    void clear() {
        ChartWidget.configureUsing(null);
    }

    @Test
    void javaPlacesChartsOnAPage() {
        ChartWidget.configureUsing(chart -> chart.maxHeight("80px"));
        JavaRevenueChart revenue = new JavaRevenueChart();
        revenue.columnSpan(get -> "2");
        revenue.extraAttributes(Map.of("data-lang", "java"));
        ChartWidget apex = ChartWidget.make("apex");
        apex.engine(ChartEngine.APEX_CHARTS);
        apex.type(get -> "line");
        apex.categories(List.of("Mon"));
        apex.series(List.of(new ChartSeries("Orders", List.of(1, 2))));
        apex.options(Map.of("stroke", Map.of("curve", "smooth")));
        Page page = new Page();
        page.title("Java");
        page.breadcrumbs(Breadcrumb.make("Home"), Breadcrumb.make("Java", "/java"));
        page.schema(new Schema().components(Text.make("Hello")));
        page.widgets(revenue, apex, StatsOverviewWidget.make("stats").stats(
            new Stat().label("Open").value("1").description(get -> "today")
        ));
        String html = page.render(Map.of());
        assertTrue(html.contains("data-ff-engine=\"chartjs\""));
        assertTrue(html.contains("data-ff-engine=\"apexcharts\""));
        assertTrue(html.contains("Hello"));
        assertTrue(html.contains("Open"));
        assertTrue(html.contains("data-ff-max-height=\"80px\""));
        assertTrue(html.contains("ff-breadcrumbs"));
        assertTrue(AccountWidget.make("me").accountName(get -> "Ada").email("a@b.c").render().contains("Ada"));
        assertTrue(Widget.make("box").render().contains("ff-widget"));
        assertTrue(ListWidget.make("recent").heading("Recent").render().contains("ff-list-widget"));
        assertTrue(CardWidget.make("tip").html("<p>hi</p>").render().contains("ff-card-widget"));
        assertTrue(NavigationCardsWidget.make("nav").render().contains("ff-nav-cards-widget"));
        org.junit.jupiter.api.Assertions.assertThrows(org.konaframework.firefly.support.FireflyException.class, () -> TableWidget.make("empty").render());
    }

    public static class JavaRevenueChart extends ChartWidget {
        public JavaRevenueChart() {
            super("java-revenue");
            engine(ChartEngine.CHART_JS);
            type("line");
            categories(get -> List.of("Mon", "Tue"));
            series(get -> List.of(new ChartSeries("Revenue", List.of(3, 4))));
        }
    }
}
