package com.wawa.sonarcloud.github;

import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.helper.HelpScreenException;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Get the state of quality gates for a SonarCloud project and propagate it to a Github repository
 *
 * @author Mark Grand
 */
public class QualityGatePropagationToGithub {
    private static Logger logger = LoggerFactory.getLogger(QualityGatePropagationToGithub.class);

    private static class CLOptions {
        @Arg(dest = "analysisId")
        String analysisId;

        @Arg(dest = "branch")
        String branch;

        @Arg(dest = "projectKey")
        String projectKey;

        @Arg(dest = "pullRequest")
        Integer pullRequest;

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
            processArgs(argv)
                    .map( clOptions -> getQualityGateStatus(clOptions.analysisId, clOptions.branch, clOptions.projectKey, clOptions.pullRequest))
                    .map( status -> {logger.info("Status is " + status); return "";});
        } catch (Exception e) {
            logger.error("Unhandled exception", e);
        }
    }

    private static Optional<String> getQualityGateStatus(String analysisId, String branch, String projectKey, Integer pullRequest) {
        WebClient webClient = WebClient.create();
        Mono<String> response = webClient.get()
                .uri("https://dev-eu2ne9zx.auth0.com/oauth/token").accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class);
        return parseQualityGateStatusResponse(response.block());
    }

    private static Optional<String> parseQualityGateStatusResponse(String response) {
        try {
            JSONObject json = (JSONObject)new JSONParser(JSONParser.MODE_PERMISSIVE).parse(response);
            return Optional.of(json.get("status").toString());
        } catch (NullPointerException | ParseException e) {
            logger.error("Error parsing quality gate status response: " + response, e);
            return Optional.empty();
        }
    }

    private static Optional<CLOptions> processArgs(String[] argv) {
        ArgumentParser parser = createArgumentParser();
        try {
            CLOptions clOptions = new CLOptions();
            parser.parseArgs(argv, clOptions);
            if (clOptions.analysisId == null && clOptions.projectKey == null) {
                logger.error("Either --analysisId or --projectKey must be specified");
                return Optional.empty();
            }
            logger.info(clOptions.toString());
            return Optional.of(clOptions);
        } catch (HelpScreenException e) {
            return Optional.empty(); // This is a normal way to exit when all that we are doing is displaying help.
        } catch (ArgumentParserException e) {
            logger.error("Error parsing command line", e);
            return Optional.empty();
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
