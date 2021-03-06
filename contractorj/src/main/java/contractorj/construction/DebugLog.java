package contractorj.construction;

import contractorj.construction.corral.RunnerResult;
import contractorj.construction.queries.Answer;
import contractorj.construction.queries.Query;
import contractorj.construction.queries.invariant.ExceptionBreaksInvariantQuery;
import contractorj.construction.queries.invariant.InvariantQuery;
import contractorj.construction.queries.necessary_actions.GlobalNecessarilyDisabledActionQuery;
import contractorj.construction.queries.necessary_actions.GlobalNecessarilyEnabledActionQuery;
import contractorj.construction.queries.necessary_actions.NecessarilyEnabledActionQuery;
import contractorj.construction.queries.necessary_actions.NecessaryActionQuery;
import contractorj.construction.queries.transition.ThrowingTransitionQuery;
import contractorj.construction.queries.transition.TransitionQuery;
import contractorj.model.Action;
import contractorj.model.State;
import contractorj.model.Transition;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DebugLog {

  private final List<State> statesInOrder = new ArrayList<>();

  private final Map<State, HashMap<Action, StateActionInfo>> stateMainActionMap = new HashMap<>();

  private final Map<State, HashMap<Action, StateActionInfo>> globalStatesMainActionMap =
      new HashMap<>();

  public void addInitialState(State state) {
    statesInOrder.add(state);
  }

  public synchronized void logQuery(Query query, RunnerResult runnerResult) {

    if (query instanceof GlobalNecessarilyDisabledActionQuery
        || query instanceof GlobalNecessarilyEnabledActionQuery) {
      logGlobalNecessaryActionQuery((NecessaryActionQuery) query, runnerResult);
      return;
    }

    if (query instanceof NecessaryActionQuery) {
      logNecessaryActionQuery((NecessaryActionQuery) query, runnerResult);
    }

    if (query instanceof InvariantQuery) {
      logInvariantQuery(((InvariantQuery) query), runnerResult);
    }

    if (query instanceof TransitionQuery) {
      logTransitionQuery(((TransitionQuery) query), runnerResult);
    }
  }

  public synchronized void logEnqueuedTransition(Transition transition) {

    final State target = transition.getTarget();

    statesInOrder.add(target);
    final StateActionInfo stateActionInfo =
        getStateActionInfo(transition.getSource(), transition.getAction());

    stateActionInfo.enqueuedStates.add(target);

    if (!stateMainActionMap.containsKey(target)) {
      stateMainActionMap.put(target, new HashMap<>());
    }
  }

  public void writeLog(PrintWriter out) {
    if (!stateMainActionMap.keySet().stream().allMatch(statesInOrder::contains)) {
      throw new RuntimeException("Info logged for source state that wasn't enqueued");
    }

    out.println("Global necessarily enabled/disabled queries: ");

    out.println();
    out.println();

    // print global queries for optimization
    globalStatesMainActionMap.forEach(
        (state, actionStateActionInfoMap) -> {
          final Action mainAction =
              state.getEnabledActions().iterator().next(); // the only action that the state has
          final StateActionInfo stateActionInfo = actionStateActionInfoMap.get(mainAction);

          out.println("\tMain Action: " + mainAction.toString());

          for (final Action testedAction : stateActionInfo.necessaryEnabledActions.keySet()) {

            out.println("\t\tTested action: " + testedAction);

            final QueryInfo enabledInfo = stateActionInfo.necessaryEnabledActions.get(testedAction);
            final QueryInfo disabledInfo =
                stateActionInfo.necessaryDisabledActions.get(testedAction);

            if (areInconsistent(enabledInfo, disabledInfo)) {
              out.println("\t\t\tInconsistent");
            }

            out.println("\t\t\tEnabled:");

            printQueryInfo("\t\t\t\t", out, enabledInfo);

            out.println("\t\t\tDisabled:");

            printQueryInfo("\t\t\t\t", out, disabledInfo);

            out.println();
          }
        });

    out.println();
    out.println();

    out.println("Normal queries: ");
    for (final State state : statesInOrder) {

      out.println();
      out.println();
      out.println();
      out.println();

      out.println("State: " + state.toString());

      if (!stateMainActionMap.containsKey(state)) continue;

      for (Action action : stateMainActionMap.get(state).keySet()) {

        out.println();
        out.println();

        out.println("\tAction: " + action.toString());

        out.println();

        final StateActionInfo stateActionInfo = getStateActionInfo(state, action);

        out.println("\t\tInvariant breakage:");

        out.println();

        out.println("\t\t\tNot throwing:");

        printQueryInfo("\t\t\t\t", out, stateActionInfo.notThrowingInvariantPreservation);

        out.println("\t\t\tThrowing:");

        printQueryInfo("\t\t\t\t", out, stateActionInfo.throwingInvariantPreservation);

        out.println();
        out.println();

        out.println("\t\tNecessary actions:");

        out.println();

        for (final Action testedAction : stateActionInfo.necessaryEnabledActions.keySet()) {

          out.println("\t\t\tTested action: " + testedAction);

          final QueryInfo enabledInfo = stateActionInfo.necessaryEnabledActions.get(testedAction);
          final QueryInfo disabledInfo = stateActionInfo.necessaryDisabledActions.get(testedAction);

          if (areInconsistent(enabledInfo, disabledInfo)) {
            out.println("\t\t\t\tInconsistent");
          }

          out.println("\t\t\t\tEnabled:");

          printQueryInfo("\t\t\t\t\t", out, enabledInfo);

          out.println("\t\t\t\tDisabled:");

          printQueryInfo("\t\t\t\t\t", out, disabledInfo);

          out.println();
        }

        out.println();
        out.println();

        out.println("\t\tTransitions:");

        for (State targetState : stateActionInfo.notThrowingTransitions.keySet()) {

          out.println("\t\t\tTarget state: " + targetState);

          if (stateActionInfo.enqueuedStates.contains(targetState)) {
            out.println("\t\t\t\tEnqueued");
          }

          out.println("\t\t\t\tNot throwing:");

          printQueryInfo(
              "\t\t\t\t\t", out, stateActionInfo.notThrowingTransitions.get(targetState));

          out.println("\t\t\t\tThrowing:");

          printQueryInfo("\t\t\t\t\t", out, stateActionInfo.throwingTransitions.get(targetState));
        }

        out.println();

        out.println();
      }
    }
  }

  private boolean areInconsistent(QueryInfo enabledInfo, QueryInfo disabledInfo) {
    return enabledInfo != null
        && disabledInfo != null
        && enabledInfo.answer.equals(Answer.YES)
        && disabledInfo.answer.equals(Answer.YES);
  }

  private void printQueryInfo(String prefix, PrintWriter out, QueryInfo queryInfo) {

    if (queryInfo == null) {
      out.println(prefix + "Not present");
    } else {
      out.println(prefix + "Answer: " + queryInfo.answer);
      out.println(prefix + "Command:");
      out.println(prefix + "\t" + queryInfo.result.command);
    }

    out.println();
  }

  private void logTransitionQuery(TransitionQuery query, RunnerResult runnerResult) {
    final State source = query.getSource();
    final Action mainAction = query.getMainAction();
    final State target = query.getTarget();

    final QueryInfo queryInfo = getQueryInfo(query, runnerResult);
    final StateActionInfo stateActionInfo = getStateActionInfo(source, mainAction);

    if (query instanceof ThrowingTransitionQuery) {
      stateActionInfo.throwingTransitions.put(target, queryInfo);
    } else {
      stateActionInfo.notThrowingTransitions.put(target, queryInfo);
    }
  }

  private void logInvariantQuery(InvariantQuery query, RunnerResult runnerResult) {

    final State source = query.getSource();
    final Action mainAction = query.getMainAction();

    final QueryInfo queryInfo = getQueryInfo(query, runnerResult);
    final StateActionInfo stateActionInfo = getStateActionInfo(source, mainAction);

    if (query instanceof ExceptionBreaksInvariantQuery) {
      stateActionInfo.throwingInvariantPreservation = queryInfo;
    } else {
      stateActionInfo.notThrowingInvariantPreservation = queryInfo;
    }
  }

  private void logNecessaryActionQuery(NecessaryActionQuery query, RunnerResult runnerResult) {

    final State source = query.getSource();
    final Action mainAction = query.getMainAction();
    final Action testedAction = query.getTestedAction();

    final QueryInfo queryInfo = getQueryInfo(query, runnerResult);
    final StateActionInfo stateActionInfo = getStateActionInfo(source, mainAction);

    if (query instanceof NecessarilyEnabledActionQuery) {
      stateActionInfo.necessaryEnabledActions.put(testedAction, queryInfo);
    } else {
      stateActionInfo.necessaryDisabledActions.put(testedAction, queryInfo);
    }
  }

  private void logGlobalNecessaryActionQuery(
      NecessaryActionQuery query, RunnerResult runnerResult) {

    final State source = query.getSource();
    final Action mainAction = query.getMainAction();
    final Action testedAction = query.getTestedAction();

    final QueryInfo queryInfo = getQueryInfo(query, runnerResult);

    final HashMap<Action, StateActionInfo> actionToStateActionInfo =
        globalStatesMainActionMap.getOrDefault(source, new HashMap<Action, StateActionInfo>());
    globalStatesMainActionMap.putIfAbsent(source, actionToStateActionInfo);

    final StateActionInfo stateActionInfo =
        actionToStateActionInfo.getOrDefault(mainAction, new StateActionInfo());
    actionToStateActionInfo.putIfAbsent(mainAction, stateActionInfo);

    if (query instanceof NecessarilyEnabledActionQuery) {
      stateActionInfo.necessaryEnabledActions.put(testedAction, queryInfo);
    } else {
      stateActionInfo.necessaryDisabledActions.put(testedAction, queryInfo);
    }
  }

  private QueryInfo getQueryInfo(Query query, RunnerResult runnerResult) {
    return new QueryInfo(query.getAnswer(runnerResult.queryResult), runnerResult);
  }

  private StateActionInfo getStateActionInfo(State state, Action mainAction) {

    if (!stateMainActionMap.containsKey(state)) {
      stateMainActionMap.put(state, new HashMap<>());
    }

    final HashMap<Action, StateActionInfo> stateActionInfoMap = stateMainActionMap.get(state);

    if (!stateActionInfoMap.containsKey(mainAction)) {
      stateActionInfoMap.put(mainAction, new StateActionInfo());
    }

    return stateActionInfoMap.get(mainAction);
  }

  private static class StateActionInfo {

    public final Map<Action, QueryInfo> necessaryEnabledActions = new HashMap<>();

    public final Map<Action, QueryInfo> necessaryDisabledActions = new HashMap<>();

    public final Map<State, QueryInfo> notThrowingTransitions = new HashMap<>();

    public final Map<State, QueryInfo> throwingTransitions = new HashMap<>();

    public final Set<State> enqueuedStates = new HashSet<>();

    public QueryInfo notThrowingInvariantPreservation;

    public QueryInfo throwingInvariantPreservation;
  }

  private static class QueryInfo {

    public final Answer answer;

    public final RunnerResult result;

    private QueryInfo(Answer answer, RunnerResult result) {
      this.answer = answer;
      this.result = result;
    }
  }
}
