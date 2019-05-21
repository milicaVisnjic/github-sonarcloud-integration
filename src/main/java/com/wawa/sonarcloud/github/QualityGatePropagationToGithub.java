package com.wawa.sonarcloud.github;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.helper.HelpScreenException;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

/**
 * Get the state of quality gates for a SonarCloud project and propagate it to a Github repository
 *
 * @author Mark Grand
 */
public class QualityGatePropagationToGithub {

    private static class CLOptions {
        @Arg(dest = "analysisId")
        String analysisId;

        @Arg(dest = "branch")
        String branch;

        @Arg(dest = "projectKey")
        String projectKey;

        @Arg(dest = "pullRequest")
        int pullRequest;

        @Override
        public String toString() {
            return "CLOptions{" +
                    "analysisId='" + analysisId + '\'' +
                    ", branch='" + branch + '\'' +
                    ", projectKey='" + projectKey + '\'' +
                    ", pullRequest='" + pullRequest + '\'' +
                    '}';
        }
    }

    /**
     * Main entry point
     * @param argv The command line arguments
     */
    public static void main(String[] argv) {
        try {
            processArgs(argv);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processArgs(String[] argv) {
        ArgumentParser parser = createArgumentParser();
        try {
            CLOptions clOptions = new CLOptions();
            parser.parseArgs(argv, clOptions);
            System.out.println(clOptions);
            System.out.println(parser.parseArgs(argv));
        } catch (HelpScreenException e) {
            System.exit(0); // This is a normal way to exit when all that we are doing is displaying help.
        } catch (ArgumentParserException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static ArgumentParser createArgumentParser() {
        ArgumentParser parser = ArgumentParsers.newFor("QualityGatePropagationToGithub")
                .addHelp(false).build().version("${prog} 1.0");
        parser.addArgument("--analysisId").help("Analysis id");
        parser.addArgument("--branch").help("Branch key");
        parser.addArgument("--projectKey").help("Project key");
        parser.addArgument("--pullRequest").type(Integer.class).help("Pull request id");
        parser.addArgument("--version").action(Arguments.version());
        parser.addArgument("--help").action(Arguments.help());
        return parser;
    }
}
