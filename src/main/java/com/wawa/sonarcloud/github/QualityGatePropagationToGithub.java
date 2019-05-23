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
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

/**
 * Get the state of quality gates for a SonarCloud project and propagate it to a Github repository
 *
 * @author Mark Grand
 */
public class QualityGatePropagationToGithub {
    private static final Logger logger = LoggerFactory.getLogger(QualityGatePropagationToGithub.class);

    private static final String GITHUB_API_BASE_URL = "https://api.github.com";

    /**
     * Class to contain command line option values.
     */
    private static class CLOptions {
        @Arg(dest = "analysisId")
        String analysisId;

        @Arg(dest = "branch")
        String branch;

        @Arg(dest = "githubRepoName")
        String githubRepoName;

        @Arg(dest = "githubRepoOwner")
        String githubRepoOwner;

        @Arg(dest = "githubToken")
        String githubToken;

        @Arg(dest = "githubUser")
        String githubUser;

        @Arg(dest = "projectKey")
        String projectKey;

        @Arg(dest = "pullRequest")
        Integer pullRequest;

        @Arg(dest = "sha")
        String sha;

        @Arg(dest = "sonarCloudUrl")
        String sonarCloudUrl;

        @Arg(dest = "verbose")
        Boolean verbose;

        @Arg(dest = "pending")
        Boolean pending;

        @Override
        public String toString() {
            return "CLOptions{" +
                    "analysisId='" + analysisId + '\'' +
                    ", branch='" + branch + '\'' +
                    ", githubToken='" + githubToken + '\'' +
                    ", githubUser='" + githubUser + '\'' +
                    ", projectKey='" + projectKey + '\'' +
                    ", pullRequest=" + pullRequest +
                    ", sonarCloudUrl='" + sonarCloudUrl + '\'' +
                    '}';
        }
    }

    /**
     * Main entry point
     *
     * @param argv The command line arguments
     */
    public static void main(String[] argv) {
        try {
            Optional<String> resultJsonString = processArgs(argv).flatMap(QualityGatePropagationToGithub::doIt);
            logger.info("Pushed status: {}", resultJsonString);
        } catch (Exception e) {
            logger.error("Unhandled exception", e);
        }
    }

    private static Optional<String> doIt(CLOptions clOptions) {
        return getQualityGateStatus(clOptions).map(status -> pushGateStatus(status, clOptions));
    }

    /**
     * Push the given quality gate status to github
     *
     * @param status    the status value
     * @param clOptions the command line options
     * @return The json string returned by Github.
     */
    private static String pushGateStatus(QualityGateStatus status, CLOptions clOptions) {
        WebClient webClient = newWebClientBuilder(GITHUB_API_BASE_URL, clOptions)
                .filter(ExchangeFilterFunctions.basicAuthentication(clOptions.githubUser, clOptions.githubToken))
                .build();
        return webClient.post()
                .uri("/repos/{owner}/{repo}/statuses/{sha}", clOptions.githubRepoOwner, clOptions.githubRepoName, clOptions.sha)
                .syncBody(formatStatusJson(status, clOptions))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /**
     * Format the JSON that will contain the status being pushed to Github.
     *
     * @param status    the status
     * @param clOptions command line options
     * @return the formatted JSON string.
     */
    private static String formatStatusJson(QualityGateStatus status, CLOptions clOptions) {
        JSONObject json = new JSONObject();
        json.put("state", qualityGetStatus2GithubCommitStatus(status));
        json.put("target_url", "https://sonarcloud.io/dashboard?id=" + clOptions.projectKey);
        json.put("description", "Quality gate status: " + status);
        json.put("context", "Sonar Cloud");
        return json.toJSONString();
    }

    /**
     * Convert a quality gate status into a Github commit status.
     *
     * @param status the quality gate status
     * @return the equivalent Github commit status.
     */
    private static String qualityGetStatus2GithubCommitStatus(QualityGateStatus status) {
        switch (status) {
            case OK:
            case WARN:
                return "success";
            case ERROR:
                return "failure";
            default:
                return "error";
        }
    }

    /**
     * Get the quality gate status from Sonar Cloud
     *
     * @param clOptions The command line options
     * @return the quality gate status.
     */
    private static Optional<QualityGateStatus> getQualityGateStatus(CLOptions clOptions) {
        WebClient webClient = newWebClientBuilder(clOptions.sonarCloudUrl, clOptions).build();
        Mono<String> response = webClient.get()
                .uri(uriBuilder -> buildQualityGateUri(clOptions, uriBuilder))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class);
        return parseQualityGateStatusResponse(response.block())
                .flatMap(QualityGatePropagationToGithub::stringToQualityGateStatus)
                .filter(QualityGatePropagationToGithub::isStatusNotNone);
    }

    private static WebClient.Builder newWebClientBuilder(String url, CLOptions clOptions) {
        WebClient.Builder builder = WebClient.builder().baseUrl(url);
        if (clOptions.verbose) {
            builder.filter(logRequest());
        }
        return builder;
    }

