package org.opentripplanner.raptor.rangeraptor.standard.configure;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.opentripplanner.raptor.api.model.RaptorTripSchedule;
import org.opentripplanner.raptor.rangeraptor.context.SearchContext;
import org.opentripplanner.raptor.rangeraptor.internalapi.HeuristicSearch;
import org.opentripplanner.raptor.rangeraptor.internalapi.Heuristics;
import org.opentripplanner.raptor.rangeraptor.internalapi.RaptorWorker;
import org.opentripplanner.raptor.rangeraptor.internalapi.RoutingStrategy;
import org.opentripplanner.raptor.rangeraptor.internalapi.WorkerState;
import org.opentripplanner.raptor.rangeraptor.path.DestinationArrivalPaths;
import org.opentripplanner.raptor.rangeraptor.path.configure.PathConfig;
import org.opentripplanner.raptor.rangeraptor.standard.ArrivalTimeRoutingStrategy;
import org.opentripplanner.raptor.rangeraptor.standard.MinTravelDurationRoutingStrategy;
import org.opentripplanner.raptor.rangeraptor.standard.StdRangeRaptorWorkerState;
import org.opentripplanner.raptor.rangeraptor.standard.StdWorkerState;
import org.opentripplanner.raptor.rangeraptor.standard.besttimes.BestTimes;
import org.opentripplanner.raptor.rangeraptor.standard.besttimes.BestTimesOnlyStopArrivalsState;
import org.opentripplanner.raptor.rangeraptor.standard.besttimes.SimpleArrivedAtDestinationCheck;
import org.opentripplanner.raptor.rangeraptor.standard.besttimes.SimpleBestNumberOfTransfers;
import org.opentripplanner.raptor.rangeraptor.standard.besttimes.UnknownPathFactory;
import org.opentripplanner.raptor.rangeraptor.standard.debug.DebugStopArrivalsState;
import org.opentripplanner.raptor.rangeraptor.standard.heuristics.HeuristicsAdapter;
import org.opentripplanner.raptor.rangeraptor.standard.internalapi.ArrivedAtDestinationCheck;
import org.opentripplanner.raptor.rangeraptor.standard.internalapi.BestNumberOfTransfers;
import org.opentripplanner.raptor.rangeraptor.standard.internalapi.StopArrivalsState;
import org.opentripplanner.raptor.rangeraptor.standard.stoparrivals.StdStopArrivals;
import org.opentripplanner.raptor.rangeraptor.standard.stoparrivals.StdStopArrivalsState;
import org.opentripplanner.raptor.rangeraptor.standard.stoparrivals.path.EgressArrivalToPathAdapter;
import org.opentripplanner.raptor.rangeraptor.standard.stoparrivals.view.StopsCursor;
import org.opentripplanner.raptor.spi.CostCalculator;

