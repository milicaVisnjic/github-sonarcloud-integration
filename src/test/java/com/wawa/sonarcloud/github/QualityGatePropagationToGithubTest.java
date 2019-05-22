package com.wawa.sonarcloud.github;

import org.junit.Test;

public class QualityGatePropagationToGithubTest {

    @Test
    public void mainHelp() {
        QualityGatePropagationToGithub.main(new String[]{"--help"});
    }
}