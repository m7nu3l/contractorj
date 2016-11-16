package contractorj.construction.corral;

import com.google.common.base.Joiner;
import contractorj.construction.Query;
import contractorj.model.Epa;
import contractorj.model.Transition;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class QueryRunner implements Runnable {

    public static final String ANSI_RESET = "\u001B[0m";

    public static final String ANSI_BLACK = "\u001B[30m";

    public static final String ANSI_RED = "\u001B[31m";

    public static final String ANSI_GREEN = "\u001B[32m";

    public static final String ANSI_YELLOW = "\u001B[33m";

    public static final String ANSI_BLUE = "\u001B[34m";

    public static final String ANSI_PURPLE = "\u001B[35m";

    public static final String ANSI_CYAN = "\u001B[36m";

    public static final String ANSI_WHITE = "\u001B[37m";

    private final File boogieFile;

    private final CorralRunner corralRunner;

    private final Epa epa;

    private final BlockingQueue<Query> queriesQueue;

    public QueryRunner(String baseTranslation, Epa epa, BlockingQueue<Query> queriesQueue) {

        this.epa = epa;

        this.queriesQueue = queriesQueue;

        corralRunner = new CorralRunner("/Users/pato/facultad/tesis/tools/corral/bin/Debug/corral.exe");

        try {
            boogieFile = File.createTempFile("epa-", ".bpl");
            System.out.println(boogieFile.getAbsolutePath());
            appendToBoogieFile(baseTranslation);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void run() {

        try {
            while (!queriesQueue.isEmpty()) {

                final Query query;

                try {
                    query = queriesQueue.poll(50, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    continue;
                }

                appendToBoogieFile("\n" + query.getBoogieCode());

                final String queryName = query.getName();

                final CorralRunner.RunnerResult runnerResult = corralRunner.run(boogieFile.getAbsolutePath(),
                        queryName);

                final Result result = runnerResult.queryResult;

                if (result.equals(Result.BUG_IN_QUERY)) {
                    final Transition transition = new Transition(query.getSource(), query.getTarget(), query.getTransition(), false, false);
                    epa.addTransition(transition);
                }

                if (result.equals(Result.MAYBE_BUG)) {
                    final Transition transition = new Transition(query.getSource(), query.getTarget(), query.getTransition(), true, false);
                    epa.addTransition(transition);
                }

                final List<String> sourceActions = query.getSource().enabledActions.stream()
                        .map(action -> action.method.getJavaNameWithArgumentTypes())
                        .collect(Collectors.toList());

                final List<String> targetActions = query.getTarget().enabledActions.stream()
                        .map(action -> action.method.getJavaNameWithArgumentTypes())
                        .collect(Collectors.toList());

                final StringBuilder logBuilder = new StringBuilder()
                        .append("Query\n")
                        .append("\tsource: ").append(Joiner.on(", ").join(sourceActions)).append("\n")
                        .append("\ttransition: ").append(query.getTransition().method.getJavaNameWithArgumentTypes())
                        .append("\n")
                        .append("\ttarget: ").append(Joiner.on(", ").join(targetActions)).append("\n")
                        .append("\tresult: ").append(result).append("\n")
                        .append("\ttime: ").append(runnerResult.runningTime).append("\n")
                        .append("\tfile: ").append(boogieFile.getAbsolutePath()).append("\n")
                        .append("\tprocedure: ").append(queryName).append("\n")
                        .append("\n");

                switch (result) {

                    case BUG_IN_QUERY:
                        System.out.println(ANSI_GREEN + logBuilder.toString() + ANSI_RESET);
                        break;

                    case MAYBE_BUG:
                        System.out.println(ANSI_BLUE + logBuilder.toString() + ANSI_RESET);
                        break;

                    case TRANSITION_MAY_NOT_THROW:
                        System.out.println(ANSI_RED + logBuilder.toString() + ANSI_RESET);
                        break;

                    default:
                        System.out.println(logBuilder.toString());
                }
            }

        } catch (RuntimeException exception) {
            System.err.println("Uncaught exception on thread " + Thread.currentThread().getName() + ":");
            System.err.println(exception.getMessage());
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private void appendToBoogieFile(String boogieCode) {

        try (
                final FileWriter fw = new FileWriter(boogieFile, true);
                final BufferedWriter bw = new BufferedWriter(fw);
                final PrintWriter out = new PrintWriter(bw)
        ) {
            out.print(boogieCode + "\n");
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