/**
 * The responsibility of this class is to wire different standard range raptor worker configurations
 * together based on the context passed into the class. There is a factory (create) method for each
 * legal configuration.
 *
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
public class StdRangeRaptorConfig<T extends RaptorTripSchedule> {

  private final SearchContext<T> ctx;
  private final PathConfig<T> pathConfig;

  private BestTimes bestTimes = null;
  private StdStopArrivals<T> arrivals = null;
  private ArrivedAtDestinationCheck destinationCheck = null;
  private BestNumberOfTransfers bestNumberOfTransfers = null;

  public StdRangeRaptorConfig(SearchContext<T> context) {
    this.ctx = context;
    this.pathConfig = new PathConfig<>(context);
  }

  /**
   * Create a heuristic search using the provided callback to create the worker. The callback is
   * necessary because the heuristics MUST be created before the worker, if not the heuristic can
   * not be added to the worker lifecycle and fails.
   */
  public HeuristicSearch<T> createHeuristicSearch(
    BiFunction<WorkerState<T>, RoutingStrategy<T>, RaptorWorker<T>> createWorker,
    CostCalculator<T> costCalculator
  ) {
    StdRangeRaptorWorkerState<T> state = createState();
    Heuristics heuristics = createHeuristicsAdapter(costCalculator);
    return new HeuristicSearch<>(
      createWorker.apply(state, createWorkerStrategy(state)),
      heuristics
    );
  }

  public RaptorWorker<T> createSearch(
    BiFunction<WorkerState<T>, RoutingStrategy<T>, RaptorWorker<T>> createWorker
  ) {
    StdRangeRaptorWorkerState<T> state = createState();
    return createWorker.apply(state, createWorkerStrategy(state));
  }

  /* private factory methods */

  private StdRangeRaptorWorkerState<T> createState() {
    new VerifyRequestIsValid(ctx).verify();
    switch (ctx.profile()) {
      case STANDARD:
      case MIN_TRAVEL_DURATION:
        return workerState(stdStopArrivalsState());
      case BEST_TIME:
      case MIN_TRAVEL_DURATION_BEST_TIME:
        return workerState(bestTimeStopArrivalsState());
    }
    throw new IllegalArgumentException(ctx.profile().toString());
  }

  private RoutingStrategy<T> createWorkerStrategy(StdWorkerState<T> state) {
    switch (ctx.profile()) {
      case STANDARD:
      case BEST_TIME:
        return new ArrivalTimeRoutingStrategy<>(
          state,
          ctx.createTimeBasedBoardingSupport(),
          ctx.calculator()
        );
      case MIN_TRAVEL_DURATION:
      case MIN_TRAVEL_DURATION_BEST_TIME:
        return new MinTravelDurationRoutingStrategy<>(
          state,
          ctx.createTimeBasedBoardingSupport(),
          ctx.calculator()
        );
    }
    throw new IllegalArgumentException(ctx.profile().toString());
  }

  private Heuristics createHeuristicsAdapter(CostCalculator<T> costCalculator) {
    Objects.requireNonNull(bestNumberOfTransfers);
    return new HeuristicsAdapter(
      bestTimes(),
      this.bestNumberOfTransfers,
      ctx.egressPaths(),
      ctx.calculator(),
      costCalculator,
      ctx.lifeCycle()
    );
  }

  private StdRangeRaptorWorkerState<T> workerState(StopArrivalsState<T> stopArrivalsState) {
    return new StdRangeRaptorWorkerState<>(
      ctx.calculator(),
      bestTimes(),
      stopArrivalsState,
      destinationCheck()
    );
  }

  private BestTimesOnlyStopArrivalsState<T> bestTimeStopArrivalsState() {
    return new BestTimesOnlyStopArrivalsState<>(
      bestTimes(),
      simpleBestNumberOfTransfers(),
      new UnknownPathFactory<>(
        bestTimes(),
        simpleBestNumberOfTransfers(),
        ctx.calculator(),
        ctx.lifeCycle(),
        ctx.egressPaths()
      )
    );
  }

  /**
   * Return instance if created by heuristics or null if not needed.
   */
  private SimpleBestNumberOfTransfers simpleBestNumberOfTransfers() {
    return withBestNumberOfTransfers(
      SimpleBestNumberOfTransfers.class,
      () -> new SimpleBestNumberOfTransfers(ctx.nStops(), ctx.roundProvider())
    );
  }

  /**
   * Create a Standard Range Raptor state for the given context. If debugging is enabled, the stop
   * arrival state is wrapped.
   */
  private StopArrivalsState<T> stdStopArrivalsState() {
    StdStopArrivalsState<T> state = new StdStopArrivalsState<>(
      stopArrivals(),
      destinationArrivalPaths()
    );
    return wrapStopArrivalsStateWithDebugger(state);
  }

  private StopArrivalsState<T> wrapStopArrivalsStateWithDebugger(StopArrivalsState<T> state) {
    if (ctx.debugFactory().isDebugStopArrival()) {
      return new DebugStopArrivalsState<>(
        ctx.roundProvider(),
        ctx.debugFactory(),
        stopsCursor(),
        state
      );
    } else {
      return state;
    }
  }

  private StdStopArrivals<T> stopArrivals() {
    //noinspection unchecked
    return withBestNumberOfTransfers(
      StdStopArrivals.class,
      () -> new StdStopArrivals<T>(ctx.nRounds(), ctx.nStops(), ctx.roundProvider())
    );
  }

  @SuppressWarnings("unchecked")
  private <S extends BestNumberOfTransfers> S withBestNumberOfTransfers(
    Class<S> type,
    Supplier<S> newValueFactory
  ) {
    if (this.bestNumberOfTransfers == null) {
      this.bestNumberOfTransfers = newValueFactory.get();
    } else if (this.bestNumberOfTransfers.getClass() != type) {
      throw new IllegalStateException(
        "There is more than one type of 'bestNumberOfTransfers': " +
        type.getSimpleName() +
        ", " +
        this.bestNumberOfTransfers.getClass().getSimpleName()
      );
    }
    return (S) this.bestNumberOfTransfers;
  }

  private StopsCursor<T> stopsCursor() {
    // Always create new cursors
    return new StopsCursor<>(stopArrivals(), ctx.calculator(), ctx.boardSlackProvider());
  }

  private DestinationArrivalPaths<T> destinationArrivalPaths() {
    DestinationArrivalPaths<T> destinationArrivalPaths = pathConfig.createDestArrivalPaths(false);

    // Add egressArrivals to stops and bind them to the destination arrival paths. The
    // adapter notify the destination on each new egress stop arrival.
    EgressArrivalToPathAdapter<T> pathsAdapter = new EgressArrivalToPathAdapter<>(
      destinationArrivalPaths,
      ctx.calculator(),
      stopsCursor(),
      ctx.lifeCycle()
    );

    // Use the  adapter to play the role of the destination arrival check
    setDestinationCheck(pathsAdapter);

    stopArrivals().setupEgressStopStates(ctx.egressPaths(), pathsAdapter);

    return destinationArrivalPaths;
  }

  private BestTimes bestTimes() {
    // Cache best times; request scope
    if (bestTimes == null) {
      bestTimes = new BestTimes(ctx.nStops(), ctx.calculator(), ctx.lifeCycle());
    }
    return bestTimes;
  }

  private ArrivedAtDestinationCheck destinationCheck() {
    // Cache best times; request scope
    if (destinationCheck == null) {
      setDestinationCheck(simpleDestinationCheck());
    }
    return destinationCheck;
  }

  private void setDestinationCheck(ArrivedAtDestinationCheck check) {
    // Cache best times; request scope
    if (destinationCheck != null) {
      throw new IllegalStateException(
        "ArrivedAtDestinationCheck is already initialized: " +
        destinationCheck.getClass().getSimpleName()
      );
    }
    destinationCheck = check;
  }

  private SimpleArrivedAtDestinationCheck simpleDestinationCheck() {
    return new SimpleArrivedAtDestinationCheck(ctx.egressStops(), bestTimes());
  }
}
