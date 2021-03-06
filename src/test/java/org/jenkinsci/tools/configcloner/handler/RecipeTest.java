/*
 * The MIT License
 *
 * Copyright (c) 2014 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.tools.configcloner.handler;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.jenkinsci.tools.configcloner.handler.Helper.stderrContains;
import static org.jenkinsci.tools.configcloner.handler.Helper.stdoutContains;
import static org.jenkinsci.tools.configcloner.handler.Helper.succeeded;
import hudson.model.FreeStyleProject;
import hudson.model.ListView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.jenkinsci.tools.configcloner.CLIPool;
import org.jenkinsci.tools.configcloner.CommandInvoker;
import org.jenkinsci.tools.configcloner.CommandResponse;
import org.jenkinsci.tools.configcloner.Main;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

public class RecipeTest {

    @Rule public final JenkinsRule j = new JenkinsRule();

    private CommandResponse.Accumulator rsp;

    @Test @WithoutJenkins
    public void failIfNotFileProvided() {

        run();
        assertThat(rsp, not(succeeded()));
    }

    @Test @WithoutJenkins
    public void failIfTheFileCanNotBeRead() {

        run("there_is_no_such_file.groovy");
        assertThat(rsp, not(succeeded()));
        assertThat(rsp, stderrContains("FileNotFoundException"));
        assertThat(rsp, stderrContains("there_is_no_such_file.groovy"));
    }

    @Test @WithoutJenkins
    public void evaluateTheFile() throws IOException {

        run(recipe("println 'text_on_out';"));

        assertThat(rsp, succeeded());
        assertThat(rsp, stdoutContains("text_on_out"));
    }

    @Test
    public void failedCloneShouldFailRecipe() throws IOException {

        j.jenkins.createProject(FreeStyleProject.class, "src_job");
        String url = j.jenkins.getRootUrl();

        run(recipe(
                "clone.job '" + url + "job/src_job', '" + url + "job/src_job'\n"
        ));
        assertThat(rsp, not(succeeded()));

        run(recipe(
                "clone.job '" + url + "job/src_job', '" + url + "job/dst_job'\n" +
                "clone.job '" + url + "job/src_job', '" + url + "job/dst_job'\n" // fail here
        ));
        assertThat(rsp, not(succeeded()));

        run(recipe(
                "clone.job '" + url + "job/src_job', '" + url + "job/src_job'\n" + // fail here
                "clone.job '" + url + "job/src_job', '" + url + "job/dst_job2'\n"

        ));
        assertThat(rsp, not(succeeded()));
    }

    @Test
    public void runRecipe() throws Exception {

        j.jenkins.createProject(FreeStyleProject.class, "src_job");
        j.jenkins.addView(new ListView("src_view"));
        j.createSlave("src_slave", "label", null);

        run("-p", "url=" + j.jenkins.getRootUrl(), recipe(
                "def url = properties.url\n" +
                "clone.job url+'job/src_job', url+'job/dst_job'\n" +
                "clone.view url+'view/src_view', url+'view/dst_view'\n" +
                "clone.node url+'computer/src_slave', url+'computer/dst_slave'\n"
        ));

        assertThat(rsp, succeeded());
        assertThat(j.jenkins.getItem("dst_job"), notNullValue());
        assertThat(j.jenkins.getView("dst_view"), notNullValue());
        assertThat(j.jenkins.getNode("dst_slave"), notNullValue());
    }

    @Test
    public void useDoubleColonNotation() throws Exception {

        j.jenkins.createProject(FreeStyleProject.class, "src_job");
        j.jenkins.addView(new ListView("src_view"));
        j.createSlave("src_slave", "label", null);

        String url = j.jenkins.getRootUrl();
        run(recipe(
                "clone.job '" + url + "::src_job', '" + url + "::dst_job'\n" +
                "clone.view '" + url + "::src_view', '" + url + "::dst_view'\n" +
                "clone.node '" + url + "::src_slave', '" + url + "::dst_slave'\n"
        ));

        assertThat(rsp, succeeded());
        assertThat(j.jenkins.getItem("dst_job"), notNullValue());
        assertThat(j.jenkins.getView("dst_view"), notNullValue());
        assertThat(j.jenkins.getNode("dst_slave"), notNullValue());
    }

    @Test
    public void runSameCommandRepeatedly() throws IOException {

        j.jenkins.createProject(FreeStyleProject.class, "src_job");

        String url = j.jenkins.getRootUrl();
        run(recipe(
                "clone.job '" + url + "job/src_job', '" + url + "job/cloned_job'\n" +
                "clone.job '" + url + "job/cloned_job', '" + url + "job/cloned_cloned_job'\n"
        ));

        assertThat(rsp, succeeded());
        assertThat(j.jenkins.getItem("cloned_job"), notNullValue());
        assertThat(j.jenkins.getItem("cloned_cloned_job"), notNullValue());
    }

    @Test
    public void doNotModifyWehnUsingDryRun() throws Exception {

        j.jenkins.createProject(FreeStyleProject.class, "src_job");
        j.jenkins.addView(new ListView("src_view"));
        j.createSlave("src_slave", "label", null);

        String url = j.jenkins.getRootUrl();
        run("--dry-run", recipe(
                "clone.job '" + url + "job/src_job', '" + url + "job/dst_job'\n" +
                "clone.view '" + url + "view/src_view', '" + url + "view/dst_view'\n" +
                "clone.node '" + url + "computer/src_slave', '" + url + "computer/dst_slave'\n"
        ));

        assertThat(rsp, succeeded());
        assertThat(j.jenkins.getItem("dst_job"), nullValue());
        assertThat(j.jenkins.getView("dst_view"), nullValue());
        assertThat(j.jenkins.getNode("dst_slave"), nullValue());
    }

    private void run(String... args) {
        final ArrayList<String> effectiveArgs = new ArrayList<String>(args.length + 1);
        effectiveArgs.add("recipe");
        effectiveArgs.addAll(Arrays.asList(args));

        rsp = CommandResponse.accumulate();
        CLIPool cliPool = CommandInvoker.getCLIPoolForTest();
        new Main(rsp, cliPool).run(effectiveArgs.toArray(new String[effectiveArgs.size()]));
        cliPool.close();

        rsp.dump("recipe");
    }

    private String recipe(String text) throws IOException {
        final File groovy = File.createTempFile("config-cloner", "ReceipeTest");
        groovy.deleteOnExit();
        final FileWriter writer = new FileWriter(groovy);
        try {
            writer.write(text);
            writer.close();
        } catch(IOException ex) {
            writer.close();
            throw ex;
        }
        return groovy.getAbsolutePath();
    }
}
