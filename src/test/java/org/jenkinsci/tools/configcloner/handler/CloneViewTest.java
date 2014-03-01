package org.jenkinsci.tools.configcloner.handler;

import static org.jenkinsci.tools.configcloner.handler.Helper.dest;
import static org.jenkinsci.tools.configcloner.handler.Helper.map;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.jenkinsci.tools.configcloner.CommandInvoker;
import org.jenkinsci.tools.configcloner.ConfigDestination;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.beust.jcommander.ParameterException;

public class CloneViewTest {

    @Test(dataProvider = "invalidArgs", expectedExceptions = ParameterException.class)
    public void failWithIncorectArguments(final String[] args) {

        final TransferHandler handler = handler(args);
        handler.source();
        handler.destinations();
    }

    @DataProvider
    public Object[][] invalidArgs() {

        return new String[][][] {
                // invalid count
                {{}},
                {{"http://jenki.ns"}},
                {{"http://jenki.ns", "http://jenki.ns", "http://jenki.ns"}},

                // same url
                {{"http://jenki.ns", "http://jenki.ns"}},

                // not an url
                {{"not-an-url", "http://jenki.ns"}},
                {{"http://jenki.ns", "not-an-url"}},
                {{"not-an-url", "not-an-url"}},
        };
    }

    @Test(dataProvider = "validArgs")
    public void parseValidDestinations(final String[] args, final ConfigDestination[] dests) {

        final TransferHandler handler = handler(args);

        assertEquals(dests[0], handler.source());
        assertEquals(
                Arrays.asList(dests).subList(1, dests.length),
                handler.destinations()
        );
    }

    @DataProvider
    public Object[][] validArgs() {

        return new Object[][][] {
                map("http://1.jnk.ns/view/a", "http://2.jnk.ns/").to(
                        dest("http://1.jnk.ns/", "a"),
                        dest("http://2.jnk.ns/", "a")
                ),
                map("http://1.jnk.ns/view/a/", "http://2.jnk.ns/view/b").to(
                        dest("http://1.jnk.ns/", "a"),
                        dest("http://2.jnk.ns/", "b")
                ),

                map("http://1.jnk.ns/jenkins/view/viewname", "http://2.jnk.ns/infra/hudson/view/name/some_tail").to(
                        dest("http://1.jnk.ns/jenkins/", "viewname"),
                        dest("http://2.jnk.ns/infra/hudson/", "name")
                ),

                map("http://1.jnk.ns/view/src", "http://2.jnk.ns/view/dst1", "http://3.jnk.ns/view/dst2").to(
                        dest("http://1.jnk.ns/", "src"),
                        dest("http://2.jnk.ns/", "dst1"),
                        dest("http://3.jnk.ns/", "dst2")
                ),

                // Parse nested views
                map("http://1.jnk.ns/jenkins/view/a/view/b", "http://2.jnk.ns/infra/hudson/view/c/view/d/view/e").to(
                        dest("http://1.jnk.ns/jenkins/", "a/b"),
                        dest("http://2.jnk.ns/infra/hudson/", "c/d/e")
                ),
        };
    }

    private TransferHandler handler(final String[] args) {

        final CommandInvoker invoker = new CommandInvoker("view").args(args);
        return (TransferHandler) invoker.main().getHandler(invoker.commandArgs());
    }
}
