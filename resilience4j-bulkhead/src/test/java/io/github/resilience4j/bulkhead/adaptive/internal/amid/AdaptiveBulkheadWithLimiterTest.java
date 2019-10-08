package io.github.resilience4j.bulkhead.adaptive.internal.amid;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;

import io.github.resilience4j.bulkhead.adaptive.AdaptiveBulkhead;
import io.github.resilience4j.bulkhead.adaptive.AdaptiveBulkheadConfig;
import io.github.resilience4j.bulkhead.adaptive.internal.config.AimdConfig;

/**
 * test the adoptive bulkhead limiter logic
 */
public class AdaptiveBulkheadWithLimiterTest {
	private AdaptiveBulkhead bulkhead;
	private AdaptiveBulkheadConfig<AimdConfig> config;
	// enable if u need to see the graphs of the executions
	private boolean drawGraphs = false;

	@Before
	public void setup() {
		config = AdaptiveBulkheadConfig.<AimdConfig>builder().config(AimdConfig.builder().maxConcurrentRequestsLimit(50)
				.minConcurrentRequestsLimit(5)
				.slidingWindowSize(5)
				.slidingWindowTime(2)
				.failureRateThreshold(50)
				.slowCallRateThreshold(50)
				.slowCallDurationThreshold(200)
				.build()).build();

		bulkhead = AdaptiveBulkhead.of("test", config);

	}

	@Test
	public void testLimiter() throws InterruptedException {
		List<Double> time = new ArrayList<>();
		List<Double> maxConcurrentCalls = new ArrayList<>();
		AtomicInteger count = new AtomicInteger();
		if (drawGraphs) {
			bulkhead.getEventPublisher().onEvent(bulkhead -> {
				maxConcurrentCalls.add(Double.valueOf(bulkhead.eventData().get("newMaxConcurrentCalls")));
				time.add((double) count.incrementAndGet());
			});
		}
		ExecutorService executorService = Executors.newFixedThreadPool(6);
		// if u like to get the graphs , increase the number of iterations to have better distribution
		for (int i = 0; i < 3000; i++) {
			Runnable runnable = () -> {
				bulkhead.acquirePermission();
				final Duration duration = Duration.ofMillis(randomLatency(5, 400));
				try {
					Thread.sleep(duration.toMillis());
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				bulkhead.onSuccess(duration.toMillis(), TimeUnit.MILLISECONDS);
			};
			executorService.execute(runnable);
		}


		Thread.sleep(20000);
		executorService.shutdown();

		if (drawGraphs) {
			// Create Chart
			XYChart chart2 = new XYChartBuilder().width(800).height(600).title(getClass().getSimpleName()).xAxisTitle("time").yAxisTitle("Concurrency limit").build();
			chart2.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
			chart2.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
			chart2.getStyler().setYAxisLabelAlignment(Styler.TextAlignment.Right);
			chart2.getStyler().setYAxisDecimalPattern("ConcurrentCalls #");
			chart2.getStyler().setPlotMargin(0);
			chart2.getStyler().setPlotContentSize(.95);


			chart2.addSeries("MaxConcurrentCalls", time, maxConcurrentCalls);
			try {
				BitmapEncoder.saveJPGWithQuality(chart2, "./AdaptiveBulkheadConcurrency.jpg", 0.95f);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	public long randomLatency(int min, int max) {
		return min + ThreadLocalRandom.current().nextLong(max - min);
	}
}