    private static boolean isStatusNotNone(QualityGateStatus status) {
        if (QualityGateStatus.NONE.equals(status)) {
            logger.warn("The quality gate does not yet have a status");
            return false;
        }
        return true;
    }

    private static Optional<QualityGateStatus> stringToQualityGateStatus(String statusString) {
        try {
            return Optional.of(QualityGateStatus.valueOf(statusString));
        } catch (IllegalArgumentException e) {
            String msg = "Unable to handle unexpected quality gate status \"" + statusString + "\". Expected one of " + Arrays.toString(QualityGateStatus.values());
            logger.error(msg);
            return Optional.empty();
        }
    }

    private static URI buildQualityGateUri(CLOptions clOptions, UriBuilder uriBuilder) {
        uriBuilder.path("/api/qualitygates/project_status");
        conditionalParameterAppend("analysisId", clOptions.analysisId, uriBuilder);
        conditionalParameterAppend("branch", clOptions.branch, uriBuilder);
        conditionalParameterAppend("projectKey", clOptions.projectKey, uriBuilder);
        conditionalParameterAppend("pullRequest", clOptions.pullRequest, uriBuilder);
        return uriBuilder.build();
    }

    /**
     * Append a parameter to the url if its value is not null.
     *
     * @param name       name parameter name
     * @param value      the parameter value
     * @param uriBuilder the builder object bein used to construct a URI.
     */
    private static void conditionalParameterAppend(String name, Object value, UriBuilder uriBuilder) {
        if (value != null) {
            uriBuilder.queryParam(name, value);
        }
    }

    /**
     * Extract the quality gate status from the given response JSON
     *
     * @param response the Sonar Cloud response containing the quality gate status.
     * @return The quality gate status
     */
    private static Optional<String> parseQualityGateStatusResponse(String response) {
        try {
            JSONObject json = (JSONObject) new JSONParser(JSONParser.MODE_PERMISSIVE).parse(response);
            return Optional.of(((JSONObject) json.get("projectStatus")).get("status").toString());
        } catch (NullPointerException | ParseException e) {
            logger.error("Error parsing quality gate status response: " + response, e);
            return Optional.empty();
        }
    }

    /**
     * Parse the command line arguments
     *
     * @param argv The command line arguments
     * @return a {@link CLOptions} object that contains the parsed settings from the command line arguments.
     */
    private static Optional<CLOptions> processArgs(String[] argv) {
        ArgumentParser parser = createArgumentParser();
        try {
            CLOptions clOptions = new CLOptions();
            parser.parseArgs(argv, clOptions);
            if (clOptions.analysisId == null && clOptions.projectKey == null) {
                logger.error("Either --analysisId or --projectKey must be specified");
                return Optional.empty();
            }
            if (logger.isInfoEnabled()) {
                logger.info(clOptions.toString());
            }
            return Optional.of(clOptions);
        } catch (HelpScreenException e) {
            return Optional.empty(); // This is a normal way to exit when all that we are doing is displaying help.
        } catch (ArgumentParserException e) {
            logger.error("Error parsing command line", e);
            return Optional.empty();
        }
    }

    /**
     * Create and configure a parser to parse the command line arguments.
     *
     * @return the command line parser.
     */
    private static ArgumentParser createArgumentParser() {
        ArgumentParser parser = ArgumentParsers.newFor("QualityGatePropagationToGithub")
                .addHelp(false).build().version("${prog} 1.0");
        parser.addArgument("--analysisId").help("Analysis id");
        parser.addArgument("--branch").help("Branch key");
        parser.addArgument("--verbose").action(Arguments.storeTrue()).help("If this is specified, all requests are logged (at INFO level).");
        parser.addArgument("--githubRepoName").required(true).help("The name of the github repo");
        parser.addArgument("--githubRepoOwner").required(true).help("The owner of the github repo");
        parser.addArgument("--githubToken").required(true)
                .help("github personal access token used in place of a password.  "
                        + "For documentation on creating a personal access token see https://github.blog/2013-05-16-personal-api-tokens/");
        parser.addArgument("--githubUser").required(true).help("github user name");
        parser.addArgument("--projectKey").help("Project key");
        parser.addArgument("--pullRequest").type(Integer.class).help("Pull request id");
        parser.addArgument("--sha").required(true).help("The SHA hash of the commit this analysis applies to");
        parser.addArgument("--sonarCloudUrl").setDefault("https://sonarcloud.io").help("Base Sonar Cloud URL");
        parser.addArgument("--version").action(Arguments.version());
        parser.addArgument("--help").action(Arguments.help());
        return parser;
    }

    /**
     * Return a function that logs the contents of a request.
     *
     * @return the function.
     */
    private static ExchangeFilterFunction logRequest() {
        return (clientRequest, next) -> {
            logger.info("Request: {} {}", clientRequest.method(), clientRequest.url());
            clientRequest.headers()
                    .forEach((name, values) -> values.forEach(value -> logger.info("{}={}", name, value)));
            return next.exchange(clientRequest);
        };
    }
}
